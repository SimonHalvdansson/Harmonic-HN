package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedStoriesPresentationTest {
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
}
