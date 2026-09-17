package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryPaginationSessionTest {
    @Test
    fun paginationIsForcedForScrapedFrontpages() {
        assertFalse(StoryPaginationPolicy.isEnabled(false, StoryType.TOP_STORIES))
        assertTrue(StoryPaginationPolicy.isEnabled(true, StoryType.TOP_STORIES))
        assertTrue(StoryPaginationPolicy.isEnabled(false, StoryType.CLASSIC))
    }

    @Test
    fun visibleTargetHonoursPaginationAndEmptyLists() {
        assertEquals(-1, StoryPaginationPolicy.visibleLoadTargetIndex(0, true, 30))
        assertEquals(19, StoryPaginationPolicy.visibleLoadTargetIndex(100, false, Int.MAX_VALUE))
        assertEquals(29, StoryPaginationPolicy.visibleLoadTargetIndex(100, true, 30))
        assertEquals(9, StoryPaginationPolicy.visibleLoadTargetIndex(10, true, 30))
    }

    @Test
    fun scrolledTargetPreloadsSeventeenStoriesPastTheViewport() {
        assertEquals(-1, StoryPaginationPolicy.scrolledLoadTargetIndex(0, 10))
        assertEquals(19, StoryPaginationPolicy.scrolledLoadTargetIndex(100, 0))
        assertEquals(29, StoryPaginationPolicy.scrolledLoadTargetIndex(100, 12))
        assertEquals(24, StoryPaginationPolicy.scrolledLoadTargetIndex(25, 20))
    }

    @Test
    fun pagePlanTracksOnlyUnloadedStoriesAndRejectsStaleResults() {
        val session = StoryPaginationSession(pageSize = 2)
        val stories = listOf(story(1, loaded = true), story(2), story(3), story(4))

        val plan = session.beginNextPage(
            stories = stories,
            loadedThroughIndex = 0,
            visibleStoryCount = 2,
            requestGeneration = 7,
        )

        assertEquals(2, plan?.targetLoadedIndex)
        assertEquals(4, plan?.nextVisibleCount)
        assertEquals(setOf(2, 3), plan?.pendingStoryIds)
        assertFalse(session.finishStory(2, requestGeneration = 6))
        assertFalse(session.finishStory(2, requestGeneration = 7))
        assertTrue(session.finishStory(3, requestGeneration = 7))
        assertFalse(session.hasPendingStories())
    }

    @Test
    fun emptyFeedHasNoPagePlan() {
        assertNull(StoryPaginationSession().beginNextPage(emptyList(), -1, 30, 1))
    }

    private fun story(id: Int, loaded: Boolean = false) =
        Story("Story $id", id, loaded, false)
}
