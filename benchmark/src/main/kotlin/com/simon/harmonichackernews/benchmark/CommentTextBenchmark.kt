package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.presentation.CommentTextPolicy
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One comment per iteration; fixtures are constructed outside measurement. */
@RunWith(AndroidJUnit4::class)
class CommentTextBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Volatile private var result: String? = null

    @Test fun spacingPlain() = measureSpacing(plain)
    @Test fun spacingParagraphs() = measureSpacing(paragraphs)
    @Test fun spacingMixed() = measureSpacing(mixed)
    @Test fun spacingLongInline() = measureSpacing(longInline)
    @Test fun anchorExpansion() = measureAnchors()

    private fun measureSpacing(samples: List<String>) {
        var index = 0
        benchmarkRule.measureRepeated {
            val html = samples[index]
            index = (index + 1) % samples.size
            result = CommentTextPolicy.preserveLegacyParagraphSpacing(html)
        }
    }

    private fun measureAnchors() {
        val comment = Comment()
        var index = 0
        benchmarkRule.measureRepeated {
            // Rotate distinct sources so the existing per-comment cache never skips expansion.
            val html = anchors[index]
            index = (index + 1) % anchors.size
            comment.text = html
            result = comment.expandedAnchorText
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
