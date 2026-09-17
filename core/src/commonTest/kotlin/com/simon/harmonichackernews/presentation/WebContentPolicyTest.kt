package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebContentPolicyTest {
    @Test
    fun validatedHttpUrlAcceptsOnlyAbsoluteWebUrls() {
        assertEquals(
            "https://example.com/article",
            WebContentPolicy.validatedHttpUrl("  https://example.com/article  "),
        )
        assertTrue(
            WebContentPolicy.validatedHttpUrl("HTTP://example.com")
                ?.startsWith("http://", ignoreCase = true) == true,
        )
        assertNull(WebContentPolicy.validatedHttpUrl(null))
        assertNull(WebContentPolicy.validatedHttpUrl(""))
        assertNull(WebContentPolicy.validatedHttpUrl("example.com/article"))
        assertNull(WebContentPolicy.validatedHttpUrl("file:///tmp/article.html"))
        assertNull(WebContentPolicy.validatedHttpUrl("https://"))
        assertNull(WebContentPolicy.validatedHttpUrl("https://exa mple.com/article"))
    }

    @Test
    fun pageTextCommandCapsTheRendererResultBeforeTheNativeBridge() {
        assertTrue(
            WebContentPageText.READ_COMMAND.contains(
                "text.slice(0, ${WebContentPageText.MAX_PAGE_TEXT_CHARS})",
            ),
        )
    }

    @Test
    fun decodedPageTextCannotExceedTheBridgeLimit() {
        val text = "a".repeat(WebContentPageText.MAX_PAGE_TEXT_CHARS + 1)

        assertEquals(
            WebContentPageText.MAX_PAGE_TEXT_CHARS,
            WebContentPageText.decode("\"$text\"").length,
        )
    }

    @Test
    fun unexpectedlyOversizedEncodedPageTextIsRejectedBeforeJsonParsing() {
        val encodedEscape = "\\u0061".repeat(WebContentPageText.MAX_PAGE_TEXT_CHARS + 1)

        assertEquals("", WebContentPageText.decode("\"$encodedEscape\""))
    }
}
