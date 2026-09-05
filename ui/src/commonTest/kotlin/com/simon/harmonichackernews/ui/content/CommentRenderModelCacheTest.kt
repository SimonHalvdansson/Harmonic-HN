package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CommentRenderModelCacheTest {
    @Test
    fun unchangedCommentReusesItsParsedRenderModel() {
        val first = CommentRenderModelCache.get(
            commentId = 123,
            expandedHtml = "Hello <b>world</b>",
            collectLinks = true,
        )
        val second = CommentRenderModelCache.get(
            commentId = 123,
            expandedHtml = "Hello <b>world</b>",
            collectLinks = true,
        )

        assertSame(first, second)
    }

    @Test
    fun sourceChangeCreatesANewRenderModel() {
        CommentRenderModelCache.clearForTest()
        val first = CommentRenderModelCache.get(456, "old", false)
        val second = CommentRenderModelCache.get(456, "new", false)

        assertNotSame(first, second)
        assertEquals(1, CommentRenderModelCache.entryCountForTest())
    }

    @Test
    fun expandedCommentDoesNotParseCollapsedPreview() {
        var parses = 0

        val preview = collapsedCommentPreview(789, "<b>body</b>", needed = false) {
            parses++
            "body"
        }

        assertEquals(null, preview)
        assertEquals(0, parses)
    }

    @Test
    fun oversizedSourcesAreParsedButNotRetained() {
        val oversized = "x".repeat(70 * 1024)
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()

        CommentRenderModelCache.get(999, oversized, false)
        CommentHtmlTextCache.get(oversized)

        assertEquals(0, CommentRenderModelCache.entryCountForTest())
        assertEquals(0, CommentHtmlTextCache.entryCountForTest())
    }
}
