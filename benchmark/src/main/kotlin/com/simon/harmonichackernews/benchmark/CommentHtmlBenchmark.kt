package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkInteractionListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises first-render work; rotating 512 sources exceeds the prepared-text LRU capacity. */
@RunWith(AndroidJUnit4::class)
class CommentHtmlBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: AnnotatedString? = null
    private val listener = LinkInteractionListener { }

    @Test fun ordinaryComment() = measure(paragraphs = 3)
    @Test fun formattingHeavyComment() = measure(paragraphs = 40)

    private fun measure(paragraphs: Int) {
        val samples = List(512) { sample ->
            buildString {
                append("Comment $sample. ")
                repeat(paragraphs) { paragraph ->
                    append("<p>Paragraph $paragraph: a <i>small clarification</i> with ")
                    append("<b>supporting evidence</b> and ")
                    append("<a href='https://example.com/$paragraph'>a reference</a>. ")
                    append("Consider the implementation and its tradeoffs.</p>")
                }
                append("<pre><code>  example()\n  result</code></pre><p>Follow-up.</p>")
            }
        }
        var index = 0
        benchmarkRule.measureRepeated {
            result = htmlAnnotatedString(samples[index], Color.Blue, listener)
            index = (index + 1) % samples.size
        }
        check(requireNotNull(result).text.endsWith("Follow-up."))
    }
}
