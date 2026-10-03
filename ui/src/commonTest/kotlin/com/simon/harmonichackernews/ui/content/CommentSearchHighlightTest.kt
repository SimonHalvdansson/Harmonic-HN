package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CommentSearchHighlightTest {
    @Test
    fun unmatchedSearchRetainsTheOriginalTextAndAnnotations() {
        val body = buildAnnotatedString {
            append("A formatted comment")
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 2, 11)
            addStringAnnotation("source", "comment", 0, length)
        }

        assertSame(body, highlightSearchMatches(body, "missing", Color.Yellow))
        assertSame(body, highlightSearchMatches(body, "  ", Color.Yellow))
    }

    @Test
    fun repeatedHighlightsPreserveExistingAnnotationsAndMatchRanges() {
        val body = buildAnnotatedString {
            append("İx İX")
            addStyle(SpanStyle(fontWeight = FontWeight.Light), 0, length)
            addStringAnnotation("source", "comment", 0, length)
        }
        val result = highlightSearchMatches(body, " x ", Color.Yellow)

        assertEquals(body.text, result.text)
        assertEquals(body.getStringAnnotations(0, body.length), result.getStringAnnotations(0, result.length))
        assertEquals(body.spanStyles.first(), result.spanStyles.first())
        assertEquals(listOf(1 to 2, 4 to 5), result.spanStyles.drop(1).map { it.start to it.end })
        assertEquals(List(2) { SpanStyle(color = Color.Yellow, fontWeight = FontWeight.Bold) },
            result.spanStyles.drop(1).map { it.item })
    }

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

    @Test
    fun unicodeCaseExpansionBeforeMatchPreservesOriginalOffsets() {
        assertContentEquals(
            floatArrayOf(0f, 1f),
            searchMatchEmphasis("İx", "x"),
        )
        assertContentEquals(
            floatArrayOf(0f, 1f, 0f, 0f, 1f),
            searchMatchEmphasis("İx İx", "x"),
        )
    }

    @Test
    fun unicodeCaseExpansionInQueryPreservesOriginalMatchLength() {
        assertContentEquals(
            floatArrayOf(1f, 1f, 1f),
            searchMatchEmphasis("İiI", "İ"),
        )
    }
}
