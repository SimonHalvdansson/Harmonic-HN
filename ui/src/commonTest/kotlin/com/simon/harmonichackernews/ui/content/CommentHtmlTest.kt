package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentHtmlTest {
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
