package com.simon.harmonichackernews.cache

import com.simon.harmonichackernews.data.ArticleSnapshotPolicy
import com.simon.harmonichackernews.data.PreparedCommentThread
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.AlgoliaStorySummary
import com.simon.harmonichackernews.network.CachedDownloadService
import com.simon.harmonichackernews.network.DownloadCachePolicy
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.network.HttpMediaType
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.network.KtorTransferClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Portable article-snapshot transfer and cache policy over a platform filesystem adapter. */
class ArticleSnapshotService(
    httpClient: KtorHttpClient,
    store: DownloadStore?,
) {
    private val downloads = store?.let {
        CachedDownloadService(
            client = KtorTransferClient(httpClient),
            store = it,
            policy = DownloadCachePolicy(
                maxFileBytes = ArticleSnapshotPolicy.MAX_BYTES,
                maxCacheBytes = MAX_ARTICLE_CACHE_BYTES,
                maxTemporaryAgeMillis = MAX_TEMPORARY_AGE_MILLIS,
            ),
            cacheLabel = "Article HTML",
        )
    }

    val supported: Boolean get() = downloads != null

    suspend fun download(storyId: Int, articleUrl: String, nowMillis: Long): Boolean {
        val service = downloads ?: return false
        if (storyId <= 0 || articleUrl.isBlank()) return false
        return try {
            service.download(
                url = articleUrl,
                accept = "text/html,application/xhtml+xml",
                key = storyId.toString(),
                nowMillis = nowMillis,
                reuseExisting = false,
                acceptsContentType = ::isHtml,
            )
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }
    }

    private companion object {
        const val MAX_ARTICLE_CACHE_BYTES = 200L * 1_024L * 1_024L
        const val MAX_TEMPORARY_AGE_MILLIS = 24L * 60L * 60L * 1_000L

        fun isHtml(contentType: HttpMediaType?): Boolean {
            if (contentType == null) return false
            val type = contentType.type.lowercase()
            val subtype = contentType.subtype.lowercase()
            return (type == "text" && subtype == "html") ||
                (type == "application" && subtype == "xhtml+xml")
        }
    }
}

/**
 * Application-scoped story cache façade. Repository access and concurrent batch writes are owned
 * here so every platform uses the same cache behavior without process-global service locators.
 */
class StoryCacheService(
    private val repository: StoryCacheRepository,
    private val articleSnapshots: ArticleSnapshotService,
    private val nowMillis: () -> Long,
) : StoryCacheSink {
    private val writeMutex = Mutex()
    private val commentsParser = AlgoliaCommentsParser()

    /** Missing, old-schema and corrupt entries rebuild from retained JSON, including offline. */
    suspend fun loadPreparedThread(storyId: Int): PreparedCommentThread? = withContext(Dispatchers.Default) {
        // Atomic files make a complete hit safe to read during a concurrent refresh. Do not make
        // reopening wait for an unrelated article download holding the cache's mutation lock.
        repository.loadPreparedThread(storyId)?.let { return@withContext it }
        writeMutex.withLock {
            repository.loadPreparedThread(storyId)?.let { return@withLock it }
            val response = repository.loadStoryPayload(storyId) ?: return@withLock null
            val story = Story().apply { id = storyId }
            repository.hydrateStory(story)
            try {
                commentsParser.prepare(response, story.kids?.toList().orEmpty()).also {
                    repository.storePreparedThread(storyId, it)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: com.simon.harmonichackernews.network.ApiDecodingException) {
                null
            }
        }
    }

    fun hydrateStory(story: com.simon.harmonichackernews.data.Story?): Boolean =
        repository.hydrateStory(story)

    fun recentStories(): List<com.simon.harmonichackernews.data.Story> =
        repository.recentStories(nowMillis())

    fun hasRecentStories(): Boolean = repository.hasRecentStories(nowMillis())

    fun loadStoryPayload(storyId: Int): String? = repository.loadStoryPayload(storyId)

    fun hasStoryPayload(storyId: Int): Boolean = repository.hasStoryPayload(storyId)

    fun loadArticle(storyId: Int): String? = repository.loadArticle(storyId, nowMillis())

    fun articleUrl(storyId: Int): String? = repository.articleUrl(storyId)

    fun itemCount(): Int = repository.cachedItemIds().size

    suspend fun clear(): Int = writeMutex.withLock { repository.clear() }

    suspend fun remove(storyId: Int) = writeMutex.withLock { repository.remove(storyId) }

    suspend fun storeStory(id: Int, payload: String): Boolean = withContext(Dispatchers.Default) {
        // Background/offline downloads enter here without a parsed response. Prepare them eagerly
        // too, so their first offline open gets the same benefit as an already-viewed thread.
        val summary = try {
            commentsParser.prepare(payload).cacheSummary()
        } catch (error: CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: com.simon.harmonichackernews.network.ApiDecodingException) {
            null
        }
        writeMutex.withLock { repository.storeStory(id, payload, nowMillis(), summary) }
    }

    override suspend fun cacheStory(id: Int, payload: String) {
        storeStory(id, payload)
    }

    suspend fun cacheParsedStory(
        id: Int,
        payload: String,
        summary: AlgoliaStorySummary?,
    ) = withContext(Dispatchers.Default) {
        writeMutex.withLock { repository.storeStory(id, payload, nowMillis(), summary) }
        Unit
    }

    override suspend fun cacheArticle(id: Int, url: String): Boolean =
        writeMutex.withLock { articleSnapshots.download(id, url, nowMillis()) }
}
