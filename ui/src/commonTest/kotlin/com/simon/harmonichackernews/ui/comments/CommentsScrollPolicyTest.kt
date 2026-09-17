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
            48,
            commentScrollTopOffset(
                requestedTopOffsetPx = 48,
                searchResult = false,
                navigationTopOffsetPx = 112,
            ),
        )
    }
}
