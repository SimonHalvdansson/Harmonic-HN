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
}
