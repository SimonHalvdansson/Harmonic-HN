package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun renderModelHitsRefreshRecencyAndPeekDoesNot() {
        CommentRenderModelCache.clearForTest()
        val models = List(192) { CommentRenderModelCache.get(it, "body $it", false) }

        assertSame(models[0], CommentRenderModelCache.get(0, "body 0", false))
        assertSame(models[1], CommentRenderModelCache.peek(1, "body 1", false))
        CommentRenderModelCache.get(192, "body 192", false)

        assertSame(models[0], CommentRenderModelCache.peek(0, "body 0", false))
        assertEquals(null, CommentRenderModelCache.peek(1, "body 1", false))
        assertSame(models[2], CommentRenderModelCache.peek(2, "body 2", false))
        assertEquals(192, CommentRenderModelCache.entryCountForTest())
    }

    @Test
    fun renderModelRevisionsInvalidateBothLinkModesWithoutRemovingOtherComments() {
        CommentRenderModelCache.clearForTest()
        CommentRenderModelCache.get(1, "old", false)
        CommentRenderModelCache.get(1, "old", true)
        val other = CommentRenderModelCache.get(2, "other", false)

        val updated = CommentRenderModelCache.get(1, "new", false)

        assertEquals(null, CommentRenderModelCache.peek(1, "old", false))
        assertEquals(null, CommentRenderModelCache.peek(1, "old", true))
        assertSame(updated, CommentRenderModelCache.peek(1, "new", false))
        assertSame(other, CommentRenderModelCache.peek(2, "other", false))
        assertEquals(2, CommentRenderModelCache.entryCountForTest())
    }

    @Test
    fun installingAnExistingModelDoesNotReplaceItOrRefreshItsRecency() {
        CommentRenderModelCache.clearForTest()
        val models = List(192) { CommentRenderModelCache.get(it, "body $it", false) }
        CommentRenderModelCache.install(0, "body 0", false, CommentRenderModelCache.prepare("replacement", false))
        assertSame(models[0], CommentRenderModelCache.peek(0, "body 0", false))

        CommentRenderModelCache.get(192, "body 192", false)

        assertEquals(null, CommentRenderModelCache.peek(0, "body 0", false))
        assertSame(models[1], CommentRenderModelCache.peek(1, "body 1", false))
    }

    @Test
    fun weightedRenderModelEvictionStillUsesLeastRecentlyUsedEntry() {
        CommentRenderModelCache.clearForTest()
        val source = "x".repeat(48 * 1024)
        val models = List(10) { CommentRenderModelCache.get(it, source, false) }
        CommentRenderModelCache.get(0, source, false)

        CommentRenderModelCache.get(10, source, false)

        assertSame(models[0], CommentRenderModelCache.peek(0, source, false))
        assertEquals(null, CommentRenderModelCache.peek(1, source, false))
        assertEquals(10, CommentRenderModelCache.entryCountForTest())
    }

    @Test
    fun htmlTextHitsRefreshRecencyAndContainsDoesNot() {
        CommentHtmlTextCache.clearForTest()
        val first = AnnotatedString("first prepared")
        repeat(384) { CommentHtmlTextCache.install("body $it", if (it == 0) first else AnnotatedString("$it")) }

        assertSame(first, CommentHtmlTextCache.get("body 0"))
        assertTrue(CommentHtmlTextCache.contains("body 1"))
        CommentHtmlTextCache.install("body 384", AnnotatedString("384"))

        assertSame(first, CommentHtmlTextCache.get("body 0"))
        assertFalse(CommentHtmlTextCache.contains("body 1"))
        assertTrue(CommentHtmlTextCache.contains("body 2"))
        assertEquals(384, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun weightedHtmlEvictionStillUsesLeastRecentlyUsedEntry() {
        CommentHtmlTextCache.clearForTest()
        val sources = List(11) { "$it:" + "x".repeat(64 * 1024 - 3) }
        sources.take(10).forEach { CommentHtmlTextCache.install(it, AnnotatedString(it)) }
        CommentHtmlTextCache.get(sources[0])

        CommentHtmlTextCache.install(sources[10], AnnotatedString(sources[10]))

        assertTrue(CommentHtmlTextCache.contains(sources[0]))
        assertFalse(CommentHtmlTextCache.contains(sources[1]))
        assertEquals(10, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun installingExistingHtmlDoesNotRefreshItsRecency() {
        CommentHtmlTextCache.clearForTest()
        repeat(384) { CommentHtmlTextCache.install("body $it", AnnotatedString("$it")) }
        CommentHtmlTextCache.install("body 0", AnnotatedString("replacement"))

        CommentHtmlTextCache.install("body 384", AnnotatedString("384"))

        assertFalse(CommentHtmlTextCache.contains("body 0"))
        assertTrue(CommentHtmlTextCache.contains("body 1"))
    }

    @Test
    fun collapsedPreviewRetainsTruncationRevisionAndRecencyBehavior() {
        // Filling all 192 slots evicts any entries left by other tests in this private cache.
        val sources = List(193) { "$it:" + "x".repeat(250) }
        var parses = 0
        val parse: (String) -> String = { source ->
            parses++
            assertEquals(240, source.length)
            "preview\n$source"
        }
        repeat(192) { collapsedCommentPreview(10_000 + it, sources[it], true, parse) }
        parses = 0

        val first = collapsedCommentPreview(10_000, sources[0], true, parse)
        assertEquals(("preview " + sources[0]).take(120), first)
        collapsedCommentPreview(10_192, sources[192], true, parse)
        assertEquals(1, parses)
        assertEquals(first, collapsedCommentPreview(10_000, sources[0] + "suffix", true, parse))
        assertEquals(1, parses)
        collapsedCommentPreview(10_001, sources[1], true, parse)
        assertEquals(2, parses)

        collapsedCommentPreview(10_000, "revision " + sources[0], true, parse)
        collapsedCommentPreview(10_000, sources[0], true, parse)
        assertEquals(4, parses)
    }
}
