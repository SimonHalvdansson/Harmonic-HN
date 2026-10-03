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
        CommentHtmlTextCache.install(html, prepared)
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
