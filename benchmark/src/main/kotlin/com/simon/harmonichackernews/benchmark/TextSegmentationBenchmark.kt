@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.content.COMMENT_QUOTE_TAG
import com.simon.harmonichackernews.ui.content.PreviewMathSegment
import com.simon.harmonichackernews.ui.content.previewMathSegments
import com.simon.harmonichackernews.ui.content.quotePaddingSegments
import com.simon.harmonichackernews.ui.content.quoteSpacedSegments
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paired pre-change/production implementations. Fixtures and equality checks are outside timing. */
@RunWith(AndroidJUnit4::class)
class TextSegmentationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    private val prose = "The algorithm handles a changing workload while preserving the previous results. ".repeat(24)
    private val formulas = "The bound is ${'$'}O(n \\log n)${'$'} with inline \\(x_i^2\\) and display \\[\\sum_i x_i\\]. ".repeat(8)
    private val spacedQuote = quoteFixture("\n\n", count = 1)
    private val paddedQuote = quoteFixture("\n", count = 1)
    private val spacedQuotes = quoteFixture("\n\n", count = 24)
    private val paddedQuotes = quoteFixture("\n", count = 24)

    @Test fun mathProseBefore() = measureMath(prose, before = true)
    @Test fun mathProseAfter() = measureMath(prose, before = false)
    @Test fun mathTwentyFourFormulasBefore() = measureMath(formulas, before = true)
    @Test fun mathTwentyFourFormulasAfter() = measureMath(formulas, before = false)
    @Test fun quoteSpacingOneBefore() = measureSpacing(spacedQuote, before = true)
    @Test fun quoteSpacingOneAfter() = measureSpacing(spacedQuote, before = false)
    @Test fun quoteSpacingTwentyFourBefore() = measureSpacing(spacedQuotes, before = true)
    @Test fun quoteSpacingTwentyFourAfter() = measureSpacing(spacedQuotes, before = false)
    @Test fun quotePaddingOneBefore() = measurePadding(paddedQuote, before = true)
    @Test fun quotePaddingOneAfter() = measurePadding(paddedQuote, before = false)
    @Test fun quotePaddingTwentyFourBefore() = measurePadding(paddedQuotes, before = true)
    @Test fun quotePaddingTwentyFourAfter() = measurePadding(paddedQuotes, before = false)

    private fun measureMath(text: String, before: Boolean) {
        check(legacyMathSegments(text) == previewMathSegments(text))
        if (before) benchmarkRule.measureRepeated { result = legacyMathSegments(text) }
        else benchmarkRule.measureRepeated { result = previewMathSegments(text) }
    }

    private fun measureSpacing(body: AnnotatedString, before: Boolean) {
        check(legacySpacedSegments(body) == body.quoteSpacedSegments())
        if (before) benchmarkRule.measureRepeated { result = legacySpacedSegments(body) }
        else benchmarkRule.measureRepeated { result = body.quoteSpacedSegments() }
    }

    private fun measurePadding(body: AnnotatedString, before: Boolean) {
        check(legacyPaddingSegments(body) == body.quotePaddingSegments())
        if (before) benchmarkRule.measureRepeated { result = legacyPaddingSegments(body) }
        else benchmarkRule.measureRepeated { result = body.quotePaddingSegments() }
    }

    private fun quoteFixture(separator: String, count: Int): AnnotatedString = buildAnnotatedString {
        repeat(count) { index ->
            append("Reply $index with context.")
            append(separator)
            val start = length
            append("Quoted words from source $index.")
            addStringAnnotation(COMMENT_QUOTE_TAG, "quote", start, length)
            addStringAnnotation("url", "https://example.com/$index", start, length)
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            append(separator)
        }
        append("The final reply.")
    }

    private fun legacyMathSegments(text: String): List<PreviewMathSegment> {
        val segments = mutableListOf<PreviewMathSegment>()
        var plainStart = 0
        var index = 0
        fun escaped(at: Int): Boolean {
            var slashes = 0
            var previous = at - 1
            while (previous >= 0 && text[previous--] == '\\') slashes++
            return slashes % 2 != 0
        }
        while (index < text.length) {
            val opening = when {
                escaped(index) -> null
                text.startsWith("$$", index) -> "$$"
                text[index] == '$' -> "$"
                text.startsWith("\\(", index) -> "\\("
                text.startsWith("\\[", index) -> "\\["
                else -> null
            }
            if (opening == null) { index++; continue }
            val closing = when (opening) { "\\(" -> "\\)"; "\\[" -> "\\]"; else -> opening }
            val start = index + opening.length
            var end = text.indexOf(closing, start)
            while (end >= 0 && escaped(end)) end = text.indexOf(closing, end + closing.length)
            if (end < 0 || text.substring(start, end).isBlank()) { index += opening.length; continue }
            if (plainStart < index) segments += PreviewMathSegment(text.substring(plainStart, index))
            segments += PreviewMathSegment(text.substring(index, end + closing.length), text.substring(start, end), opening == "$$" || opening == "\\[")
            index = end + closing.length
            plainStart = index
        }
        if (plainStart < text.length) segments += PreviewMathSegment(text.substring(plainStart))
        return segments
    }

    private fun legacySpacedSegments(body: AnnotatedString): List<AnnotatedString> = with(body) {
        val blankLines = mutableSetOf<Int>()
        getStringAnnotations(COMMENT_QUOTE_TAG, 0, length).forEach { quote ->
            if (quote.start >= 2 && text.substring(quote.start - 2, quote.start) == "\n\n") blankLines += quote.start - 2
            if (quote.end + 1 < length && text.substring(quote.end, quote.end + 2) == "\n\n") blankLines += quote.end
        }
        if (blankLines.isEmpty()) return listOf(body)
        return buildList {
            var copiedThrough = 0
            blankLines.sorted().forEach { index ->
                if (index < copiedThrough) return@forEach
                add(body.subSequence(copiedThrough, index))
                copiedThrough = index + 2
            }
            add(body.subSequence(copiedThrough, length))
        }
    }

    private fun legacyPaddingSegments(body: AnnotatedString): List<AnnotatedString> = with(body) {
        val boundaries = mutableSetOf<Int>()
        getStringAnnotations(COMMENT_QUOTE_TAG, 0, length).forEach { quote ->
            for (index in listOf(quote.start - 1, quote.end)) {
                if (index > 0 && index < length - 1 && text[index] == '\n' && text[index - 1] != '\n' && text[index + 1] != '\n') boundaries += index
            }
        }
        if (boundaries.isEmpty()) return listOf(body)
        return buildList {
            var start = 0
            boundaries.sorted().forEach { end ->
                add(body.subSequence(start, end))
                start = end + 1
            }
            add(body.subSequence(start, length))
        }
    }
}
