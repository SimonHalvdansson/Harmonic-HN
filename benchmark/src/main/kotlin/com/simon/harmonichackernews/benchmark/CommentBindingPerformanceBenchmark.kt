@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.content.SearchHighlightTransition
import com.simon.harmonichackernews.ui.content.bindCommentLinks
import com.simon.harmonichackernews.ui.content.prepareCommentHtml
import com.simon.harmonichackernews.ui.content.trimmedCommentText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One production operation per iteration; parsing, fixtures and assertions are outside timing. */
@RunWith(AndroidJUnit4::class)
class CommentBindingPerformanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null
    private val listener = LinkInteractionListener { }

    @Test fun bindOneLink() = measureBinding(1)
    @Test fun bindTwelveLinks() = measureBinding(12)
    @Test fun trimTwelveSpans() = measureTrimming(12)
    @Test fun trimOneHundredTwentySpans() = measureTrimming(120)
    @Test fun inactiveSearchPlainComment() = measureInactiveSearch(0)
    @Test fun inactiveSearchFormattedComment() = measureInactiveSearch(64)

    private fun measureBinding(linkCount: Int) {
        val body = prepareCommentHtml(buildString {
            append("References supporting this comment: ")
            repeat(linkCount) { index ->
                append("<a href='https://example.com/$index'>source $index</a> ")
            }
        })
        val bound = bindCommentLinks(body, Color.Blue, listener)
        check(bound.text == body.text)
        val links = bound.getLinkAnnotations(0, bound.length)
        check(links.size == linkCount)
        links.forEachIndexed { index, range ->
            val link = range.item as LinkAnnotation.Url
            check(link.url == "https://example.com/$index")
            check(bound.text.substring(range.start, range.end) == "source $index")
            check(link.styles?.style?.color == Color.Blue)
            check(link.styles?.style?.textDecoration == TextDecoration.Underline)
            check(link.linkInteractionListener === listener)
        }
        benchmarkRule.measureRepeated { result = bindCommentLinks(body, Color.Blue, listener) }
    }

    private fun measureTrimming(spanCount: Int) {
        val body = buildAnnotatedString {
            append("  ")
            repeat(spanCount) { index ->
                val start = length
                append("formatted word $index ")
                addStyle(
                    if (index % 3 == 0) SpanStyle(fontFamily = FontFamily.Monospace)
                    else SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    length,
                )
            }
            append("\n  ")
        }
        val expected = body.subSequence(2, body.length - 4)
        check(body.trimmedCommentText() == expected)
        benchmarkRule.measureRepeated { result = body.trimmedCommentText() }
    }

    private fun measureInactiveSearch(spanCount: Int) {
        val body = buildAnnotatedString {
            append("A comment with formatting and no active search. ".repeat(64))
            repeat(spanCount) { index ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), index * 40, index * 40 + 12)
            }
        }
        val transition = SearchHighlightTransition()
        check(transition.retarget(body, "", 1f).runs.isEmpty())
        benchmarkRule.measureRepeated { result = transition.retarget(body, "", 1f) }
    }
}
