package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
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
        assertEquals(
            listOf(TextRange(1, 3), TextRange(3, 5), TextRange(7, 9)),
            searchMatchRanges("Banana AN", "an"),
        )
    }

    @Test
    fun blankSearchHasNoEmphasis() {
        assertEquals(
            emptyList(),
            searchMatchRanges("comment", "  "),
        )
    }

    @Test
    fun unicodeCaseExpansionBeforeMatchPreservesOriginalOffsets() {
        assertEquals(
            listOf(TextRange(1, 2)),
            searchMatchRanges("İx", "x"),
        )
        assertEquals(
            listOf(TextRange(1, 2), TextRange(4, 5)),
            searchMatchRanges("İx İx", "x"),
        )
    }

    @Test
    fun unicodeCaseExpansionInQueryPreservesOriginalMatchLength() {
        assertEquals(
            listOf(TextRange(0, 1), TextRange(1, 2), TextRange(2, 3)),
            searchMatchRanges("İiI", "İ"),
        )
    }
    @Test
    fun sparseMatchRetainsOnlyOneRunRegardlessOfCommentLength() {
        val body = buildAnnotatedString { append("x".repeat(100_000)); append("needle") }
        val transition = SearchHighlightTransition().retarget(body, "needle", 0f)
        assertEquals(1, transition.runs.size)
        assertEquals(100_000, transition.runs.single().start)
        assertSame(body, highlightSearchMatches(body, transition, 0f, Color.Black, Color.Yellow))
    }

    @Test
    fun interruptedSearchRetainsCurrentEmphasisAndOriginalWeightBoundaries() {
        val body = buildAnnotatedString {
            append("abcdef")
            addStyle(SpanStyle(fontWeight = FontWeight.Light), 0, 3)
            addStyle(SpanStyle(fontWeight = FontWeight.Medium), 3, 6)
            addStringAnnotation("source", "comment", 0, length)
        }
        val first = SearchHighlightTransition().retarget(body, "bcde", 0f)
        val second = first.retarget(body, "de", 0.5f)
        val before = highlightSearchMatches(body, first, 0.5f, Color.Black, Color.Yellow)
        val after = highlightSearchMatches(body, second, 0f, Color.Black, Color.Yellow)
        // A new query starts from exactly the visible colors/weights, even across old spans.
        for (index in body.text.indices) {
            fun styleAt(text: androidx.compose.ui.text.AnnotatedString) = text.spanStyles
                .lastOrNull { index in it.start until it.end }?.item
            assertEquals(styleAt(before), styleAt(after))
        }
        val finished = highlightSearchMatches(body, second, 1f, Color.Black, Color.Yellow)
        assertEquals(listOf(3 to 6, 3 to 5),
            finished.spanStyles.drop(1).map { it.start to it.end })
        assertEquals(body.getStringAnnotations(0, body.length), finished.getStringAnnotations(0, finished.length))
    }

}
