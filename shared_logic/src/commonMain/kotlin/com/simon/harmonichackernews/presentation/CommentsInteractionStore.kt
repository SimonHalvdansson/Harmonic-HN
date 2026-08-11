package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment

data class CommentNavigationRequest(
    val serial: Int,
    val forward: Boolean,
    val topLevelOnly: Boolean,
    val animate: Boolean,
    val scaleLongScrollSpeed: Boolean,
    val edge: CommentNavigationEdge? = null,
)

enum class CommentNavigationEdge { First, Last }

data class CommentScrollRequest(
    val serial: Int,
    val commentId: Int,
    val topOffsetPx: Int,
    val animate: Boolean,
    val searchResult: Boolean,
)

data class CommentSheetRequest(
    val serial: Int,
    val expanded: Boolean,
)

data class CommentPredictiveBackSettleRequest(val serial: Int, val target: Float)

sealed interface CommentLinkPreview {
    data class Reference(
        val originalUrl: String,
        val fallbackTitle: String,
        val resolvedTitle: String?,
        val sourceCommentId: Int?,
        val headerReference: Boolean,
    ) : CommentLinkPreview

    data class Image(
        val imageUrl: String,
        val description: String,
        val backgroundColor: Int,
    ) : CommentLinkPreview
}

data class CommentsInteractionState(
    val sheetSlideOffset: Float,
    val topInsetPx: Int = 0,
    val sheetRequest: CommentSheetRequest? = null,
    val navigationRequest: CommentNavigationRequest? = null,
    val websiteRequestVersion: Int = 0,
    val scrollRequest: CommentScrollRequest? = null,
    val stopScrollRequestVersion: Int = 0,
    val highlightedCommentId: Int = -1,
    val searchScrollTopTargetId: Int = -1,
    val searchDialogVisible: Boolean = false,
    val predictiveBackActive: Boolean = false,
    val predictiveBackProgress: Float = 0f,
    val commentAction: Comment? = null,
    val commentActionDismissRequestVersion: Int = 0,
    val commentActionPredictiveBackProgress: Float = 0f,
    val commentActionPredictiveBackEdge: Int = 0,
    val commentActionPredictiveBackTouchY: Float = 0f,
    val commentActionFavoriteLoadingId: Int = -1,
    val commentActionVoteLoadingId: Int = -1,
    val commentActionVoteLoadingAction: Int = -1,
    val commentActionDownvotedIds: Set<Int> = emptySet(),
    val suppressedCommentIds: Set<Int> = emptySet(),
    val linkPreview: CommentLinkPreview? = null,
    val linkPreviewVisibleUrl: String? = null,
    val linkPreviewDismissRequestVersion: Int = 0,
    val linkPreviewPredictiveBackProgress: Float = 0f,
    val linkPreviewPredictiveBackEdge: Int = 0,
    val linkPreviewPredictiveBackTouchY: Float = 0f,
    val linkPreviewPredictiveBackSettleRequest: CommentPredictiveBackSettleRequest? = null,
)

/**
 * Platform-neutral interaction state machine for the comments screen.
 *
 * Rendering, scrolling, Android back dispatch, and source geometry remain in the platform UI.
 * This store owns the request ordering and state transitions so every future UI shell can share
 * the same navigation, search, sheet, and comment-action behavior.
 */
