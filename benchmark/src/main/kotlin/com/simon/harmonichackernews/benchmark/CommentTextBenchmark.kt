package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Comment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One comment per iteration; fixtures are constructed outside measurement. */
@RunWith(AndroidJUnit4::class)
class CommentTextBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    @Volatile private var result: String? = null

    @Test fun anchorExpansion() = measureAnchors()

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

    private val anchors = listOf(
        "<p>See <a href='https://example.com/article'>https://example.com/...</a> for the details.</p>",
        "A <a href='https://example.com/first'>named link</a> and " +
            "<a href='https://example.org/second'>https://example.org/...</a>.",
        "<p><a href='https://example.com/?a=1&amp;b=2'>https://example.com/...</a></p>",
        "<a>Missing destination</a> <a href='https://other.example'>A full title</a>",
    )
}
