package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.random.Random

class CommentSearchHighlightTest {
    @Test
    fun retargetMatchesReferenceWithDuplicateEmptyAndOverlappingStyleRanges() {
        val random = Random(823)
        repeat(100) {
            val body = buildAnnotatedString {
                append("Banana needle İx and repeated needle. ".repeat(8))
                repeat(32) { span ->
                    val start = random.nextInt(length + 1)
                    val end = if (span % 4 == 0) start else random.nextInt(start, length + 1)
                    val style = if (span % 3 == 0) SpanStyle(color = Color.Red)
                        else SpanStyle(fontWeight = if (span % 2 == 0) FontWeight.Light else FontWeight.Medium)
                    addStyle(style, start, end)
                    if (span % 5 == 0) addStyle(style, start, end)
                }
            }
            var transition = SearchHighlightTransition()
            for (query in listOf("an", "needle", "İx", " missing ", "", "a")) {
                val progress = random.nextFloat()
                val expected = referenceRetarget(transition.runs, body, query, progress)
                val actual = transition.retarget(body, query, progress)
                assertEquals(expected.runs, actual.runs, "Query: $query")
                transition = actual
            }
        }
    }

    @Test
    fun inactiveSearchDoesNotCreateHighlightRunsFromFormatting() {
        val body = buildAnnotatedString {
            append("A formatted comment")
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), 2, 11)
            addStyle(SpanStyle(fontWeight = FontWeight.Light), 6, length)
            addStringAnnotation("source", "comment", 0, length)
        }
        for (query in listOf("", "  ", "missing")) {
            val transition = SearchHighlightTransition().retarget(body, query, 0.5f)
            assertEquals(emptyList(), transition.runs)
            assertSame(body, highlightSearchMatches(body, transition, 0.5f, Color.Black, Color.Yellow))
        }
    }

    @Test
    fun clearingSearchRetainsItsFadeUntilItFinishes() {
        val body = buildAnnotatedString {
            append("A formatted comment")
            addStyle(SpanStyle(fontWeight = FontWeight.Light), 2, 11)
        }
        val active = SearchHighlightTransition().retarget(body, "formatted", 0f)
        for (query in listOf("", "missing")) {
            val clearing = active.retarget(body, query, 0.5f)
            assertEquals(
                highlightSearchMatches(body, active, 0.5f, Color.Black, Color.Yellow),
                highlightSearchMatches(body, clearing, 0f, Color.Black, Color.Yellow),
            )
            assertSame(body, highlightSearchMatches(body, clearing, 1f, Color.Black, Color.Yellow))
        }
    }

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

/** Pre-optimization interval construction, kept as an independent differential oracle. */
private fun referenceRetarget(
    runs: List<SearchHighlightRun>,
    body: AnnotatedString,
    searchTerm: String,
    progress: Float,
): SearchHighlightTransition {
    val matches = searchMatchRanges(body.text, searchTerm)
    if (runs.isEmpty() && matches.isEmpty()) return SearchHighlightTransition()
    val boundaries = buildSet {
        runs.forEach { add(it.start); add(it.end) }
        matches.forEach { add(it.start); add(it.end) }
        body.spanStyles.forEach { span ->
            if (span.item.fontWeight != null) { add(span.start); add(span.end) }
        }
    }.sorted()
    var previousIndex = 0
    var matchIndex = 0
    return SearchHighlightTransition(buildList {
        for (index in 0 until boundaries.lastIndex) {
            val start = boundaries[index]
            val end = boundaries[index + 1]
            while (previousIndex < runs.size && runs[previousIndex].end <= start) previousIndex++
            while (matchIndex < matches.size && matches[matchIndex].end <= start) matchIndex++
            val from = runs.getOrNull(previousIndex)?.takeIf { start >= it.start }?.amount(progress) ?: 0f
            val to = if (matches.getOrNull(matchIndex)?.let { start >= it.start } == true) 1f else 0f
            if (from > 0.001f || to > 0f) {
                val weight = body.spanStyles.lastOrNull {
                    start >= it.start && start < it.end && it.item.fontWeight != null
                }?.item?.fontWeight ?: FontWeight.Normal
                add(SearchHighlightRun(start, end, from, to, weight))
            }
        }
    })
}
