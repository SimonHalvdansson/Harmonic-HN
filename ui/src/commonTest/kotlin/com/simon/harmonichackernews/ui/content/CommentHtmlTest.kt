package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.em
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.network.LinkPreviewParsers
import com.simon.harmonichackernews.network.StoryTextProcessor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentHtmlTest {
    @Test
    fun overlappingQuoteSeparatorsPreserveTextAndFormattingWhenTogglingHighlighting() {
        for (breaks in 2..6) {
            for (highlight in listOf(true, false)) {
                val html = "&gt; <b>First</b>" + "<br>".repeat(breaks) +
                    "&gt; <a href='https://example.com'>Second</a>"
                val source = if (highlight) prepareCommentHtml(html) else
                    prepareCommentHtml(html, false).withHighlightedQuotedLines(removeMarkers = false)
                val segments = source.quoteSpacedSegments()
                assertEquals(source.text, segments.joinToString("\n\n") { it.text })
                assertEquals("First", segments.first().spanStyles.single().let {
                    segments.first().text.substring(it.start, it.end)
                })
                val last = segments.last()
                assertEquals("Second", last.getStringAnnotations(0, last.length)
                    .single { it.item == "https://example.com" }.let { last.text.substring(it.start, it.end) })
                assertTrue(segments.first().getStringAnnotations(COMMENT_QUOTE_TAG, 0, segments.first().length).isNotEmpty())
                assertTrue(last.getStringAnnotations(COMMENT_QUOTE_TAG, 0, last.length).isNotEmpty())
            }
        }
    }

    @Test
    fun paragraphAndLineBreakBetweenQuotesDoNotCreateABackwardsTextRange() {
        val source = prepareCommentHtml("&gt; First<p><br>&gt; Second")
        assertEquals("First\n\n\nSecond", source.text)
        assertEquals(listOf("First", "\nSecond"), source.quoteSpacedSegments().map { it.text })
    }

    @Test
    fun quoteSpacingSegmentsPreserveOrdinaryParagraphGapsAndLinks() {
        val source = prepareCommentHtml("Intro<p>&gt; <b>Quoted</b> <a href='https://example.com'>link</a><p>Reply<p>Ordinary paragraph")
        val segments = source.quoteSpacedSegments()
        assertEquals(listOf("Intro", "Quoted link", "Reply\n\nOrdinary paragraph"), segments.map { it.text })
        assertEquals(source.text, segments.joinToString("\n\n") { it.text })
        val compact = segments[1]
        val quote = compact.getStringAnnotations(COMMENT_QUOTE_TAG, 0, compact.length).single()
        assertEquals("Quoted link", compact.text.substring(quote.start, quote.end))
        assertTrue(compact.spanStyles.any { compact.text.substring(it.start, it.end) == "Quoted" })
        assertTrue(compact.getStringAnnotations(0, compact.length).any {
            it.item == "https://example.com" && compact.text.substring(it.start, it.end) == "link"
        })
        assertTrue(compact.paragraphStyles.isEmpty())
    }

    @Test
    fun disabledQuoteDecorationRetainsRangesForFadeOutWithoutRemovingMarkers() {
        val source = prepareCommentHtml("&gt; Quote<p>Reply<pre><code>&gt; code</code></pre>", false)
        val annotated = source.withHighlightedQuotedLines(removeMarkers = false)
        assertEquals(source.text, annotated.text)
        val quote = annotated.getStringAnnotations(COMMENT_QUOTE_TAG, 0, annotated.length).single()
        assertEquals("> Quote", annotated.text.substring(quote.start, quote.end))
    }

    @Test
    fun quoteToggleRestoresMarkersAndKeepsLinkOffsetsAcrossCachedModes() {
        val html = "&gt;&gt; <b>Quoted</b> <a href='https://example.com'>link</a><p>Reply"
        val listener = LinkInteractionListener { }
        val enabled = htmlAnnotatedString(html, Color.Blue, listener, highlightQuotes = true)
        val disabled = htmlAnnotatedString(html, Color.Blue, listener, highlightQuotes = false)
        assertEquals("Quoted link\n\nReply", enabled.text)
        assertEquals(">> Quoted link\n\nReply", disabled.text)
        assertTrue(disabled.getStringAnnotations(COMMENT_QUOTE_TAG, 0, disabled.length).isEmpty())
        val link = disabled.getLinkAnnotations(0, disabled.length).single()
        assertEquals("link", disabled.text.substring(link.start, link.end))
        assertEquals(enabled, htmlAnnotatedString(html, Color.Blue, listener, highlightQuotes = true))
        assertEquals(disabled, htmlAnnotatedString(html, Color.Blue, listener, highlightQuotes = false))
    }

    @Test
    fun quotedParagraphsAreMarkedWithoutLosingLinksOrBoldText() {
        val html = "Lead<p>&gt; quoted <b>bold</b> and <a href='https://example.com'>linked</a><p>After"
        val prepared = prepareCommentHtml(html)
        assertEquals("Lead\n\nquoted bold and linked\n\nAfter", prepared.text)
        val quotes = prepared.getStringAnnotations(COMMENT_QUOTE_TAG, 0, prepared.length)
        assertEquals(1, quotes.size)
        assertEquals("quoted bold and linked", prepared.text.substring(quotes[0].start, quotes[0].end))
        // ParagraphStyle itself produces implicit line boundaries in Compose, adding an
        // extra visual line beneath the quote even when the text has the right newlines.
        assertTrue(prepared.paragraphStyles.isEmpty())
        assertTrue(prepared.spanStyles.any { prepared.text.substring(it.start, it.end) == "bold" })
        val rendered = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener { })
        val link = rendered.getLinkAnnotations(0, rendered.length).single()
        assertEquals("linked", rendered.text.substring(link.start, link.end))
    }

    @Test
    fun consecutiveQuoteMarkersAreRemovedButCodeQuotesRemainUntouched() {
        val rendered = prepareCommentHtml("&gt;&gt; First<br>&gt; Second<p>Normal<pre><code>&gt; code</code></pre>")
        assertEquals("First\nSecond\n\nNormal\n\n> code", rendered.text)
        assertEquals(2, rendered.getStringAnnotations(COMMENT_QUOTE_TAG, 0, rendered.length).size)
        assertTrue(rendered.paragraphStyles.isEmpty())
    }

    @Test
    fun quoteAtEndDoesNotIntroduceTrailingWhitespaceOrEmptyParagraphs() {
        val rendered = prepareCommentHtml("Before<p>&gt; Last quote")
        assertEquals("Before\n\nLast quote", rendered.text)
        assertTrue(rendered.paragraphStyles.isEmpty())
    }

    @Test
    fun quotesDoNotChangeExistingParagraphGaps() {
        val quoted = prepareCommentHtml(
            "&gt; a peanut shortage prompted Delta, then America’s fourth-largest airline, " +
                "to seek an alternative onboard snack<p>I never knew this was the reason."
        )
        val plain = prepareCommentHtml(
            "a peanut shortage prompted Delta, then America’s fourth-largest airline, " +
                "to seek an alternative onboard snack<p>I never knew this was the reason."
        )
        assertEquals(plain.text, quoted.text)
        assertTrue("snack\n\nI never" in quoted.text)
        assertTrue(quoted.paragraphStyles.isEmpty())
        assertEquals(1, quoted.getStringAnnotations(COMMENT_QUOTE_TAG, 0, quoted.length).size)
    }

    @Test
    fun quotedLineWithSingleBreakKeepsExactlyOneBreak() {
        val quoted = prepareCommentHtml("&gt; Quoted text<br>Reply")
        val plain = prepareCommentHtml("Quoted text<br>Reply")
        assertEquals("Quoted text\nReply", quoted.text)
        assertEquals(plain.text, quoted.text)
        assertTrue(quoted.paragraphStyles.isEmpty())
    }

    @Test
    fun linkFreeTextReusesCachedStylesAcrossThemeAndListenerChanges() {
        val html = "<p>A <b>bold</b> comment.<p>With <code>code</code>."
        val first = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener { error("No links") })
        val second = htmlAnnotatedString(html, Color.Red, LinkInteractionListener { error("No links") })

        assertEquals(prepareCommentHtml(html), first)
        assertSame(first, second)
        assertTrue(first.spanStyles.isNotEmpty())
    }

    @Test
    fun wikipediaSummaryPreservesParagraphsAndExplicitLineBreaks() {
        val info = requireNotNull(
            LinkPreviewParsers.parseWikipedia(
                """{"query":{"pages":{"123":{"title":"Example","extract":"<p>First <b>paragraph</b> &amp; text.</p><p>Second paragraph.<br>Next line.<br /><br />After blank line.</p>"}}}}""",
            ),
        )

        assertEquals(
            "First paragraph & text.\n\nSecond paragraph.\nNext line.\n\nAfter blank line.",
            prepareCommentHtml(info.summary.orEmpty()).text,
        )
    }

    @Test
    fun manyInlineNodesPreserveTextAndFormattingRanges() {
        val rendered = prepareCommentHtml("<b>word</b> plain <i>text</i> ".repeat(1_000))

        assertEquals("word plain text ".repeat(1_000).trimEnd(), rendered.text)
        assertEquals(2_000, rendered.spanStyles.size)
        assertTrue(rendered.spanStyles.all {
            rendered.text.substring(it.start, it.end) in setOf("word", "text")
        })
    }

    @Test
    fun emptyInlineNodesPreserveParagraphAndCodeBoundaries() {
        val rendered = prepareCommentHtml(
            "Before<br><span> </span><br><b> </b><pre><code>  line\n</code></pre>" +
                "<span> </span><i> After</i>",
        )

        assertEquals("Before\n\n  line\n\nAfter", rendered.text)
    }

    @Test
    fun codeBoundaryPreservesThreeAuthoredLineBreaks() {
        val rendered = prepareCommentHtml("Before<pre><code>line\n\n\n</code></pre>After")

        assertEquals("Before\n\nline\n\n\nAfter", rendered.text)
    }

    @Test
    fun codeBlocksSeparateProseAndRetainIndentation() {
        val rendered = prepareCommentHtml("Before<pre><code>  one\n    two</code></pre>After")
        assertEquals("Before\n\n  one\n    two\n\nAfter", rendered.text)
        assertTrue(rendered.spanStyles.any {
            it.item.fontFamily == FontFamily.Monospace &&
                rendered.text.substring(it.start, it.end) == "  one\n    two"
        })
    }

    @Test
    fun convertedCodeRetainsFormattingInThreadAndDialogContent() {
        val raw = "<a href='https://example.com/full'>https://example.com/...</a>" +
            "<p>This should work:<p><pre><code>  command\n    option\n</code></pre>\n" +
            "Then open:<p><pre><code>  another command\n</code></pre>\nThat's running."
        val processed = StoryTextProcessor.preprocessHtml(raw)!!
        val expanded = Comment().apply { text = processed }.expandedAnchorText!!
        for (html in listOf(processed, expanded)) {
            val rendered = prepareCommentHtml(html)
            val text = rendered.text
            assertEquals(
                "This should work:\n\n  command\n    option\n\nThen open:\n\n  another command\n\nThat's running.",
                text.substringAfter("\n\n"),
            )
            assertEquals(2, rendered.spanStyles.count { it.item.fontFamily == FontFamily.Monospace })
            assertEquals(2, rendered.spanStyles.count { it.item.fontSize == 0.8.em })
        }
    }

    @Test
    fun convertedCodeRestoresBreakableSpacesAndPreservesAuthoredWhitespace() {
        val code = "    Set up Clop to optimise any PNG that I drop in my website assets folder\n\n" +
            "    Get Crank to start Time Machine backups immediately when I connect my HDD\n" +
            "    aligned  columns    stay\n"
        val raw = "<pre><code>$code</code></pre>"
        val processed = StoryTextProcessor.preprocessHtml(raw)!!
        val expanded = Comment().apply { text = processed }.expandedAnchorText!!

        for (html in listOf(raw, processed, expanded)) {
            val rendered = prepareCommentHtml(html)
            assertEquals(code, rendered.text)
            assertTrue(rendered.spanStyles.any {
                it.item.fontFamily == FontFamily.Monospace &&
                    rendered.text.substring(it.start, it.end) == code
            })
        }
    }

    @Test
    fun proseKeepsIntentionalNonBreakingSpaces() {
        assertEquals("10\u00a0km", prepareCommentHtml("10&nbsp;km").text)
    }

    @Test
    fun formattedProseCollapsesHtmlWhitespaceWithoutChangingNonbreakingSpaces() {
        val text = prepareCommentHtml("<i>one \t\r\n\u000c two</i> <b>three&nbsp; four</b>")
        assertEquals("one two three\u00a0 four", text.text)
        assertEquals(listOf("one two", "three\u00a0 four"), text.spanStyles.map {
            text.text.substring(it.start, it.end)
        })
    }

    @Test
    fun codeBlockMarginsReuseFollowingParagraphBreaksAndKeepInternalBlankLines() {
        for (ending in listOf("After", "\nAfter", "<p>After", "<br><br>After")) {
            val rendered = prepareCommentHtml("Before<p><pre><code>  one\n\n    two\n</code></pre>$ending")
            assertEquals("Before\n\n  one\n\n    two\n\nAfter", rendered.text)
        }
    }

    @Test
    fun codeAtTheDocumentEdgesIsNotWhitespaceTrimmed() {
        val rendered = prepareCommentHtml("<pre><code>  if (a &lt; b) {\n    run();\n  }\n</code></pre>")
        assertEquals("  if (a < b) {\n    run();\n  }\n", rendered.text)
    }

    @Test
    fun inlineCodeUsesMonospaceWithoutAddingBlockBreaks() {
        val rendered = prepareCommentHtml("Use <code>a  b</code> here")
        assertEquals("Use a  b here", rendered.text)
        val code = rendered.spanStyles.single { it.item.fontFamily == FontFamily.Monospace }
        assertEquals("a  b", rendered.text.substring(code.start, code.end))
    }

    @Test
    fun preparedTextPreservesFormattingAndUsesEachScreensLinkStyleAndListener() {
        val html = "  <b>Bold <i>nested</i></b><br><a href='https://example.com'>Link</a><script>hidden</script>  "
        val prepared = prepareCommentHtml(html)
        assertEquals("Bold nested\nLink", prepared.text)
        assertEquals(2, prepared.spanStyles.size)
        assertTrue(prepared.getLinkAnnotations(0, prepared.length).isEmpty())
        CommentHtmlTextCache.install(html, PreparedCommentHtml(prepared))
        var firstClicks = 0
        var secondClicks = 0
        val first = htmlAnnotatedString(html, Color.Red, LinkInteractionListener { firstClicks++ })
        val second = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener { secondClicks++ })
        val firstLink = first.getLinkAnnotations(0, first.length).single()
        val secondLink = second.getLinkAnnotations(0, second.length).single()
        assertEquals("Link", second.text.substring(secondLink.start, secondLink.end))
        assertEquals(Color.Red, firstLink.item.styles?.style?.color)
        assertEquals(Color.Blue, secondLink.item.styles?.style?.color)
        secondLink.item.linkInteractionListener?.onClick(secondLink.item)
        assertEquals(0, firstClicks)
        assertEquals(1, secondClicks)
    }

    @Test
    fun formattedParagraphsUseOneLegacyBlankLine() {
        val rendered = htmlAnnotatedString(
            html = "First\n<p>Second</p>\n<p>Third</p>",
            linkColor = Color.Blue,
            linkListener = LinkInteractionListener { },
        )

        assertEquals("First\n\nSecond\n\nThird", rendered.text)
    }
}
