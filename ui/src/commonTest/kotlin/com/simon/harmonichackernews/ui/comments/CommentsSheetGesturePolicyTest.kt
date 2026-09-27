package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentsSheetGesturePolicyTest {
    @Test
    fun reachingTheTopAllowsTheNextPullImmediately() {
        assertFalse(canDragCommentsSheet(1, 20, true, true, false))
        assertFalse(canDragCommentsSheet(0, 1, true, true, false))
        assertTrue(canDragCommentsSheet(0, 0, false, true, false))
    }

    @Test
    fun rotationOrRestorationCannotUseAProvisionalTopPosition() {
        assertFalse(canDragCommentsSheet(0, 0, false, false, false))
        assertFalse(canDragCommentsSheet(0, 0, false, true, true))
        assertFalse(canDragCommentsSheet(0, 0, true, true, false))
        assertFalse(canDragCommentsSheet(12, 0, false, true, false))
        assertFalse(canDragCommentsSheet(0, 25, false, true, false))
        assertTrue(canDragCommentsSheet(0, 0, false, true, false))
    }
}
