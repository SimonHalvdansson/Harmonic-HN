package com.simon.harmonichackernews.cache

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.StoryCachePayloadParser
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

interface StoryCacheSink {
    suspend fun cacheStory(id: Int, payload: String)
    suspend fun cacheArticle(id: Int, url: String): Boolean
}

data class StoryCacheRequest(
    val storyCount: Int,
    val cacheArticleSnapshots: Boolean,
)

data class StoryCacheProgress(
    val completed: Int,
    val total: Int,
)

enum class StoryCacheOutcome {
    FINISHED,
    EMPTY,
    FAILED,
}

/** Platform-neutral batch workflow; app shells only provide persistence and article downloads. */
class StoryCacheUseCase(
    private val hackerNewsRepository: HackerNewsRepository,
    private val algoliaRepository: AlgoliaRepository,
    private val sink: StoryCacheSink,
) {
    suspend fun execute(
        request: StoryCacheRequest,
        onProgress: (StoryCacheProgress) -> Unit,
    ): StoryCacheOutcome {
        if (request.storyCount <= 0) return StoryCacheOutcome.EMPTY
        val storyIds = try {
            hackerNewsRepository.getStoryIds(StoryType.TOP_STORIES)
                .take(request.storyCount)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return StoryCacheOutcome.FAILED
        }
        if (storyIds.isEmpty()) return StoryCacheOutcome.EMPTY

        var completed = 0
        val progressMutex = Mutex()
        val concurrency = Semaphore(MAX_CONCURRENT_STORY_CACHES)
        coroutineScope {
            storyIds.map { id ->
                async {
                    concurrency.withPermit {
                        try {
                            val payload = algoliaRepository.getItemJson(id)
                            sink.cacheStory(id, payload)
                            if (request.cacheArticleSnapshots) {
                                StoryCachePayloadParser.externalArticleUrl(payload)?.let { url ->
                                    sink.cacheArticle(id, url)
                                }
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            // A failed item does not prevent the rest of the requested batch.
                        }
                    }
                    progressMutex.withLock {
                        completed++
                        onProgress(StoryCacheProgress(completed, storyIds.size))
                    }
                }
            }.awaitAll()
        }
        return StoryCacheOutcome.FINISHED
    }

    private companion object {
        const val MAX_CONCURRENT_STORY_CACHES = 4
    }
}
