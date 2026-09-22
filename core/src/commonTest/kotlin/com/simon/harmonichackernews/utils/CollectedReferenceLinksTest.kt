package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollectedReferenceLinksTest {
    @Test
    fun numberedLinksBeforeAProseFootnoteAreCollectedInPlace() {
        // HN emits unclosed paragraphs; [3] is an explanatory footnote, not a URL.
        val html = "See the poster [1] and video [2]." +
            "<p>[1] <a href=\"https://example.com/poster\">https://example.com/poster</a>" +
            "<p>[2] <a href=\"https://www.youtube.com/watch?v=0VLAoVGf_74\">Video</a>" +
            "<p>[3] When multiplying A by V, transform each column."
        val result = CollectedReferenceLinks.parse(html)

        assertEquals(listOf("[1]", "[2]"), result.links.map { it.markerLabel })
        assertEquals(listOf(false, true, true, false), result.contentBlocks.map { it.isLink() })
        assertTrue(result.contentBlocks.first().bodyHtml.orEmpty().contains("poster [1]"))
        assertTrue(result.contentBlocks.last().bodyHtml.orEmpty().contains("[3] When multiplying"))
        assertTrue(result.hasInterleavedLinks())
    }

    @Test
    fun numberedLinksEmbeddedInProseRemainUntouched() {
        val html = "Before [1] <a href=\"https://example.com\">Source</a><p>After."
        assertFalse(CollectedReferenceLinks.parse(html).hasLinks())
    }

    @Test
    fun explicitHrefPreservesTerminalPunctuationWhileBareProseStillTrimsIt() {
        for (url in listOf(
            "https://en.wikipedia.org/wiki/Yahoo!",
            "https://example.com/search?q=hello!",
            "https://example.com/end.",
            "https://example.com/empty?",
        )) {
            for (marker in listOf("", "[1] ")) {
                val result = CollectedReferenceLinks.parse("<p>$marker<a href=\"$url\">Source</a></p>")
                assertEquals(url, result.links.single().url)
            }
        }
        assertEquals(
            "https://example.com/path",
            CollectedReferenceLinks.parse("<p>[1] https://example.com/path.</p>").links.single().url,
        )
    }

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
    fun longCommaSeparatedInlineRunWithTrailingProseRemainsInCommentBody() {
        val anchors = (1..64).joinToString(", ") { index ->
            "<a href=\"https://example.com/$index\">Source $index</a>"
        }
        val html = "$anchors all support the same argument."

        val result = CollectedReferenceLinks.parse(html)

        assertFalse(result.hasLinks())
        assertEquals(html, result.bodyHtml)
    }

    @Test
    fun standaloneRunAfterInlineProseStillCollectsAcrossLineAndBlockBoundaries() {
        val inlineHtml =
            "<a href=\"https://example.com/inline\">Inline</a>, " +
                "<a href=\"https://example.com/also-inline\">Also inline</a> remain in prose."
        val standaloneHtml =
            "<a href=\"https://example.com/first\">First</a>, " +
                "<a href=\"https://example.com/second\">Second</a>"
        for (boundary in listOf("<br>", "<p>References.</p>")) {
            val result = CollectedReferenceLinks.parse(
                "$inlineHtml$boundary$standaloneHtml<p>After.</p>",
            )

            assertEquals(
                listOf("https://example.com/first", "https://example.com/second"),
                result.links.map { it.url },
            )
            assertEquals("$inlineHtml$boundary<p>After.</p>", result.bodyHtml)
            assertEquals(2, result.contentBlocks.count { it.isLink() })
            assertTrue(result.hasInterleavedLinks())
        }
    }

    @Test
    fun commaSeparatedTopLevelAnchorsAreCollectedAsSeparateLinks() {
        val html =
            "<a href=\"https:&#x2F;&#x2F;twitter.com&#x2F;cdngdev&#x2F;status&#x2F;2091909073038082139\" " +
                "rel=\"nofollow\">https:&#x2F;&#x2F;twitter.com&#x2F;cdngdev&#x2F;status&#x2F;" +
                "2091909073038082139</a>, <a href=\"https:&#x2F;&#x2F;xcancel.com&#x2F;cdngdev&#x2F;" +
                "status&#x2F;2091909073038082139\" rel=\"nofollow\">https:&#x2F;&#x2F;xcancel.com&#x2F;" +
                "cdngdev&#x2F;status&#x2F;2091909073038082139</a>"

        val result = CollectedReferenceLinks.parse(html)

        assertEquals(
            listOf(
                "https://twitter.com/cdngdev/status/2091909073038082139",
                "https://xcancel.com/cdngdev/status/2091909073038082139",
            ),
            result.links.map { it.url },
        )
        assertEquals("", result.bodyHtml)
        assertEquals(2, result.contentBlocks.count { it.isLink() })
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
