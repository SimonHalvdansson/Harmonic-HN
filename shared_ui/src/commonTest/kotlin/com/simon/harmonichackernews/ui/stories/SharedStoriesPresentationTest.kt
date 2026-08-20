package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
