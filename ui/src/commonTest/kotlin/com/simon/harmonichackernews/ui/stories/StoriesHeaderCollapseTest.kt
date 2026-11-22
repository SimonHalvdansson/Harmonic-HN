package com.simon.harmonichackernews.ui.stories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoriesHeaderCollapseTest {
    @Test
    fun peekingControlsMoveMonotonicallyToTheirRestingPosition() {
        for (lift in listOf(4f, 24f)) {
            for (reveal in listOf(0f, .25f, .5f, .75f, 1f)) {
                val height = 100f
                var previous = storiesControlsOffsetPx(200, height, lift, reveal)
                for (collapse in 199 downTo 0) {
                    val offset = storiesControlsOffsetPx(collapse, height, lift, reveal)
                    assertTrue(offset in previous..0f)
                    previous = offset
                }
                assertEquals(0f, previous)
            }
            assertEquals(-lift, storiesControlsOffsetPx(200, 100f, lift, 1f))
            assertEquals(-lift / 2, storiesControlsOffsetPx(50, 100f, lift, 1f))
        }
    }

    @Test
    fun deliberateReverseScrollRevealsControlsUntilForwardScrollOrTop() {
        val peek = FeedControlsReveal(24f)
        peek.onScroll(30f, atTop = false, headerHidden = false)
        assertFalse(peek.visible)
        peek.onScroll(-8f, atTop = false, headerHidden = true)
        peek.onScroll(15f, atTop = false, headerHidden = true)
        assertFalse(peek.visible)
        peek.onScroll(9f, atTop = false, headerHidden = true)
        assertTrue(peek.visible)
        peek.onScroll(-8f, atTop = false, headerHidden = true)
        assertTrue(peek.visible)
        peek.onScroll(-16f, atTop = false, headerHidden = true)
        assertFalse(peek.visible)
        peek.onScroll(30f, atTop = false, headerHidden = true)
        peek.onScroll(1f, atTop = true, headerHidden = false)
        assertFalse(peek.visible)
    }
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
