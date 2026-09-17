package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals

class StoriesHeaderCollapseTest {
    @Test
    fun firstItemTransitionKeepsCollapsingHeaderContinuously() {
        val itemHeights = listOf(180, 250)

        assertEquals(
            179,
            calculateStoriesHeaderCollapsePx(
                headerHeightPx = 400,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 179,
                precedingItemHeightPx = itemHeights::get,
            ),
        )
        assertEquals(
            180,
            calculateStoriesHeaderCollapsePx(
                headerHeightPx = 400,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                precedingItemHeightPx = itemHeights::get,
            ),
        )
        assertEquals(
            255,
            calculateStoriesHeaderCollapsePx(
                headerHeightPx = 400,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 75,
                precedingItemHeightPx = itemHeights::get,
            ),
        )
    }

    @Test
    fun collapseIsCappedAtHeaderHeight() {
        assertEquals(
            400,
            calculateStoriesHeaderCollapsePx(
                headerHeightPx = 400,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 30,
                precedingItemHeightPx = listOf(180, 250)::get,
            ),
        )
    }
}
