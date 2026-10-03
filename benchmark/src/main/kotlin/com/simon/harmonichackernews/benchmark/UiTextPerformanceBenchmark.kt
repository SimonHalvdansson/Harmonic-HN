@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.comments.releaseMarkdownBlocks
import com.simon.harmonichackernews.ui.comments.summaryMarkdownAnnotatedString
import com.simon.harmonichackernews.ui.content.highlightSearchMatches
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cached comment binding and Markdown parsing, including unchanged linked-content controls. */
@RunWith(AndroidJUnit4::class)
class UiTextPerformanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null
    private val listener = LinkInteractionListener { }
    private val comment = "<p>A <b>bold</b> point with <i>emphasis</i> and more text.".repeat(12)
    private val pageUrl = "https://github.com/owner/repo/releases/tag/v1"

    @Test fun cachedCommentWithoutLinks() = measureCachedComment(comment)

    @Test fun cachedCommentWithLink() = measureCachedComment(
        comment + "<p><a href='https://example.com'>Details</a>",
    )

    private fun measureCachedComment(html: String) {
        result = htmlAnnotatedString(html, Color.Blue, listener)
        benchmarkRule.measureRepeated {
            result = htmlAnnotatedString(html, Color.Blue, listener)
        }
    }

    @Test fun searchWithoutMatches() {
        val body = htmlAnnotatedString(comment, Color.Blue, listener)
        benchmarkRule.measureRepeated {
            result = highlightSearchMatches(body, "absent", Color.Yellow)
        }
    }

    @Test fun summaryPlainText() {
        val markdown = "The new release improves rendering and keeps all existing controls available. ".repeat(12)
        benchmarkRule.measureRepeated {
            result = summaryMarkdownAnnotatedString(markdown)
        }
    }

    @Test fun summaryWithSixteenLinks() {
        val markdown = "[Details](../details) and **[issue](/owner/repo/issues/1)**\n".repeat(8)
        benchmarkRule.measureRepeated {
            result = summaryMarkdownAnnotatedString(markdown, baseUrl = pageUrl)
        }
    }

    @Test fun releaseWithUnclosedAnchors() {
        val markdown = "<a href='https://example.com'>Description\n".repeat(128)
        benchmarkRule.measureRepeated {
            result = releaseMarkdownBlocks(markdown, pageUrl)
        }
    }
}
