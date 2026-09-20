package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class WidgetFeedRequest(
    val storyType: StoryType,
    val fetchCount: Int,
    val visibleCount: Int,
    val itemTimeoutMillis: Long = DEFAULT_ITEM_TIMEOUT_MILLIS,
    val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) {
    init {
        require(storyType in StoryTypeMenuPolicy.baseFrontpages + StoryType.additionalFrontpages)
        require(fetchCount > 0 && visibleCount > 0)
        require(itemTimeoutMillis > 0 && totalTimeoutMillis > 0)
    }

    companion object {
        const val DEFAULT_ITEM_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_TOTAL_TIMEOUT_MILLIS = 60_000L
    }
}

sealed interface WidgetFeedResult {
    data class Loaded(
        val stories: List<Story>,
        val availableStoryCount: Int,
        val failedStoryCount: Int,
        val timedOut: Boolean,
    ) : WidgetFeedResult

    data class Failed(val cause: Throwable? = null) : WidgetFeedResult
}

/** Bounded parallel loading, including feed discovery in the overall deadline. */
class WidgetFeedUseCase(
    private val repository: HackerNewsRepository,
    private val feedLoader: suspend (StoryType, Int) -> StoryFeedResult = { type, _ ->
        StoryFeedResult.ItemIds(repository.getStoryIds(type))
    },
) {
    suspend fun load(request: WidgetFeedRequest): WidgetFeedResult {
        val stories = mutableMapOf<Int, Story>()
        val lock = Mutex()
        var available = 0
        var failures = 0
        var firstFailure: Throwable? = null
        var itemTimedOut = false
        val completed = try {
            withTimeoutOrNull(request.totalTimeoutMillis) {
                val source = feedLoader(request.storyType, request.fetchCount)
                if (source is StoryFeedResult.LinkDirectory) {
                    available = source.stories.size
                    source.stories.take(request.visibleCount).forEachIndexed { index, story ->
                        stories[index] = story
                    }
                } else {
                    val ids = when (source) {
                        is StoryFeedResult.ItemIds -> source.ids
                        is StoryFeedResult.Scraped -> source.page.itemIds
                    }.distinct()
                    available = ids.size
                    for (batch in ids.take(request.fetchCount).withIndex().chunked(4)) {
                        coroutineScope {
                            batch.map { (index, id) ->
                                async {
                                    var failure: Throwable? = null
                                    var timedOut = false
                                    val story = try {
                                        // A nullable result is different from a timed-out request.
                                        val response = withTimeoutOrNull(request.itemTimeoutMillis) {
                                            listOf(repository.getStory(id))
                                        }
                                        timedOut = response == null
                                        response?.single()
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        failure = error
                                        null
                                    }
                                    lock.withLock {
                                        if (story != null && !story.loadingFailed &&
                                            (!story.isComment || request.storyType.usesCommentRows())) {
                                            stories[index] = story
                                        } else {
                                            failures++
                                            itemTimedOut = itemTimedOut || timedOut
                                            if (firstFailure == null) {
                                                firstFailure = failure ?: IllegalStateException(
                                                    if (timedOut) "Timed out loading HN item $id"
                                                    else "HN item $id was unavailable",
                                                )
                                            }
                                        }
                                    }
                                }
                            }.awaitAll()
                        }
                        if (stories.size >= request.visibleCount) break
                    }
                }
                true
            } ?: false
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return WidgetFeedResult.Failed(error)
        }
        if (stories.isEmpty()) {
            return WidgetFeedResult.Failed(
                firstFailure ?: IllegalStateException(
                    if (!completed) "Timed out loading ${request.storyType.label}"
                    else "${request.storyType.label} returned no available items",
                ),
            )
        }
        return WidgetFeedResult.Loaded(
            stories = stories.entries.sortedBy { it.key }.map { it.value }.take(request.visibleCount),
            availableStoryCount = available,
            failedStoryCount = failures,
            timedOut = !completed || itemTimedOut,
        )
    }
}

fun widgetStoryTypeForUrl(url: String?): StoryType =
    (StoryTypeMenuPolicy.baseFrontpages + StoryType.additionalFrontpages).firstOrNull { type ->
        type.name == url || type.hackerNewsUrl != null && type.hackerNewsUrl == url
    } ?: StoryType.TOP_STORIES