class CommentsInteractionStore(
    initialShowWebsite: Boolean,
    private val shouldSmoothScroll: () -> Boolean,
) {
    var state = CommentsInteractionState(
        sheetSlideOffset = if (initialShowWebsite) 0f else 1f,
    )
        private set

    private var requestSerial = 0

    fun updateTopInset(topInsetPx: Int) {
        state = state.copy(topInsetPx = topInsetPx)
    }

    fun updateSheet(slideOffset: Float, topInsetPx: Int) {
        state = state.copy(
            sheetSlideOffset = slideOffset.coerceIn(0f, 1f),
            topInsetPx = topInsetPx,
        )
    }

    fun requestSheet(expanded: Boolean) {
        state = state.copy(
            sheetRequest = CommentSheetRequest(++requestSerial, expanded),
        )
    }

    fun consumeSheetRequest(request: CommentSheetRequest) {
        if (state.sheetRequest == request) state = state.copy(sheetRequest = null)
    }

    fun navigate(
        forward: Boolean,
        topLevelOnly: Boolean,
        scaleLongScrollSpeed: Boolean,
        edge: CommentNavigationEdge? = null,
    ) {
        state = state.copy(
            navigationRequest = CommentNavigationRequest(
                serial = ++requestSerial,
                forward = forward,
                topLevelOnly = topLevelOnly,
                animate = shouldSmoothScroll(),
                scaleLongScrollSpeed = scaleLongScrollSpeed,
                edge = edge,
            ),
        )
    }

    fun consumeNavigationRequest(request: CommentNavigationRequest) {
        if (state.navigationRequest == request) state = state.copy(navigationRequest = null)
    }

    fun requestWebsite() {
        state = state.copy(websiteRequestVersion = state.websiteRequestVersion + 1)
    }

    fun scrollToComment(
        commentId: Int,
        topOffsetPx: Int = state.topInsetPx,
        animate: Boolean = true,
        searchResult: Boolean = false,
    ) {
        state = state.copy(
            scrollRequest = CommentScrollRequest(
                serial = ++requestSerial,
                commentId = commentId,
                topOffsetPx = topOffsetPx,
                animate = animate && shouldSmoothScroll(),
                searchResult = searchResult,
            ),
        )
    }

    fun consumeScrollRequest(request: CommentScrollRequest) {
        if (state.scrollRequest == request) state = state.copy(scrollRequest = null)
    }

    fun requestStopScroll() {
        state = state.copy(stopScrollRequestVersion = state.stopScrollRequestVersion + 1)
    }

    fun showCommentSearch() {
        state = state.copy(searchDialogVisible = true)
    }

    fun dismissCommentSearch() {
        state = state.copy(searchDialogVisible = false)
    }

    fun revealSearchResult(commentId: Int, visiblePosition: Int, showUpdate: Boolean) {
        state = state.copy(
            highlightedCommentId = commentId,
            searchScrollTopTargetId = commentId.takeIf {
                visiblePosition > SEARCH_SCROLL_TOP_THRESHOLD && !showUpdate
            } ?: -1,
        )
    }

    fun clearSearchHighlight(commentId: Int) {
        if (state.highlightedCommentId == commentId) {
            state = state.copy(highlightedCommentId = -1)
        }
    }

    fun clearSearchScrollTopTarget() {
        state = state.copy(searchScrollTopTargetId = -1)
    }

    fun beginPredictiveBack(progress: Float) {
        state = state.copy(
            predictiveBackActive = true,
            predictiveBackProgress = progress.coerceIn(0f, 1f),
        )
    }

    fun updatePredictiveBack(progress: Float) {
        state = state.copy(predictiveBackProgress = progress.coerceIn(0f, 1f))
    }

    fun endPredictiveBack() {
        state = state.copy(
            predictiveBackActive = false,
            predictiveBackProgress = 0f,
        )
    }

    fun showCommentActions(comment: Comment, stopScroll: Boolean): Boolean {
        if (state.commentAction != null) return false
        state = state.copy(
            commentAction = comment,
            commentActionDismissRequestVersion = 0,
            commentActionPredictiveBackProgress = 0f,
            suppressedCommentIds = setOf(comment.id),
            stopScrollRequestVersion = state.stopScrollRequestVersion + if (stopScroll) 1 else 0,
        )
        return true
    }

    fun requestDismissCommentActions() {
        if (state.commentAction != null) {
            state = state.copy(
                commentActionDismissRequestVersion =
                    state.commentActionDismissRequestVersion + 1,
            )
        }
    }

    fun updateCommentActionPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        if (state.commentAction == null) return
        state = state.copy(
            commentActionPredictiveBackProgress = progress.coerceIn(0f, 1f),
            commentActionPredictiveBackEdge = edge,
            commentActionPredictiveBackTouchY = touchY,
        )
    }

    fun cancelCommentActionPredictiveBack() {
        state = state.copy(commentActionPredictiveBackProgress = 0f)
    }

    fun commitCommentActionPredictiveBack() {
        if (state.commentAction == null) return
        state = state.copy(commentActionPredictiveBackProgress = 0f)
        requestDismissCommentActions()
    }

    fun completeCommentActionDismiss(): Boolean {
        val commentId = state.commentAction?.id ?: return false
        state = state.copy(
            commentAction = null,
            commentActionDismissRequestVersion = 0,
            commentActionPredictiveBackProgress = 0f,
            suppressedCommentIds = state.suppressedCommentIds - commentId,
        )
        return true
    }

    fun setCommentActionFavoriteLoading(commentId: Int, loading: Boolean) {
        state = state.copy(commentActionFavoriteLoadingId = if (loading) commentId else -1)
    }

    fun setCommentActionVoteLoading(commentId: Int, action: Int) {
        state = state.copy(
            commentActionVoteLoadingId = commentId,
            commentActionVoteLoadingAction = action,
        )
    }

    fun finishCommentActionVote(commentId: Int, downvoted: Boolean) {
        state = state.copy(
            commentActionDownvotedIds = if (downvoted) {
                state.commentActionDownvotedIds + commentId
            } else {
                state.commentActionDownvotedIds - commentId
            },
            commentActionVoteLoadingId = if (state.commentActionVoteLoadingId == commentId) {
                -1
            } else {
                state.commentActionVoteLoadingId
            },
            commentActionVoteLoadingAction = if (state.commentActionVoteLoadingId == commentId) {
                -1
            } else {
                state.commentActionVoteLoadingAction
            },
        )
    }

    fun showReferencePreview(
        originalUrl: String,
        fallbackTitle: String,
        resolvedTitle: String?,
        sourceCommentId: Int?,
        headerReference: Boolean,
    ): Boolean {
        if (originalUrl.isBlank()) return false
        resetLinkPreviewAnimationState()
        state = state.copy(
            linkPreview = CommentLinkPreview.Reference(
                originalUrl = originalUrl,
                fallbackTitle = fallbackTitle,
                resolvedTitle = resolvedTitle,
                sourceCommentId = sourceCommentId,
                headerReference = headerReference,
            ),
            linkPreviewVisibleUrl = originalUrl,
            stopScrollRequestVersion = state.stopScrollRequestVersion + 1,
        )
        return true
    }

    fun showImagePreview(
        imageUrl: String,
        description: String,
        backgroundColor: Int,
    ): Boolean {
        if (imageUrl.isBlank()) return false
        resetLinkPreviewAnimationState()
        state = state.copy(
            linkPreview = CommentLinkPreview.Image(imageUrl, description, backgroundColor),
            linkPreviewVisibleUrl = imageUrl,
            stopScrollRequestVersion = state.stopScrollRequestVersion + 1,
        )
        return true
    }

    fun updateLinkPreviewVisibleUrl(originalUrl: String, resolvedUrl: String?) {
        val current = state.linkPreview as? CommentLinkPreview.Reference ?: return
        if (current.originalUrl == originalUrl && !resolvedUrl.isNullOrBlank()) {
            state = state.copy(linkPreviewVisibleUrl = resolvedUrl)
        }
    }

    fun requestDismissLinkPreview() {
        if (state.linkPreview == null || state.linkPreviewDismissRequestVersion != 0) return
        state = state.copy(linkPreviewDismissRequestVersion = ++requestSerial)
    }

    fun completeLinkPreviewDismiss(): Boolean {
        if (state.linkPreview == null) return false
        state = state.copy(linkPreview = null, linkPreviewVisibleUrl = null)
        resetLinkPreviewAnimationState()
        return true
    }

    fun updateLinkPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        if (state.linkPreview == null || state.linkPreviewDismissRequestVersion != 0) return
        state = state.copy(
            linkPreviewPredictiveBackSettleRequest = null,
            linkPreviewPredictiveBackEdge = edge,
            linkPreviewPredictiveBackTouchY = touchY,
            linkPreviewPredictiveBackProgress = progress.coerceIn(0f, 1f),
        )
    }

    fun cancelLinkPreviewPredictiveBack() {
        if (state.linkPreview == null || state.linkPreviewPredictiveBackProgress <= 0f) return
        state = state.copy(
            linkPreviewPredictiveBackSettleRequest = CommentPredictiveBackSettleRequest(
                serial = ++requestSerial,
                target = 0f,
            ),
        )
    }

    fun finishLinkPreviewPredictiveBackSettle(request: CommentPredictiveBackSettleRequest) {
        if (state.linkPreviewPredictiveBackSettleRequest != request) return
        state = state.copy(
            linkPreviewPredictiveBackProgress = request.target.coerceIn(0f, 1f),
            linkPreviewPredictiveBackSettleRequest = null,
        )
    }

    private fun resetLinkPreviewAnimationState() {
        state = state.copy(
            linkPreviewDismissRequestVersion = 0,
            linkPreviewPredictiveBackProgress = 0f,
            linkPreviewPredictiveBackSettleRequest = null,
        )
    }

    private companion object {
        const val SEARCH_SCROLL_TOP_THRESHOLD = 10
    }
}
