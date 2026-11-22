package com.simon.harmonichackernews.cache

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Clock

interface StoryCacheSink {
    /** Returns the article URL from the stored thread, when it has one. */
    suspend fun cacheStory(id: Int, payload: String, download: StoryDownloadPosition? = null): String?
    suspend fun cacheArticle(id: Int, url: String): Boolean
}

data class StoryDownloadPosition(val startedAtMillis: Long, val index: Int)

data class StoryCacheRequest(
    val storyCount: Int,
    val cacheArticleSnapshots: Boolean,
    // Count-only callers retain their original Top Stories source.
    val storyIds: List<Int>? = null,
)

data class StoryCacheProgress(
    val completed: Int,
    val total: Int,
)

enum class StoryCacheOutcome {
    FINISHED,
    PARTIAL,
    EMPTY,
    FAILED,
}

/** Platform-neutral batch workflow; app shells only provide persistence and article downloads. */
class StoryCacheUseCase(
    private val hackerNewsRepository: HackerNewsRepository,
    private val algoliaRepository: AlgoliaRepository,
    private val sink: StoryCacheSink,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    suspend fun execute(
        request: StoryCacheRequest,
        onProgress: (StoryCacheProgress) -> Unit,
    ): StoryCacheOutcome {
        if (request.storyCount <= 0) return StoryCacheOutcome.EMPTY
        val storyIds = try {
            (request.storyIds ?: hackerNewsRepository.getStoryIds(StoryType.TOP_STORIES))
                .asSequence()
                .filter { it > 0 }
                .distinct()
                .take(request.storyCount)
                .toList()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return StoryCacheOutcome.FAILED
        }
        if (storyIds.isEmpty()) return StoryCacheOutcome.EMPTY

        val startedAtMillis = nowMillis()
        var completed = 0
        val progressMutex = Mutex()
        val concurrency = Semaphore(MAX_CONCURRENT_STORY_CACHES)
        val outcomes = coroutineScope {
            storyIds.mapIndexed { index, id ->
                async {
                    val outcome = concurrency.withPermit {
                        var storySaved = false
                        try {
                            val payload = algoliaRepository.getItemJson(id)
                            val url = sink.cacheStory(id, payload, StoryDownloadPosition(startedAtMillis, index))
                            storySaved = true
                            if (request.cacheArticleSnapshots && url != null && !sink.cacheArticle(id, url)) {
                                return@withPermit StoryCacheOutcome.PARTIAL
                            }
                            StoryCacheOutcome.FINISHED
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            // A failed item does not prevent the rest of the requested batch.
                            if (storySaved) StoryCacheOutcome.PARTIAL else StoryCacheOutcome.FAILED
                        }
                    }
                    progressMutex.withLock {
                        completed++
                        onProgress(StoryCacheProgress(completed, storyIds.size))
                    }
                    outcome
                }
            }.awaitAll()
        }
        return when {
            outcomes.all { it == StoryCacheOutcome.FINISHED } -> StoryCacheOutcome.FINISHED
            outcomes.all { it == StoryCacheOutcome.FAILED } -> StoryCacheOutcome.FAILED
            else -> StoryCacheOutcome.PARTIAL
        }
    }

    private companion object {
        const val MAX_CONCURRENT_STORY_CACHES = 4
    }
}
