package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class UiTextHotPathEquivalenceTest {
    @Test
    fun mathTokenizerPreservesEscapedBlankAndUnclosedDelimiters() {
        val random = Random(1847)
        val alphabet = "abc $\\()[]\n\t"
        val samples = buildList {
            add("")
            add("ordinary prose without formulas")
            for (slashes in 0..128) {
                val prefix = "\\".repeat(slashes)
                for (delimiters in listOf("$" to "$", "$$" to "$$", "\\(" to "\\)", "\\[" to "\\]")) {
                    add(prefix + delimiters.first + "x + y" + delimiters.second)
                    add(prefix + delimiters.first + " \n\t" + delimiters.second)
                    add(prefix + delimiters.first + "unfinished")
                }
            }
            repeat(4_000) {
                add(buildString { repeat(random.nextInt(96)) { append(alphabet[random.nextInt(alphabet.length)]) } })
            }
        }
        for (text in samples) assertEquals(legacyMathSegments(text), previewMathSegments(text), text)
    }

    @Test
    fun quoteSegmentsPreserveTextAnnotationsAndDuplicateUnorderedBoundaries() {
        val random = Random(3921)
        repeat(600) {
            val source = buildAnnotatedString {
                repeat(random.nextInt(1, 80)) { append("ab \n"[random.nextInt(4)]) }
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), 0, length)
                addStringAnnotation("url", "https://example.com", 0, length)
                repeat(random.nextInt(24)) {
                    val start = random.nextInt(length + 1)
                    val end = random.nextInt(start, length + 1)
                    addStringAnnotation(COMMENT_QUOTE_TAG, "quote", start, end)
                    if (random.nextBoolean()) addStringAnnotation(COMMENT_QUOTE_TAG, "quote", start, end)
                }
            }
            assertEquals(legacySpacedSegments(source), source.quoteSpacedSegments(), source.text)
            assertEquals(legacyPaddingSegments(source), source.quotePaddingSegments(), source.text)
        }
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
