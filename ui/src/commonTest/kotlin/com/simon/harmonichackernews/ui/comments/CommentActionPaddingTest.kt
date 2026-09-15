package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentActionPaddingTest {
    @Test
    fun paddingUsesAvailableWidthAndLeavesRoomForTheActions() {
        for (count in 5..8) {
            for (width in 270..1000) {
                val padding = commentActionPadding(width.toFloat(), count)
                val buttonWidth = commentActionButtonWidth(width.toFloat(), count)
                assertTrue(padding >= 0f)
                if (width >= count * buttonWidth) assertTrue(width - 2f * padding >= count * buttonWidth - 0.001f)
                else assertEquals(0f, padding)
            }
        }
        assertEquals(0f, commentActionPadding(320f, 5))
        assertEquals(0f, commentActionPadding(360f, 5))
        assertEquals(243f, commentActionPadding(900f, 5))
        assertEquals(48f, commentActionButtonWidth(320f, 6))
        assertEquals(54f, commentActionButtonWidth(324f, 6))
        assertEquals(54f, commentActionButtonWidth(900f, 6))
    }
}
