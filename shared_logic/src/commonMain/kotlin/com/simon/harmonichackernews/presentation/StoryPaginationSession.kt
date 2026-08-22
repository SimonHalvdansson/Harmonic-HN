package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story

data class StoryPageLoadPlan(
    val targetLoadedIndex: Int,
    val nextVisibleCount: Int,
    val pendingStoryIds: Set<Int>,
)

/** Pure pagination rules shared by every future stories-screen host. */
object StoryPaginationPolicy {
    const val DEFAULT_PAGE_SIZE = 30
    const val DEFAULT_INITIAL_LOAD_COUNT = 20
    const val DEFAULT_VISIBLE_LOAD_AHEAD = 17

    fun isEnabled(userEnabled: Boolean, storyType: StoryType?): Boolean =
        userEnabled || storyType?.isScrapedFrontpage == true

    fun initialVisibleCount(enabled: Boolean, pageSize: Int = DEFAULT_PAGE_SIZE): Int =
        if (enabled) pageSize else Int.MAX_VALUE

    fun visibleLoadTargetIndex(
        storyCount: Int,
        paginationEnabled: Boolean,
        visibleStoryCount: Int,
        initialLoadCount: Int = DEFAULT_INITIAL_LOAD_COUNT,
    ): Int {
        if (storyCount <= 0) return -1
        val requestedCount = if (paginationEnabled) visibleStoryCount else initialLoadCount
        return minOf(requestedCount, storyCount) - 1
    }

    fun scrolledLoadTargetIndex(
        storyCount: Int,
        lastVisibleIndex: Int,
        initialLoadCount: Int = DEFAULT_INITIAL_LOAD_COUNT,
        loadAhead: Int = DEFAULT_VISIBLE_LOAD_AHEAD,
    ): Int {
        if (storyCount <= 0) return -1
        return maxOf(
            initialLoadCount - 1,
            lastVisibleIndex.coerceAtLeast(0) + loadAhead,
        ).coerceAtMost(storyCount - 1)
    }
}

/**
 * Lifecycle-independent owner for one feed's page boundary and pending item loads.
 * Network calls remain the responsibility of the platform host.
 */
class StoryPaginationSession(
    private val pageSize: Int = StoryPaginationPolicy.DEFAULT_PAGE_SIZE,
) {
    private val pendingStoryIds = linkedSetOf<Int>()
    private var generation = -1

    fun beginNextPage(
        stories: List<Story>,
        loadedThroughIndex: Int,
        visibleStoryCount: Int,
        requestGeneration: Int,
    ): StoryPageLoadPlan? {
        clear()
        if (stories.isEmpty()) return null

        generation = requestGeneration
        val targetIndex = (loadedThroughIndex + pageSize).coerceAtMost(stories.lastIndex)
        val firstIndex = (loadedThroughIndex + 1).coerceAtLeast(0)
        if (firstIndex <= targetIndex) {
            for (index in firstIndex..targetIndex) {
                stories[index].takeUnless(Story::loaded)?.id?.let(pendingStoryIds::add)
            }
        }
        return StoryPageLoadPlan(
            targetLoadedIndex = targetIndex,
            nextVisibleCount = (visibleStoryCount + pageSize).coerceAtMost(stories.size),
            pendingStoryIds = pendingStoryIds.toSet(),
        )
    }

    /** Returns true when this result completed the current page. */
    fun finishStory(storyId: Int, requestGeneration: Int): Boolean {
        if (requestGeneration != generation) return false
        pendingStoryIds.remove(storyId)
        return pendingStoryIds.isEmpty()
    }

    fun hasPendingStories(): Boolean = pendingStoryIds.isNotEmpty()

    fun clear() {
        pendingStoryIds.clear()
        generation = -1
    }
}
