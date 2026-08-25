package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertContentEquals

class CommentSearchHighlightTest {
    @Test
    fun matchingIsCaseInsensitiveAndMarksEveryNonOverlappingOccurrence() {
        assertContentEquals(
            floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 1f),
            searchMatchEmphasis("Banana AN", "an"),
        )
    }

    @Test
    fun blankSearchHasNoEmphasis() {
        assertContentEquals(
            FloatArray(7),
            searchMatchEmphasis("comment", "  "),
        )
    }
}
