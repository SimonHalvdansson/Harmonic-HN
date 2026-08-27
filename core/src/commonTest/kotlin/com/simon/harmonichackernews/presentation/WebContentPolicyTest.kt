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
}
