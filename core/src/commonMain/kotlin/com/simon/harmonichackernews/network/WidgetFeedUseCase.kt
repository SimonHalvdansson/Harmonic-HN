package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

data class WidgetFeedRequest(
    val storyType: StoryType,
    val fetchCount: Int,
    val visibleCount: Int,
    val itemTimeoutMillis: Long = DEFAULT_ITEM_TIMEOUT_MILLIS,
    val totalTimeoutMillis: Long = DEFAULT_TOTAL_TIMEOUT_MILLIS,
) {
    init {
        require(storyType.hackerNewsUrl != null) {
            "$storyType is not backed by an official Hacker News widget feed"
        }
        require(fetchCount > 0) { "Widget fetch count must be positive" }
        require(visibleCount > 0) { "Widget visible count must be positive" }
        require(itemTimeoutMillis > 0L) { "Widget item timeout must be positive" }
        require(totalTimeoutMillis > 0L) { "Widget total timeout must be positive" }
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

/**
 * Fetches the portable data needed by a platform widget or glance surface.
 *
 * Widget hosts are normally called through a synchronous platform API. They may bridge that API
 * at the platform edge, while this workflow remains suspend-first and shares the application's
 * normal Hacker News repository instead of maintaining a second raw-HTTP implementation.
 */
class WidgetFeedUseCase(
    private val repository: HackerNewsRepository,
) {
    suspend fun load(request: WidgetFeedRequest): WidgetFeedResult {
        val ids = try {
            repository.getStoryIds(request.storyType)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            return WidgetFeedResult.Failed(error)
        }
        if (ids.isEmpty()) return WidgetFeedResult.Failed()

        val startedAt = TimeSource.Monotonic.markNow()
        val stories = ArrayList<Story>(minOf(request.fetchCount, request.visibleCount))
        var failures = 0
        var timedOut = false

        for (storyId in ids.take(request.fetchCount)) {
            val remainingMillis = request.totalTimeoutMillis -
                startedAt.elapsedNow().inWholeMilliseconds
            if (remainingMillis <= 0L) {
                timedOut = true
                break
            }

            val story = try {
                withTimeoutOrNull(minOf(request.itemTimeoutMillis, remainingMillis)) {
                    repository.getStory(storyId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }
            if (story == null) {
                failures++
            } else if (!story.isComment) {
                stories += story
                if (stories.size == request.visibleCount) break
            } else {
                failures++
            }
        }

        if (stories.isEmpty()) return WidgetFeedResult.Failed()
        return WidgetFeedResult.Loaded(
            stories = stories.toList(),
            availableStoryCount = ids.size,
            failedStoryCount = failures,
            timedOut = timedOut,
        )
    }
}

fun widgetStoryTypeForUrl(url: String?): StoryType = StoryType.entries.firstOrNull { type ->
    type.hackerNewsUrl == url
} ?: StoryType.TOP_STORIES
