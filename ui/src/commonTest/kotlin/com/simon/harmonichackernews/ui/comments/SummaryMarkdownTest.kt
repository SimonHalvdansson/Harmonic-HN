package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryMarkdownTest {
    @Test
    fun rendersBulletsAndInlineEmphasisWithoutMarkdownDelimiters() {
        val rendered = summaryMarkdownAnnotatedString(
            "- **First** item\n* Second with `code` and _emphasis_",
        )

        assertEquals("• First item\n• Second with code and emphasis", rendered.text)
        assertTrue(rendered.paragraphStyles.isEmpty())
        assertTrue(rendered.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && rendered.text.substring(it.start, it.end) == "First"
        })
        assertTrue(rendered.spanStyles.any {
            it.item.fontFamily == FontFamily.Monospace &&
                rendered.text.substring(it.start, it.end) == "code"
        })
        assertTrue(rendered.spanStyles.any {
            it.item.fontStyle == FontStyle.Italic &&
                rendered.text.substring(it.start, it.end) == "emphasis"
        })
    }

    @Test
    fun rendersNumberedItemsHeadingsQuotesAndLinks() {
        val rendered = summaryMarkdownAnnotatedString(
            "## Key points\n1. [First](https://example.com)\n> Context",
        )

        assertEquals("Key points\n1. First\n› Context", rendered.text)
    }

    @Test
    fun removesBlankLinesBetweenListItemsButPreservesParagraphSpacing() {
        val rendered = summaryMarkdownAnnotatedString(
            "- First\n\n- Second\n\n\n3. Third\n\nFollowing paragraph",
        )

        assertEquals(
            "• First\n• Second\n3. Third\n\nFollowing paragraph",
            rendered.text,
        )
    }

    @Test
    fun removesBlankLinesFromGeminiNanoLiteralBulletOutput() {
        val rendered = summaryMarkdownAnnotatedString(
            "• First Gemini bullet\n\n• Second Gemini bullet\n\n• Third Gemini bullet",
        )

        assertEquals(
            "• First Gemini bullet\n• Second Gemini bullet\n• Third Gemini bullet",
            rendered.text,
        )
    }

    @Test
    fun preservesIncompleteMarkdownWhileAResultIsStreaming() {
        val rendered = summaryMarkdownAnnotatedString("- **Partially generated")

        assertEquals("• **Partially generated", rendered.text)
    }
}
