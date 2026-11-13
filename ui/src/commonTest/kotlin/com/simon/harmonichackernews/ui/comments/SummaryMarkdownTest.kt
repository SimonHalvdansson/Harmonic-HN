package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SummaryMarkdownTest {
    @Test
    fun plainTextRunsPreserveEveryInlineSyntaxBoundaryAndAnnotationOffset() {
        val prefix = "Ordinary prose with Unicode å and \uD83D\uDE00 repeated. ".repeat(32)
        val suffix = " followed by another plain sentence \uD83D\uDE01."
        val tokens = listOf(
            "**bold**", "__bold__", "*italic*", "_italic_", "`code`",
            "[label](https://example.org)", "![image](https://example.org/image)",
            "<https://example.org>", "\\*escaped*", "\\[literal]", "\\q", "!ordinary",
            "**unfinished", "__unfinished", "*unfinished", "_unfinished", "`unfinished",
            "[unfinished", "<", "!", "\\", "[", "`", "*", "_",
        )
        for (token in tokens) for (bold in listOf(false, true)) {
            val expected = buildAnnotatedString {
                append(prefix)
                append(summaryMarkdownAnnotatedString(token, enableBoldFormatting = bold))
                append(suffix)
            }
            assertEquals(
                expected,
                summaryMarkdownAnnotatedString(prefix + token + suffix, enableBoldFormatting = bold),
                "Token: $token, bold: $bold",
            )
        }
    }

    @Test
    fun htmlRemovalPreservesLiteralAnglesAndIncompleteComments() {
        assertEquals(
            "Before after\n3 < 5 and 6 > 4\n<!-- unfinished",
            summaryMarkdownAnnotatedString(
                "Before <!-- hidden\non two lines --><b>after</b>\n3 < 5 and 6 > 4\n<!-- unfinished",
            ).text,
        )
        assertEquals(
            listOf(SummaryMarkdownListItem("• ", "First"), SummaryMarkdownListItem("• ", "Second")),
            summaryMarkdownListItems("<!-- hidden -->- First\n- Second"),
        )
    }

    @Test
    fun nestedMarkdownLinksResolveAgainstThePageAndUseTheAppCallback() {
        val opened = mutableListOf<String>()
        val rendered = summaryMarkdownAnnotatedString(
            "- **[Issue](/owner/repo/issues/1)**\n- [Details](#details)\n" +
                "- [Query](?view=full#details)\n- [External](https://example.org/info)",
            baseUrl = "https://github.com/owner/repo/releases/tag/v1",
            onOpenLink = opened::add,
        )
        val links = rendered.getLinkAnnotations(0, rendered.length)
        val expected = listOf(
            "https://github.com/owner/repo/issues/1",
            "https://github.com/owner/repo/releases/tag/v1#details",
            "https://github.com/owner/repo/releases/tag/v1?view=full#details",
            "https://example.org/info",
        )
        assertEquals(expected, links.map { (it.item as LinkAnnotation.Url).url })
        links.forEach { it.item.linkInteractionListener!!.onClick(it.item) }
        assertEquals(expected, opened)
        assertTrue(rendered.spanStyles.any {
            it.item.fontWeight == FontWeight.Bold && rendered.text.substring(it.start, it.end) == "Issue"
        })
    }

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
    fun preservesParagraphGapsWhileCompactingMixedWhitespaceAroundListsAndHeadings() {
        val rendered = summaryMarkdownAnnotatedString(
            "Intro\n \t\n\t \n- First\n\t\n  \n- Second\n \t \nFollowing paragraph" +
                "\n \n\t\n## Heading\n\t \n \nFinal paragraph",
        )

        assertEquals(
            "Intro\n\n• First\n• Second\n\nFollowing paragraph\n\nHeading\nFinal paragraph",
            rendered.text,
        )
    }

    @Test
    fun preservesSpacingAcrossLongParagraphAndListInput() {
        val markdown = (1..256).joinToString("\n \t\n\t\n") { index ->
            "Paragraph $index\n\t\n- First $index\n \n\t\n- Second $index"
        }
        val expected = (1..256).joinToString("\n\n") { index ->
            "Paragraph $index\n\n• First $index\n• Second $index"
        }

        assertEquals(expected, summaryMarkdownAnnotatedString(markdown).text)
        assertEquals(null, summaryMarkdownListItems(markdown))
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
    fun listLayoutIgnoresWhitespaceRunsAndKeepsAnIncompleteFinalMarker() {
        val blankRun = " \t\n".repeat(256)

        assertEquals(
            listOf(
                SummaryMarkdownListItem("• ", "First"),
                SummaryMarkdownListItem("2. ", "Second"),
                SummaryMarkdownListItem("• ", ""),
            ),
            summaryMarkdownListItems("- First\n${blankRun}2. Second\n${blankRun}-"),
        )
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

    @Test
    fun streamingFadeKeepsCommonPrefixUnicodeBoundaries() {
        val random = Random(481)
        val atoms = listOf("a", " ", "\n", "å", "\uD83D\uDE00", "\uD83D", "\uDE01")
        fun text() = buildString { repeat(random.nextInt(12)) { append(atoms.random(random)) } }
        val pairs = listOf(
            "" to "new", "same" to "same", "longer" to "long", "old" to "new",
            "x\uD83D\uDE00" to "x\uD83D\uDE01", "x\uD83D" to "x\uD83D\uDE00",
            "x\uD83D\uDE00" to "x\uD83D", "a".repeat(16_384) to "a".repeat(16_384) + "more",
        ) + List(1_000) {
            val prefix = text()
            (prefix + text()) to (prefix + text())
        }
        for ((previous, current) in pairs) for (streaming in listOf(false, true)) {
            for (wasStreaming in listOf(false, true)) {
                val prefixLength = previous.commonPrefixWith(current).length
                val expected = if ((!streaming && !wasStreaming) || current.isEmpty() ||
                    current == previous || prefixLength >= current.length) null else prefixLength until current.length
                assertEquals(expected, streamingTextFadeRange(previous, current, streaming, wasStreaming))
            }
        }
    }
}
