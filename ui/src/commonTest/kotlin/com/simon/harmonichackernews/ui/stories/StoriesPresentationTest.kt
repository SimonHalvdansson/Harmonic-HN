package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.cache.StoryCacheState
import com.simon.harmonichackernews.cache.StoryCacheStatus
import com.simon.harmonichackernews.presentation.StoriesState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoriesPresentationTest {
    @Test
    fun partialCacheCompletionExplainsThatSomeItemsFailed() {
        val presentation = storiesScreenPresentation(
            StoriesState(
                cache = StoryCacheState(
                    status = StoryCacheStatus.PARTIAL,
                    progressVisible = true,
                ),
            ),
            StoriesPlatformPresentation(),
        )
        assertTrue(presentation.cacheProgressVisible)
        assertEquals("Some items could not be cached", presentation.cacheProgressStatus)
    }

    @Test
    fun completedPredictiveSearchBackDoesNotExposeStaleAnimatedProgress() {
        assertEquals(
            0f,
            resolvedStandardSearchProgress(
                searching = false,
                suppressSearchAutoFocus = true,
                animatedProgress = 1f,
            ),
        )
    }

    @Test
    fun ordinarySearchTransitionKeepsItsAnimatedProgress() {
        assertEquals(
            0.4f,
            resolvedStandardSearchProgress(
                searching = false,
                suppressSearchAutoFocus = false,
                animatedProgress = 0.4f,
            ),
        )
    }

    @Test
    fun searchLayerIsAbsentUntilSearchOrATransitionNeedsIt() {
        assertFalse(
            shouldComposeSearchLayer(
                searching = false,
                predictiveBackActive = false,
                searchProgress = 0f,
            ),
        )
        assertTrue(
            shouldComposeSearchLayer(
                searching = true,
                predictiveBackActive = false,
                searchProgress = 0f,
            ),
        )
        assertTrue(
            shouldComposeSearchLayer(
                searching = false,
                predictiveBackActive = false,
                searchProgress = 0.01f,
            ),
        )
        assertTrue(
            shouldComposeSearchLayer(
                searching = false,
                predictiveBackActive = true,
                searchProgress = 0f,
            ),
        )
    }

    @Test
    fun exitingSavedEmptyStateRetainsItsApplicableMessage() {
        assertEquals(
            "No bookmarked comments",
            retainedEmptySavedListText(
                current = "No bookmarked comments",
                next = "No bookmarks",
                visible = false,
            ),
        )
        assertEquals(
            "No bookmarked stories",
            retainedEmptySavedListText(
                current = "No bookmarked comments",
                next = "No bookmarked stories",
                visible = true,
            ),
        )
    }
}
