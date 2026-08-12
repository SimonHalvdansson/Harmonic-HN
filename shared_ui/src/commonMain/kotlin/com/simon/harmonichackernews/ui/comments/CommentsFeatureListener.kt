package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.presentation.CommentMenuAction
import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.presentation.CommentsShareAction
import com.simon.harmonichackernews.presentation.CommentsSheetAction

/** Shared comments UI binding; callbacks are limited to platform UI and facilities. */
class CommentsFeatureListener(
    private val feature: CommentsFeatureRuntime,
    private val platform: PlatformCallbacks,
) : CommentsComposeController.Listener {
    override fun onToggleComment(comment: Comment, position: Int) = feature.toggleExpanded(comment)

    override fun onScrollPositionChanged(commentId: Int, offset: Int) {
        if (!platform.isRestoringScroll()) feature.recordScrollPosition(commentId, offset)
    }

    override fun onCommentAction(comment: Comment, action: CommentMenuAction) {
        if (!platform.canHandleCommentAction()) return
        feature.commentAction(
            action = action,
            comment = comment,
            voteLoading = platform.isCommentVoteLoading(comment.id),
            downvoted = platform.isCommentDownvoted(comment.id),
        )
    }

    override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) =
        platform.onCommentActionOverlayVisibilityChanged()
    override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) =
        platform.onLinkPreviewOverlayVisibilityChanged()
    override fun onHeaderClick() = platform.openHeaderLink()
    override fun onHeaderPreviewLoaded() = platform.onHeaderPreviewLoaded()
    override fun onHeaderPreviewLoadFailed() = platform.onHeaderPreviewLoadFailed()
    override fun onHeaderAction(action: CommentsHeaderAction) = feature.header(action)
    override fun onShareAction(action: CommentsShareAction) = feature.share(action)
    override fun onMoreAction(action: CommentsMoreAction) = feature.more(action)

    override fun onSearchResultSelected(comment: Comment) {
        feature.expandParents(comment)
        platform.scrollToSearchResult(comment.id)
    }

    override fun onSearchQueryChanged(query: String) = feature.setSearchQuery(query)
    override fun onSortComments(sortType: String) = feature.setSorting(sortType)
    override fun onSheetAction(action: CommentsSheetAction) = feature.sheet(action)
    override fun onCollapseSheetForWebsite() = platform.collapseSheetForWebsite()
    override fun onSheetProgressChanged(expandedFraction: Float) =
        platform.onSheetProgressChanged(expandedFraction)
    override fun onSheetSettled(expanded: Boolean) = platform.onSheetSettled(expanded)
    override fun onHeaderColorChanged(color: Int) = platform.onHeaderColorChanged(color)
    override fun onHeaderCoverageChanged(coverage: Float) = platform.onHeaderCoverageChanged(coverage)
    override fun onPollOption(optionId: Int) = platform.votePollOption(optionId)

    interface PlatformCallbacks {
        fun isRestoringScroll(): Boolean
        fun canHandleCommentAction(): Boolean
        fun isCommentVoteLoading(commentId: Int): Boolean
        fun isCommentDownvoted(commentId: Int): Boolean
        fun onCommentActionOverlayVisibilityChanged()
        fun onLinkPreviewOverlayVisibilityChanged()
        fun openHeaderLink()
        fun onHeaderPreviewLoaded()
        fun onHeaderPreviewLoadFailed()
        fun scrollToSearchResult(commentId: Int)
        fun collapseSheetForWebsite()
        fun onSheetProgressChanged(expandedFraction: Float)
        fun onSheetSettled(expanded: Boolean)
        fun onHeaderColorChanged(color: Int)
        fun onHeaderCoverageChanged(coverage: Float)
        fun votePollOption(optionId: Int)
    }
}
