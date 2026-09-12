package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.presentation.CommentTextPolicy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One comment per iteration; all fixtures and equivalence checks are outside measurement. */
@RunWith(AndroidJUnit4::class)
class CommentParsingMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Volatile private var result: String? = null

    @Test fun spacingPlainBefore() = measureSpacing(plain, before = true)
    @Test fun spacingPlainAfter() = measureSpacing(plain, before = false)
    @Test fun spacingParagraphsBefore() = measureSpacing(paragraphs, before = true)
    @Test fun spacingParagraphsAfter() = measureSpacing(paragraphs, before = false)
    @Test fun spacingMixedBefore() = measureSpacing(mixed, before = true)
    @Test fun spacingMixedAfter() = measureSpacing(mixed, before = false)
    @Test fun spacingLongInlineBefore() = measureSpacing(longInline, before = true)
    @Test fun spacingLongInlineAfter() = measureSpacing(longInline, before = false)
    @Test fun anchorExpansionBefore() = measureAnchors(before = true)
    @Test fun anchorExpansionAfter() = measureAnchors(before = false)

    private fun measureSpacing(samples: List<String>, before: Boolean) {
        samples.forEach {
            assertEquals(FrozenParagraphSpacing.preserve(it), CommentTextPolicy.preserveLegacyParagraphSpacing(it))
        }
        var index = 0
        benchmarkRule.measureRepeated {
            val html = samples[index]
            index = (index + 1) % samples.size
            result = if (before) FrozenParagraphSpacing.preserve(html)
            else CommentTextPolicy.preserveLegacyParagraphSpacing(html)
        }
    }

    private fun measureAnchors(before: Boolean) {
        val original = FrozenCommentAnchorText()
        val optimized = Comment()
        anchors.forEach { html ->
            original.text = html
            optimized.text = html
            assertEquals(original.expandedAnchorText, optimized.expandedAnchorText)
        }
        var index = 0
        benchmarkRule.measureRepeated {
            // Rotate distinct sources so the existing per-comment cache never skips expansion.
            val html = anchors[index]
            index = (index + 1) % anchors.size
            result = if (before) {
                original.text = html
                original.expandedAnchorText
            } else {
                optimized.text = html
                optimized.expandedAnchorText
            }
        }
    }

    private val plain = listOf(
        "That matches what I observed while reading the implementation.",
        "I tried this with a larger data set. The outcome was consistent across runs &amp; machines.",
        "Thanks for sharing the details. 😀",
    )
    private val paragraphs = listOf(
        "The first paragraph explains the result.<p>The second paragraph explains the methodology.",
        "A longer discussion with two followups.<p>First, consider the input size.<p>Then inspect the output.",
        "One point.<P \n>Another point.",
    )
    private val mixed = plain + paragraphs + listOf(
        "A <i>small</i> clarification about the result.",
        "<p>First</p><p class='next'>Second</p>",
        "<div>First</div>\n<div>Second</div>",
        "See <a href='https://example.com/article'>the article</a> for more context.<p>There is more to say.",
    )
    private val longInline = listOf(
        "<span>${"A long inline comment without any paragraph or division boundaries. ".repeat(40)}</span>",
        "<a href='https://example.com'>Reference</a> ${"Additional discussion of this same reference. ".repeat(40)}",
    )
    private val anchors = listOf(
        "<p>See <a href='https://example.com/article'>https://example.com/...</a> for the details.</p>",
        "A <a href='https://example.com/first'>named link</a> and " +
            "<a href='https://example.org/second'>https://example.org/...</a>.",
        "<p><a href='https://example.com/?a=1&amp;b=2'>https://example.com/...</a></p>",
        "<a>Missing destination</a> <a href='https://other.example'>A full title</a>",
    )
}

/** Frozen production implementation before the literal guards were added. */
private object FrozenParagraphSpacing {
    private val paragraphStart = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
    private val adjacentParagraphs = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
    private val adjacentDivisions = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)

    fun preserve(html: String): String = html
        .replace(paragraphStart, "<br><br>")
        .replace(adjacentParagraphs, "</p><br><p")
        .replace(adjacentDivisions, "</div><br><div")
}

/** Frozen Comment text getter and expansion before caching the parsed CSS selector. */
private class FrozenCommentAnchorText {
    var text: String? = null
    private var cachedExpandedAnchorTextSource: String? = null
    private var cachedExpandedAnchorText: String? = null

    val expandedAnchorText: String?
        get() {
            val currentText = text
            if (currentText == cachedExpandedAnchorTextSource) return cachedExpandedAnchorText
            val expandedText = expandShortenedAnchorText(currentText)
            cachedExpandedAnchorTextSource = currentText
            cachedExpandedAnchorText = expandedText
            return expandedText
        }

    private fun expandShortenedAnchorText(inputHtml: String?): String? {
        if (inputHtml.isNullOrEmpty() || !inputHtml.contains("<a")) return inputHtml
        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        document.select("a[href]").forEach { link ->
            val decodedLinkText = decodeAnchorPart(link.text())
            if (decodedLinkText.endsWith("...")) {
                val decodedHref = decodeAnchorPart(link.attr("href"))
                val prefix = decodedLinkText.dropLast(3)
                if (decodedHref.startsWith(prefix)) link.text(decodedHref)
            }
        }
        return document.body().html()
    }

    private fun decodeAnchorPart(value: String): String {
        var previousWasSpace = true
        for (character in value) {
            if (character !in ' '..'~' || character == '<' || character == '&' ||
                (character == ' ' && previousWasSpace)
            ) {
                return Ksoup.parse(value).text()
            }
            previousWasSpace = character == ' '
        }
        if (value.isNotEmpty() && previousWasSpace) return Ksoup.parse(value).text()
        return value
    }
}
