package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals

class CommentsScrollPolicyTest {
    @Test
    fun searchResultUsesTheInsetThatIncludesFloatingBackNavigation() {
        assertEquals(
            112,
            commentScrollTopOffset(
                requestedTopOffsetPx = 48,
                searchResult = true,
                navigationTopOffsetPx = 112,
            ),
        )
        assertEquals(
            112,
            commentScrollTopOffset(
                requestedTopOffsetPx = 48,
                searchResult = false,
                navigationTopOffsetPx = 112,
            ),
        )
    }

    @Test
    fun restoresExactReadingPositionAndPreservesLargerRequestedInsets() {
        assertEquals(-280, commentScrollTopOffset(-280, false, 112, restorePosition = true))
        assertEquals(160, commentScrollTopOffset(160, false, 112))
        assertEquals(48, commentScrollTopOffset(48, false, 48))
    }
}
