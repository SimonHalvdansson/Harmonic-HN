package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommentsInteractionStoreTest {
    @Test
    fun navigationAndScrollingCaptureThePlatformAnimationPolicy() {
        var smoothScroll = true
        val store = CommentsInteractionStore(
            initialShowWebsite = false,
            shouldSmoothScroll = { smoothScroll },
        )

        store.navigate(forward = true, topLevelOnly = false, scaleLongScrollSpeed = true)
        val firstNavigation = requireNotNull(store.state.navigationRequest)
        assertTrue(firstNavigation.animate)
        assertTrue(firstNavigation.forward)

        smoothScroll = false
        store.navigate(
            forward = false,
            topLevelOnly = true,
            scaleLongScrollSpeed = true,
            edge = CommentNavigationEdge.First,
        )
        assertFalse(requireNotNull(store.state.navigationRequest).animate)
        assertEquals(CommentNavigationEdge.First, store.state.navigationRequest?.edge)

        store.updateTopInset(42)
        store.scrollToComment(commentId = 91)
        assertEquals(42, store.state.scrollRequest?.topOffsetPx)
        assertEquals(LayoutCoordinate(42), store.state.scrollRequest?.topOffset)
        assertFalse(requireNotNull(store.state.scrollRequest).animate)

        store.consumeNavigationRequest(firstNavigation)
        assertEquals(CommentNavigationEdge.First, store.state.navigationRequest?.edge)
        store.consumeNavigationRequest(requireNotNull(store.state.navigationRequest))
        assertNull(store.state.navigationRequest)
    }

    @Test
    fun commentActionOverlayHasOneOwnerAndCleansUpSuppressionAfterDismiss() {
        val store = store()
        val first = portableComment(1)
        val second = portableComment(2)

        assertTrue(store.showCommentActions(first, stopScroll = true))
        assertFalse(store.showCommentActions(second, stopScroll = true))
        assertEquals(1, store.state.commentAction?.id)
        assertEquals(setOf(1), store.state.suppressedCommentIds)
        assertEquals(1, store.state.stopScrollRequestVersion)

        store.updateCommentActionPredictiveBack(progress = 2f, edge = 1, touchY = 300f)
        assertEquals(1f, store.state.commentActionPredictiveBackProgress)
        assertEquals(BackGestureEdge.RIGHT, store.state.commentActionBackGesture.edge)
        store.commitCommentActionPredictiveBack()
        assertEquals(0f, store.state.commentActionPredictiveBackProgress)
        assertEquals(1, store.state.commentActionDismissRequestVersion)

        assertTrue(store.completeCommentActionDismiss())
        assertNull(store.state.commentAction)
        assertTrue(store.state.suppressedCommentIds.isEmpty())
        assertFalse(store.completeCommentActionDismiss())
    }

    @Test
    fun searchOnlyPinsDistantResultsWhenTheUpdateAffordanceIsHidden() {
        val store = store()

        store.revealSearchResult(commentId = 7, visiblePosition = 11, showUpdate = false)
        assertEquals(7, store.state.highlightedCommentId)
        assertEquals(7, store.state.searchScrollTopTargetId)

        store.revealSearchResult(commentId = 8, visiblePosition = 11, showUpdate = true)
        assertEquals(8, store.state.highlightedCommentId)
        assertEquals(-1, store.state.searchScrollTopTargetId)

        store.clearSearchHighlight(commentId = 7)
        assertEquals(8, store.state.highlightedCommentId)
        store.clearSearchHighlight(commentId = 8)
        assertEquals(-1, store.state.highlightedCommentId)
    }

    @Test
    fun voteCompletionOnlyClearsTheMatchingRequestAndTracksDownvotes() {
        val store = store()
        store.setCommentActionVoteLoading(commentId = 3, action = CommentMenuAction.DOWNVOTE)

        store.finishCommentActionVote(commentId = 4, downvoted = true)
        assertEquals(3, store.state.commentActionVoteLoadingId)
        assertEquals(setOf(4), store.state.commentActionDownvotedIds)

        store.finishCommentActionVote(commentId = 3, downvoted = true)
        assertEquals(-1, store.state.commentActionVoteLoadingId)
        assertEquals(null, store.state.commentActionVoteLoadingAction)
        assertEquals(setOf(3, 4), store.state.commentActionDownvotedIds)

        store.finishCommentActionVote(commentId = 4, downvoted = false)
        assertEquals(setOf(3), store.state.commentActionDownvotedIds)
    }

    @Test
    fun linkPreviewOwnsVisibleUrlPredictiveBackAndDismissalOrdering() {
        val store = store()

        assertFalse(
            store.showReferencePreview("", "Missing", null, null, headerReference = false),
        )
        assertTrue(
            store.showReferencePreview(
                originalUrl = "https://example.com/original",
                fallbackTitle = "Example",
                resolvedTitle = null,
                sourceCommentId = 42,
                headerReference = false,
            ),
        )
        assertEquals(1, store.state.stopScrollRequestVersion)
        assertEquals("https://example.com/original", store.state.linkPreviewVisibleUrl)

        store.updateLinkPreviewVisibleUrl(
            "https://example.com/original",
            "https://example.com/resolved",
        )
        assertEquals("https://example.com/resolved", store.state.linkPreviewVisibleUrl)

        store.updateLinkPreviewPredictiveBack(progress = 0.6f, edge = 1, touchY = 200f)
        store.cancelLinkPreviewPredictiveBack()
        val settle = requireNotNull(store.state.linkPreviewPredictiveBackSettleRequest)
        store.finishLinkPreviewPredictiveBackSettle(settle.copy(serial = settle.serial + 1))
        assertEquals(settle, store.state.linkPreviewPredictiveBackSettleRequest)
        store.finishLinkPreviewPredictiveBackSettle(settle)
        assertEquals(0f, store.state.linkPreviewPredictiveBackProgress)

        store.requestDismissLinkPreview()
        val dismissVersion = store.state.linkPreviewDismissRequestVersion
        store.requestDismissLinkPreview()
        assertEquals(dismissVersion, store.state.linkPreviewDismissRequestVersion)
        assertTrue(store.completeLinkPreviewDismiss())
        assertNull(store.state.linkPreview)
        assertNull(store.state.linkPreviewVisibleUrl)
        assertFalse(store.completeLinkPreviewDismiss())
    }

    private fun store() = CommentsInteractionStore(
        initialShowWebsite = false,
        shouldSmoothScroll = { true },
    )

    private fun comment(id: Int) = Comment().also { it.id = id }

    private fun portableComment(id: Int) = comment(id).let { comment ->
        PortableCommentItem(comment.toSnapshot(), comment.presentationSnapshot())
    }
}
