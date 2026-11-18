package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.ScrollableState
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsScrollbarPolicyTest {
    private fun indicator(offset: Int, size: Int, viewport: Int, back: Boolean, forward: Boolean): ScrollIndicatorState {
        val estimates = object : ScrollIndicatorState {
            override val scrollOffset = offset
            override val contentSize = size
            override val viewportSize = viewport
        }
        val list = object : ScrollableState by ScrollableState({ it }) {
            override val canScrollBackward = back
            override val canScrollForward = forward
        }
        return CommentsScrollIndicator(estimates, list)
    }

    @Test
    fun estimatedOffsetsAreClampedAndReachBothEndpoints() {
        assertEquals(0, indicator(50, 8000, 2300, false, true).scrollOffset)
        assertEquals(7000, indicator(6700, 8000, 1000, true, false).scrollOffset)
        assertEquals(4000, indicator(4000, 10000, 2000, true, true).scrollOffset)
        assertEquals(0, indicator(-20, 4000, 1000, true, true).scrollOffset)
        assertEquals(3000, indicator(5000, 4000, 1000, true, true).scrollOffset)
    }

    @Test
    fun fittingAndUnknownContentAreHiddenWhileOneTallRowStillScrolls() {
        assertEquals(0, indicator(0, 1100, 1000, false, false).contentSize)
        assertEquals(0, indicator(Int.MAX_VALUE, 4000, 1000, true, true).contentSize)
        assertEquals(0, indicator(0, Int.MAX_VALUE, 1000, true, true).contentSize)
        assertEquals(4000, indicator(3000, 4000, 1000, true, false).contentSize)
    }
}
