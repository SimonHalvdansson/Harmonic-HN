package com.simon.harmonichackernews.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerticalSwipeDismissTest {
    @Test
    fun visualProgressTracksDistanceInEitherDirection() {
        assertEquals(0f, verticalSwipeDismissProgress(0f, 1000f))
        assertEquals(0.5f, verticalSwipeDismissProgress(175f, 1000f))
        assertEquals(0.5f, verticalSwipeDismissProgress(-175f, 1000f))
        assertEquals(1f, verticalSwipeDismissProgress(500f, 1000f))
    }

    @Test
    fun distancePastThresholdDismissesInEitherDirection() {
        assertTrue(shouldDismissVerticalSwipe(121f, 0f, 120f, 1000f))
        assertTrue(shouldDismissVerticalSwipe(-121f, 0f, 120f, 1000f))
    }

    @Test
    fun fastFlingDismissesBeforeDistanceThreshold() {
        assertTrue(shouldDismissVerticalSwipe(30f, 1001f, 120f, 1000f))
        assertTrue(shouldDismissVerticalSwipe(-30f, -1001f, 120f, 1000f))
    }

    @Test
    fun shortSlowDragSettlesBack() {
        assertFalse(shouldDismissVerticalSwipe(119f, 999f, 120f, 1000f))
    }
}
