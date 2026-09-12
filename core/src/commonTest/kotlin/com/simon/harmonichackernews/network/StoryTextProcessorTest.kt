package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class StoryTextProcessorTest {
    @Test
    fun preservesExistingAnchorsWhileLinkifyingSurroundingUrls() {
        val anchor = """<A href="https://example.com/existing">https://example.com/label</A>"""
        assertEquals(
            """<a href="http://before.example/path">http://before.example/path</a> $anchor <a href="https://after.example/path">https://after.example/path</a>.""",
            StoryTextProcessor.preprocessHtml("http://before.example/path $anchor https://after.example/path."),
        )
        assertEquals(anchor, StoryTextProcessor.preprocessHtml(anchor))
    }

    @Test
    fun textContainingOnlySchemePrefixesStaysUnchanged() {
        val text = "Discuss http, https, httpx://example.com and httpsomething://example.com."
        assertEquals(text, StoryTextProcessor.preprocessHtml(text))
    }

    @Test
    fun preservesCodeWhitespaceAndHandlesStandaloneClosingPreTag() {
        val expected = "<div><tt><small>&nbsp;a<br>b&nbsp;</small></tt></div>"
        assertEquals(expected, StoryTextProcessor.preprocessHtml("<code> a\nb </code>"))
        assertEquals(expected, StoryTextProcessor.preprocessHtml("<pre><code> a\nb </code></pre>"))
        assertEquals("stray </tt></div>", StoryTextProcessor.preprocessHtml("stray </pre>"))
    }

    @Test
    fun linksBareUrlsAndPreservesAnchorsAcrossSegmentBoundaries() {
        val fragments = listOf(
            "" to "",
            "ordinary Unicode text: æøå 😀" to "ordinary Unicode text: æøå 😀",
            "http" to "http",
            "HTTPS://example.com" to "HTTPS://example.com",
            "https://example.com" to "<a href=\"https://example.com\">https://example.com</a>",
            "http://example.com/path)." to
                "<a href=\"http://example.com/path\">http://example.com/path</a>).",
            "https:&#x2F;&#47;example.com/a" to
                "<a href=\"https://example.com/a\">https://example.com/a</a>",
            "<a href=\"https://example.com\">existing</a>" to
                "<a href=\"https://example.com\">existing</a>",
            "<A HREF=\"https://example.com\">existing\nhttps://label.example.com</A>" to
                "<A HREF=\"https://example.com\">existing\nhttps://label.example.com</A>",
            "<abbr>https://example.com</abbr>" to
                "<abbr><a href=\"https://example.com\">https://example.com</a></abbr>",
            "<pre><code> https://example.com </code></pre>" to
                "<div><tt><small>&nbsp;<a&nbsp;href=\"https://example.com\">https://example.com</a>&nbsp;</small></tt></div>",
        )
        for ((left, expectedLeft) in fragments) {
            assertEquals(expectedLeft, StoryTextProcessor.preprocessHtml(left), left)
            for ((right, expectedRight) in fragments) {
                val input = "$left | $right"
                assertEquals("$expectedLeft | $expectedRight", StoryTextProcessor.preprocessHtml(input), input)
            }
        }
    }
}
