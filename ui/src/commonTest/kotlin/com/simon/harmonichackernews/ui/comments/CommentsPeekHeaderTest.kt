package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsPeekHeaderTest {
    @Test fun titleHandoffFollowsTheTitleInsteadOfTheBottomOfALongHeader() {
        val title = Rect(16f, 100f, 320f, 160f)
        assertEquals(0f, commentsPeekHandoff(-200f, title, 24f))
        assertEquals(0f, commentsPeekHandoff(-136f, title, 24f))
        assertEquals(0.5f, commentsPeekHandoff(-106f, title, 24f))
        assertEquals(1f, commentsPeekHandoff(-76f, title, 24f))
        assertEquals(1f, commentsPeekHandoff(0f, title, 24f))
    }

    @Test fun reverseScrollNeedsDeliberateConsumedMovementAndIgnoresProgrammaticMotion() {
        // No measured header represents a position deep in the thread.
        val state = CommentsPeekHeaderState(LazyListState(20), 24, 8f)
        fun scroll(y: Float, source: NestedScrollSource = NestedScrollSource.UserInput) =
            state.onPostScroll(Offset(0f, y), Offset.Zero, source)
        scroll(100f, NestedScrollSource.SideEffect)
        assertFalse(state.requested)
        state.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput)
        assertFalse(state.requested, "Overscroll must not reveal the header")
        scroll(4f)
        assertFalse(state.requested)
        scroll(4f)
        assertTrue(state.requested)
        scroll(-2f)
        assertTrue(state.requested, "A small direction wobble should not flicker")
        scroll(-6f)
        assertFalse(state.requested)
        scroll(7f)
        assertFalse(state.requested, "Travel resets on direction reversal")
        scroll(1f)
        assertTrue(state.requested)
    }
}
