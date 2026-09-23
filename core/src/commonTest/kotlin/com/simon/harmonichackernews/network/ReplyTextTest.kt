package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyTextTest {
    @Test
    fun emptyRepliesUseTheExistingFallback() {
        for (html in listOf(null, "", " \t\n", "<p> </p>")) {
            assertEquals("Tap to view the reply.", ReplyText.plainReplyText(html))
        }
    }

    @Test
    fun htmlEntitiesAndWhitespaceProducePlainText() {
        assertEquals(
            "Hello & thanks for the link!",
            ReplyText.plainReplyText("<p>Hello &amp;\t thanks</p><p>for  the <a href='https://example.com'>link</a>!</p>"),
        )
    }

    @Test
    fun truncationRetainsTheExistingBoundaryAndEllipsis() {
        assertEquals("a".repeat(240), ReplyText.plainReplyText("a".repeat(240)))
        assertEquals("a".repeat(237) + "...", ReplyText.plainReplyText("a".repeat(241)))
    }
}
