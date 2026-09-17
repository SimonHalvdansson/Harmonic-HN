package com.simon.harmonichackernews.ui.comments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReleaseMarkdownTest {
    private val pageUrl = "https://github.com/audacity/audacity/releases/tag/Audacity-4.0.0"

    @Test
    fun keepsAudacityVideoThumbnailBetweenIntroductionAndEditingNotes() {
        val blocks = releaseMarkdownBlocks(
            """
            ## Audacity 4.0

            A new interface and clip editing model.

            ### Watch the video:

            <a href="https://www.youtube.com/watch?v=BTQymidLYIM">
              <img src="https://img.youtube.com/vi/BTQymidLYIM/maxresdefault.jpg" width="600">
            </a>

            ## Editing clips

            - **Select clips directly.** Click the header.
            """.trimIndent(),
            pageUrl,
        )
        assertEquals(3, blocks.size)
        assertEquals(true, assertIs<ReleaseMarkdownBlock.Text>(blocks[0]).markdown.endsWith("### Watch the video:"))
        assertEquals(
            ReleaseMarkdownBlock.Image(
                "https://img.youtube.com/vi/BTQymidLYIM/maxresdefault.jpg", "",
                "https://www.youtube.com/watch?v=BTQymidLYIM",
            ),
            blocks[1],
        )
        assertEquals(true, assertIs<ReleaseMarkdownBlock.Text>(blocks[2]).markdown.startsWith("## Editing clips"))
    }

    @Test
    fun preservesMultipleMarkdownImagesAndTheirLinksInOrder() {
        assertEquals(
            listOf(
                ReleaseMarkdownBlock.Text("Before"),
                ReleaseMarkdownBlock.Image("https://example.org/one.png", "One", null),
                ReleaseMarkdownBlock.Text("Between"),
                ReleaseMarkdownBlock.Image("https://github.com/two.jpg", "Two", "https://example.org/video"),
                ReleaseMarkdownBlock.Text("After"),
            ),
            releaseMarkdownBlocks(
                "Before\n![One](https://example.org/one.png)\nBetween\n[![Two](/two.jpg)](https://example.org/video)\nAfter",
                pageUrl,
            ),
        )
    }

    @Test
    fun doesNotLoadImagesFromCodeCommentsOrUnsupportedSchemes() {
        val markdown = """
            <!-- <img src="https://example.org/hidden.png"> -->
            ```html
            <img src="https://example.org/example.png">
            ```
            `<img src="https://example.org/inline.png">`
            ![Local](file:///private.png)
            ![Script](javascript:alert)
        """.trimIndent()
        assertEquals(1, releaseMarkdownBlocks(markdown, pageUrl).size)
        assertIs<ReleaseMarkdownBlock.Text>(releaseMarkdownBlocks(markdown, pageUrl).single())
    }

    @Test
    fun truncatesTheWholePreviewAfterAnInlineImage() {
        val blocks = releaseMarkdownPreviewBlocks(
            """
            ## Before

            ![Screenshot](https://example.org/screenshot.png)

            ## Changes

            - First change
            - Second change which is deliberately long enough to exceed the remaining preview budget
            - This must not be shown
            """.trimIndent(),
            pageUrl,
            lineBudget = 12,
        )

        assertEquals(3, blocks.size)
        assertEquals(ReleaseMarkdownBlock.Text("## Before"), blocks[0])
        assertIs<ReleaseMarkdownBlock.Image>(blocks[1])
        assertEquals("## Changes\n\n- First change\n\n...", assertIs<ReleaseMarkdownBlock.Text>(blocks[2]).markdown)
    }

    @Test
    fun leavesShortReleaseMarkdownUnchanged() {
        assertEquals(
            listOf(ReleaseMarkdownBlock.Text("A short release note.")),
            releaseMarkdownPreviewBlocks("A short release note.", pageUrl),
        )
    }
}
