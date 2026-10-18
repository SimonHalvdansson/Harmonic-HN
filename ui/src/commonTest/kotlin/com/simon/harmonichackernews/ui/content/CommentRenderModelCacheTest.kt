package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableCommentThreadState
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CommentRenderModelCacheTest {
    @Test
    fun prefetchServesBothQuoteModesAndFadeOutWithoutAnotherParseOrCacheEntry() = runTest {
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()
        val comments = listOf("Ordinary <b>comment</b>", "&gt; A <b>quote</b><p>Reply").mapIndexed { index, html ->
            Comment().apply { id = index + 1; text = html }
                .let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }
        }
        val prepared = mutableMapOf<String, PreparedCommentHtml>()
        prefetchCommentRenderModels(comments, collectLinks = false) { html ->
            prepareCommentHtmlContent(html).also { prepared[html] = it }
        }
        assertEquals(2, prepared.size)
        val listener = LinkInteractionListener { error("No links in fixture") }
        repeat(3) {
            comments.forEach { comment ->
                val html = comment.expandedAnchorText!!
                val expected = prepared.getValue(html)
                // Identity verifies that UI rendering consumes the worker result, not another parse.
                assertSame(expected.plain, htmlAnnotatedString(html, Color.Blue, listener, false))
                assertSame(expected.highlighted, htmlAnnotatedString(html, Color.Blue, listener, true))
                assertSame(expected.decoratedPlain, htmlAnnotatedString(html, Color.Blue, listener, false, true))
                assertSame(expected, CommentHtmlTextCache.getPrepared(html))
            }
            assertEquals(2, CommentHtmlTextCache.entryCountForTest())
        }
        var extraPreparations = 0
        prefetchCommentRenderModels(comments, collectLinks = false) {
            extraPreparations++
            prepareCommentHtmlContent(it)
        }
        assertEquals(0, extraPreparations)
        assertEquals(2, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun quoteModesSharePlainTextWhenThereAreNoQuotesAndRetainLinkOffsetsWhenThereAre() {
        CommentHtmlTextCache.clearForTest()
        val plain = "A <b>normal</b> comment with a &gt; comparison"
        assertSame(CommentHtmlTextCache.get(plain, false), CommentHtmlTextCache.get(plain, true))
        val html = "&gt;&gt; <b>Quote</b> <a href='https://example.com'>link</a><p>Reply"
        val prepared = CommentHtmlTextCache.getPrepared(html)
        for (enabled in listOf(false, true)) {
            val rendered = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener {}, enabled)
            assertEquals(if (enabled) "Quote link\n\nReply" else ">> Quote link\n\nReply", rendered.text)
            val link = rendered.getLinkAnnotations(0, rendered.length).single()
            assertEquals("link", rendered.text.substring(link.start, link.end))
            assertSame(prepared, CommentHtmlTextCache.getPrepared(html))
        }
        assertEquals(2, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun failedPrefetchDoesNotBlockOtherCommentsOrCacheAPartialResultAndCanRetry() = runTest {
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()
        val comments = commentsForPrefetch(10)
        val failed = comments[1]
        val failedHtml = failed.expandedAnchorText!!

        prefetchCommentRenderModels(comments, collectLinks = false) { html ->
            if (html == failedHtml) throw IllegalArgumentException("Cannot prepare comment")
            prepareCommentHtmlContent(html)
        }

        assertEquals(9, CommentRenderModelCache.entryCountForTest())
        assertEquals(null, CommentRenderModelCache.peek(failed.id, failedHtml, false))
        assertFalse(CommentHtmlTextCache.contains(failedHtml))
        comments.filterNot { it.id == failed.id }.forEach { comment ->
            assertTrue(CommentHtmlTextCache.contains(comment.expandedAnchorText!!))
        }

        prefetchCommentRenderModels(comments, collectLinks = false)

        assertEquals(10, CommentRenderModelCache.entryCountForTest())
        assertEquals(prepareCommentHtml(failedHtml), CommentHtmlTextCache.get(failedHtml))
    }

    @Test
    fun prefetchRetainsCompletedCommentsButDoesNotInstallCancelledComment() = runTest {
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()
        val comments = commentsForPrefetch(3)
        val cancellation = CancellationException("Prefetch cancelled")
        var prepared = 0

        val thrown = assertFailsWith<CancellationException> {
            prefetchCommentRenderModels(comments, collectLinks = false) { html ->
                if (++prepared == 2) throw cancellation
                prepareCommentHtmlContent(html)
            }
        }

        assertEquals(cancellation.message, thrown.message)
        assertEquals(2, prepared)
        assertEquals(1, CommentRenderModelCache.entryCountForTest())
        assertEquals(1, CommentHtmlTextCache.entryCountForTest())
        assertTrue(CommentHtmlTextCache.contains(comments[0].expandedAnchorText!!))
        assertFalse(CommentHtmlTextCache.contains(comments[1].expandedAnchorText!!))
        assertFalse(CommentHtmlTextCache.contains(comments[2].expandedAnchorText!!))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scrollingFinishesCurrentCommentThenPreparesOnlyTheLatestWindow() = runTest {
        val comments = commentsForPrefetch(6)
        val windows = Channel<List<PortableCommentItem>>(Channel.UNLIMITED)
        val finishFirst = CompletableDeferred<Unit>()
        val preparedIds = mutableListOf<Int>()
        val job = launch {
            prefetchCommentWindows(windows.receiveAsFlow()) { comment ->
                if (comment.id == 1) finishFirst.await()
                preparedIds += comment.id
            }
        }
        windows.send(comments.take(2))
        runCurrent()
        windows.send(comments.subList(2, 4))
        windows.send(comments.subList(4, 6))
        runCurrent()
        assertTrue(preparedIds.isEmpty())
        finishFirst.complete(Unit)
        windows.close()
        job.join()
        assertEquals(listOf(1, 5, 6), preparedIds)
    }

    @Test
    fun leavingTheScreenCancelsItsPrefetchWorker() = runTest {
        val windows = Channel<List<PortableCommentItem>>(Channel.UNLIMITED)
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val job = launch {
            prefetchCommentWindows(windows.receiveAsFlow()) {
                started.complete(Unit)
                try {
                    CompletableDeferred<Unit>().await()
                } finally {
                    cancelled = true
                }
            }
        }
        windows.send(commentsForPrefetch(3))
        started.await()
        job.cancelAndJoin()
        windows.close()
        assertTrue(cancelled)
    }

    @Test
    fun textWithoutLinksReusesPreparedTextAndQuotedSegmentsKeepTheirLinkFlags() {
        val plain = preparedCommentText("Plain <b>text</b>", false, false)
        assertFalse(plain.hasCommentLinks())
        assertSame(plain, bindCommentLinks(plain, Color.Blue, LinkInteractionListener {}))

        val quoted = preparedCommentText(
            "&gt; Quoted text<p><a href='https://example.com'>Reference</a>", true, true,
        )
        val segments = quoted.quoteSpacedSegments()
        assertEquals(2, segments.size)
        assertFalse(segments[0].hasCommentLinks())
        assertTrue(segments[1].hasCommentLinks())
        val linked = bindCommentLinks(segments[1], Color.Blue, LinkInteractionListener {})
        val link = linked.getLinkAnnotations(0, linked.length).single()
        assertEquals("Reference", linked.text.substring(link.start, link.end))
    }

    private fun commentsForPrefetch(count: Int): List<PortableCommentItem> = (1..count).map { id ->
        Comment().apply {
            this.id = id
            text = "<p>Prefetch body $id</p>"
        }.let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }
    }

    @Test
    fun initialPreparationUsesRestoredViewportAndMatchesSynchronousRenderingInBothLinkModes() = runTest {
        val comments = (1..30).map { id ->
            Comment().apply {
                this.id = id
                text = "<p>Body $id <b>bold</b> <a href=\"https://example.com/$id\">link</a></p>"
            }.let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }
        }
        val thread = PortableCommentThreadState(visibleComments = comments.mapIndexed { index, item ->
            PortableVisibleComment(index, item, 0)
        })
        for (collectLinks in listOf(false, true)) {
            CommentRenderModelCache.clearForTest()
            CommentHtmlTextCache.clearForTest()
            prepareInitialCommentContent(thread, collectLinks, targetCommentId = 10)
            assertEquals(8, CommentRenderModelCache.entryCountForTest())
            assertEquals(null, CommentRenderModelCache.peek(1, comments[0].expandedAnchorText!!, collectLinks))
            for (comment in comments.subList(9, 17)) {
                val expected = CommentRenderModelCache.prepare(comment.expandedAnchorText, collectLinks)
                val actual = CommentRenderModelCache.peek(comment.id, comment.expandedAnchorText!!, collectLinks)
                assertEquals(expected.contentBlocks.map { it.bodyHtml }, actual?.contentBlocks?.map { it.bodyHtml })
                assertEquals(expected.references?.links?.map { it.url }, actual?.references?.links?.map { it.url })
                expected.contentBlocks.mapNotNull { it.bodyHtml }.forEach { html ->
                    assertTrue(CommentHtmlTextCache.contains(html))
                    assertEquals(prepareCommentHtml(html), CommentHtmlTextCache.get(html))
                    assertEquals(prepareCommentHtml(html, false), CommentHtmlTextCache.get(html, false))
                }
            }
        }
    }

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
        repeat(384) { CommentHtmlTextCache.install("body $it", PreparedCommentHtml(if (it == 0) first else AnnotatedString("$it"))) }

        assertSame(first, CommentHtmlTextCache.get("body 0"))
        assertTrue(CommentHtmlTextCache.contains("body 1"))
        CommentHtmlTextCache.install("body 384", PreparedCommentHtml(AnnotatedString("384")))

        assertSame(first, CommentHtmlTextCache.get("body 0"))
        assertFalse(CommentHtmlTextCache.contains("body 1"))
        assertTrue(CommentHtmlTextCache.contains("body 2"))
        assertEquals(384, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun weightedHtmlEvictionStillUsesLeastRecentlyUsedEntry() {
        CommentHtmlTextCache.clearForTest()
        val sources = List(11) { "$it:" + "x".repeat(64 * 1024 - 3) }
        sources.take(10).forEach { CommentHtmlTextCache.install(it, PreparedCommentHtml(AnnotatedString(it))) }
        CommentHtmlTextCache.get(sources[0])

        CommentHtmlTextCache.install(sources[10], PreparedCommentHtml(AnnotatedString(sources[10])))

        assertTrue(CommentHtmlTextCache.contains(sources[0]))
        assertFalse(CommentHtmlTextCache.contains(sources[1]))
        assertEquals(10, CommentHtmlTextCache.entryCountForTest())
    }

    @Test
    fun installingExistingHtmlDoesNotRefreshItsRecency() {
        CommentHtmlTextCache.clearForTest()
        repeat(384) { CommentHtmlTextCache.install("body $it", PreparedCommentHtml(AnnotatedString("$it"))) }
        CommentHtmlTextCache.install("body 0", PreparedCommentHtml(AnnotatedString("replacement")))

        CommentHtmlTextCache.install("body 384", PreparedCommentHtml(AnnotatedString("384")))

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
