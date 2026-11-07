package com.simon.harmonichackernews.ui.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsSwipeBackTest {
    @Test fun distanceAndFlingThresholdsScaleWithDensity() {
        for (density in listOf(1f, 2f, 3f)) {
            val width = 400f * density
            fun commits(distance: Float, velocity: Float) =
                commitsCommentsSwipeBack(distance * density, width, velocity * density, density)
            assertTrue(commits(280f, 0f))
            assertFalse(commits(279f, 0f))
            assertTrue(commits(100f, 900f))
            assertFalse(commits(20f, 1_500f))
            assertFalse(commits(100f, 500f))
            assertFalse(commits(300f, -900f), "Reversing back towards the origin cancels")
            assertFalse(commits(-100f, 1_500f))
        }
        assertFalse(commitsCommentsSwipeBack(100f, 0f, 1_000f, 1f))
    }
}
