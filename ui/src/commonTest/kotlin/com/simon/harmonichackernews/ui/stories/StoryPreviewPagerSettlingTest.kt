package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoryPreviewPagerSettlingTest {
    @Test
    fun centeredIdlePagerDoesNotRequestSettle() {
        assertNull(
            storyPreviewPagerSettleTarget(
                isScrollInProgress = false,
                isPointerPressed = false,
                currentPage = 4,
                currentPageOffsetFraction = 0f,
            ),
        )
    }

    @Test
    fun activeSwipeDoesNotRequestSettle() {
        assertNull(
            storyPreviewPagerSettleTarget(
                isScrollInProgress = true,
                isPointerPressed = true,
                currentPage = 4,
                currentPageOffsetFraction = 0.25f,
            ),
        )
    }

    @Test
    fun nestedScrollAndHeldTouchDoNotRequestSettleEvenWhenPagerReportsIdle() {
        for (offset in listOf(-0.25f, 0.25f)) {
            assertNull(
                storyPreviewPagerSettleTarget(
                    isScrollInProgress = false,
                    isPointerPressed = true,
                    currentPage = 4,
                    currentPageOffsetFraction = offset,
                ),
            )
        }
    }

    @Test
    fun flingAfterTouchReleaseDoesNotRequestSettle() {
        assertNull(
            storyPreviewPagerSettleTarget(
                isScrollInProgress = true,
                isPointerPressed = false,
                currentPage = 4,
                currentPageOffsetFraction = 0.25f,
            ),
        )
    }

    @Test
    fun interruptedIncompleteSwipeSettlesToCurrentPage() {
        assertEquals(
            4,
            storyPreviewPagerSettleTarget(
                isScrollInProgress = false,
                isPointerPressed = false,
                currentPage = 4,
                currentPageOffsetFraction = 0.25f,
            ),
        )
        assertEquals(
            4,
            storyPreviewPagerSettleTarget(
                isScrollInProgress = false,
                isPointerPressed = false,
                currentPage = 4,
                currentPageOffsetFraction = -0.25f,
            ),
        )
    }

    @Test
    fun invalidOffsetDoesNotRequestSettle() {
        assertNull(
            storyPreviewPagerSettleTarget(
                isScrollInProgress = false,
                isPointerPressed = false,
                currentPage = 4,
                currentPageOffsetFraction = Float.NaN,
            ),
        )
    }

    @Test
    fun scrollWheelMovesExactlyOnePageInItsDirection() {
        assertEquals(5, storyPreviewScrollWheelTarget(4, pageCount = 10, scrollDeltaY = 1f))
        assertEquals(3, storyPreviewScrollWheelTarget(4, pageCount = 10, scrollDeltaY = -1f))
        assertEquals(5, storyPreviewScrollWheelTarget(4, pageCount = 10, scrollDeltaY = 120f))
    }

    @Test
    fun scrollWheelDoesNotMovePastPagerEdges() {
        assertNull(storyPreviewScrollWheelTarget(0, pageCount = 10, scrollDeltaY = -1f))
        assertNull(storyPreviewScrollWheelTarget(9, pageCount = 10, scrollDeltaY = 1f))
        assertNull(storyPreviewScrollWheelTarget(4, pageCount = 10, scrollDeltaY = 0f))
    }

}
