package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectedReferenceLinksTest {
    @Test
    fun plainCommentUsesEmptyResultWithoutChangingBody() {
        val html = "<p>A comment without a link.</p>"

        val result = CollectedReferenceLinks.parse(html)

        assertFalse(result.hasLinks())
        assertEquals(html, result.bodyHtml)
    }

    @Test
    fun anchorStillUsesFullReferenceExtraction() {
        val result = CollectedReferenceLinks.parse(
            "<p>Discussion.</p><p>[1] <a href=\"https://example.com/source\">Source</a></p>",
        )

        assertTrue(result.hasLinks())
        assertEquals("https://example.com/source", result.links.single().url)
    }

    @Test
    fun bareDomainStillUsesFullReferenceExtraction() {
        val result = CollectedReferenceLinks.parse("<p>[1] example.com/source</p>")

        assertTrue(result.hasLinks())
        assertEquals("https://example.com/source", result.links.single().url)
    }
}
