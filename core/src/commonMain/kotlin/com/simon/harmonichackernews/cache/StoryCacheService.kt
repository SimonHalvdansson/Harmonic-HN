package com.simon.harmonichackernews.cache

import com.simon.harmonichackernews.data.ArticleSnapshotPolicy
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.CachedDownloadService
import com.simon.harmonichackernews.network.DownloadCachePolicy
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.network.HttpMediaType
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.network.KtorTransferClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    fun hydrateStory(story: com.simon.harmonichackernews.data.Story?): Boolean =
        repository.hydrateStory(story)

    fun recentStories(): List<com.simon.harmonichackernews.data.Story> =
        repository.recentStories(nowMillis())

    fun hasRecentStories(): Boolean = repository.hasRecentStories(nowMillis())

    fun loadStoryPayload(storyId: Int): String? = repository.loadStoryPayload(storyId)

    fun loadArticle(storyId: Int): String? = repository.loadArticle(storyId, nowMillis())

    fun articleUrl(storyId: Int): String? = repository.articleUrl(storyId)

    fun itemCount(): Int = repository.cachedItemIds().size

    suspend fun clear(): Int = writeMutex.withLock { repository.clear() }

    suspend fun remove(storyId: Int) = writeMutex.withLock { repository.remove(storyId) }

    suspend fun storeStory(id: Int, payload: String): Boolean =
        writeMutex.withLock { repository.storeStory(id, payload, nowMillis()) }

    override suspend fun cacheStory(id: Int, payload: String) {
        storeStory(id, payload)
    }

    override suspend fun cacheArticle(id: Int, url: String): Boolean =
        writeMutex.withLock { articleSnapshots.download(id, url, nowMillis()) }
}
