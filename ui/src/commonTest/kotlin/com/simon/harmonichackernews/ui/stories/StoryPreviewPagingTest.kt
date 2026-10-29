package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryPreviewPagingTest {
    @Test
    fun dragDistanceIsIndependentOfHeightAndDensity() {
        for (density in listOf(1f, 2f, 3f)) {
            for (heightDp in listOf(400, 800, 1200, 1600)) {
                val pageSizePx = (heightDp * density).toInt()
                val threshold = storyPreviewSnapPositionalThreshold(pageSizePx, 96f * density)
                assertEquals(96f, threshold * pageSizePx / density, 0.001f)
            }
        }
    }

    @Test
    fun unmeasuredAndVerySmallPagesHaveSafeThresholds() {
        assertEquals(0.5f, storyPreviewSnapPositionalThreshold(0, 96f))
        assertEquals(0.5f, storyPreviewSnapPositionalThreshold(80, 96f))
    }
}
