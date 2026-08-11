package com.simon.harmonichackernews.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogMarkdownParserTest {
    @Test
    fun parsesHeadingsParagraphsAndBullets() {
        assertEquals(
            listOf(
                ChangelogBlock.Heading("Version 3.1"),
                ChangelogBlock.Paragraph("A release summary split over two lines."),
                ChangelogBlock.Bullet("First change"),
                ChangelogBlock.Bullet("Second change"),
            ),
            parseChangelogMarkdown(
                """
                    # Version 3.1

                    A release summary split
                    over two lines.

                    - First change
                    - Second change
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun ignoresBomAndBlankLines() {
        assertEquals(
            listOf(ChangelogBlock.Heading("Version 1")),
            parseChangelogMarkdown("\uFEFF\n\n## Version 1\n"),
        )
    }
}
