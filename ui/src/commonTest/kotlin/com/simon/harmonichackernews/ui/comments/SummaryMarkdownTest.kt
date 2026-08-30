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

    @Test
    fun listRenderingDoesNotAddParagraphSpacingStyles() {
        val rendered = summaryMarkdownAnnotatedString(
            "- A long first item\n- A long second item\nFollowing paragraph",
        )

        assertTrue(rendered.paragraphStyles.isEmpty())
    }

    @Test
    fun compactsHeadingSpacingAndRepeatedBlankLines() {
        val rendered = summaryMarkdownAnnotatedString(
            "### Heading\n\nNext line\n\n\nFinal paragraph",
        )

        assertEquals("Heading\nNext line\n\nFinal paragraph", rendered.text)
    }

    @Test
    fun rendersGithubTasksFencesAlertsAndHtmlWithoutRawDelimiters() {
        val rendered = summaryMarkdownAnnotatedString(
            """
                - [x] Reviewed
                - [ ] Pending
                ```text
                code()
                ```
                > [!NOTE]
                <!-- hidden -->
                <details><summary>More</summary></details>
            """.trimIndent(),
        )

        assertEquals("☑ Reviewed\n☐ Pending\ncode()\nNote\n\nMore", rendered.text)
        assertTrue(rendered.spanStyles.any {
            it.item.fontFamily == FontFamily.Monospace &&
                rendered.text.substring(it.start, it.end) == "code()"
        })
    }

    @Test
    fun separatesListMarkersFromContentForWrappedLineLayout() {
        val items = summaryMarkdownListItems(
            "- A first item long enough to wrap\n\n10. A numbered item\n- [x] A task",
        )

        assertEquals(
            listOf(
                SummaryMarkdownListItem("• ", "A first item long enough to wrap"),
                SummaryMarkdownListItem("10. ", "A numbered item"),
                SummaryMarkdownListItem("☑ ", "A task"),
            ),
            items,
        )
    }

    @Test
    fun listLayoutRejectsMixedParagraphs() {
        assertEquals(null, summaryMarkdownListItems("- A list item\nFollowing paragraph"))
    }

    @Test
    fun streamingFadeTargetsOnlyNewlyRenderedTextIncludingFinalChunk() {
        assertEquals(
            13..18,
            streamingTextFadeRange(
                previous = "Existing text",
                current = "Existing text grows",
                streaming = true,
                wasStreaming = true,
            ),
        )
        assertEquals(
            19..19,
            streamingTextFadeRange(
                previous = "Existing text grows",
                current = "Existing text grows!",
                streaming = false,
                wasStreaming = true,
            ),
        )
        assertEquals(
            null,
            streamingTextFadeRange(
                previous = "Cached",
                current = "Cached summary",
                streaming = false,
                wasStreaming = false,
            ),
        )
    }
}
