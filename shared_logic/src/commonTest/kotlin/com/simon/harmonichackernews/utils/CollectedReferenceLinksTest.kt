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

    @Test
    fun inlineAnchorsInProseRemainInCommentBody() {
        val html =
            "<a href=\"https:&#x2F;&#x2F;felonybench.org&#x2F;\" rel=\"nofollow\">" +
                "https:&#x2F;&#x2F;felonybench.org&#x2F;</a> and " +
                "<a href=\"https:&#x2F;&#x2F;felonybench.com&#x2F;\" rel=\"nofollow\">" +
                "https:&#x2F;&#x2F;felonybench.com&#x2F;</a> seem unrelated?<p>" +
                "One&#x27;s hosted on porkbun and one&#x27;s hosted on namecheap."

        val result = CollectedReferenceLinks.parse(html)

        assertFalse(result.hasLinks())
        assertEquals(html, result.bodyHtml)
    }

    @Test
    fun standaloneTopLevelAnchorStillCollectsWhenBoundedByBlocks() {
        val result = CollectedReferenceLinks.parse(
            "<p>Discussion.</p><a href=\"https://example.com/source\">Source</a><p>After.</p>",
        )

        assertTrue(result.hasLinks())
        assertEquals("https://example.com/source", result.links.single().url)
        assertTrue(result.hasInterleavedLinks())
    }

    @Test
    fun numberedFragmentWithTrailingProseIsNotCollected() {
        val html =
            "<p>Discussion.</p><p>[1] <a href=\"https://example.com/source\">Source</a> commentary</p>"

        val result = CollectedReferenceLinks.parse(html)

        assertFalse(result.hasLinks())
        assertEquals(html, result.bodyHtml)
    }

    @Test
    fun dottedIdentifierWithUncommonTldIsNotCollectedAsBareDomain() {
        val html = "<p>browser.ml.enable</p>"

        val result = CollectedReferenceLinks.parse(html)

        assertFalse(result.hasLinks())
        assertEquals(html, result.bodyHtml)
    }

    @Test
    fun commonLongBareDomainTldsAreCollected() {
        val result = CollectedReferenceLinks.parse(
            "<p>[1] example.online/source</p><p>[2] example.store/source</p>",
        )

        assertEquals(
            listOf("https://example.online/source", "https://example.store/source"),
            result.links.map { it.url },
        )
    }
}
