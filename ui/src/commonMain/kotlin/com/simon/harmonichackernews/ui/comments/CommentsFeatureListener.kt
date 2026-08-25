package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.presentation.CommentMenuAction
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.CommentsIntent
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.presentation.CommentsShareAction
import com.simon.harmonichackernews.presentation.CommentsSheetAction
import com.simon.harmonichackernews.presentation.CommentsStore

/** Shared comments UI binding; callbacks are limited to platform UI and facilities. */
class CommentsFeatureListener(
    private val store: CommentsStore,
    private val platform: PlatformCallbacks,
) : CommentsComposeController.Listener {
    override fun onToggleComment(comment: PortableCommentItem, position: Int) =
        store.accept(CommentsIntent.ToggleComment(comment.id))

    override fun onScrollPositionChanged(commentId: Int, offset: Int) {
        if (!platform.isRestoringScroll()) {
            store.accept(CommentsIntent.RecordScrollPosition(commentId, offset))
        }
    }

    override fun onCommentAction(comment: PortableCommentItem, action: CommentMenuAction) {
        if (!platform.canHandleCommentAction()) return
        store.accept(CommentsIntent.CommentAction(comment, action))
    }

    override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) =
        platform.onCommentActionOverlayVisibilityChanged()
    override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) =
        platform.onLinkPreviewOverlayVisibilityChanged()
    override fun onHeaderClick() = store.accept(CommentsIntent.OpenHeaderLink)
    override fun onHeaderPreviewImageResult(imageUrl: String, success: Boolean) =
        store.accept(CommentsIntent.HeaderPreviewImageResult(imageUrl, success))
    override fun onHeaderPreviewTintExtracted(
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ) = store.recordHeaderPreviewTint(
        sourceUrl,
        baseColorArgb,
        paletteConfigKey,
        tintColorArgb,
    )
    override fun onHeaderAction(action: CommentsHeaderAction) =
        store.accept(CommentsIntent.HeaderAction(action))
    override fun onShareAction(action: CommentsShareAction) =
        store.accept(CommentsIntent.ShareAction(action))
    override fun onMoreAction(action: CommentsMoreAction) =
        store.accept(CommentsIntent.MoreAction(action))

    override fun onSearchResultSelected(comment: PortableCommentItem) {
        store.accept(CommentsIntent.ExpandParents(comment.id))
        platform.scrollToSearchResult(comment.id)
    }

    override fun onSearchQueryChanged(query: String) =
        store.accept(CommentsIntent.SearchQuery(query))
    override fun onSortComments(sortType: String) = store.accept(CommentsIntent.Sort(sortType))
    override fun onSheetAction(action: CommentsSheetAction) =
        store.accept(CommentsIntent.SheetAction(action))
    override fun onCollapseSheetForWebsite() = platform.collapseSheetForWebsite()
    override fun onSheetProgressChanged(expandedFraction: Float) =
        platform.onSheetProgressChanged(expandedFraction)
    override fun onSheetSettled(expanded: Boolean) = platform.onSheetSettled(expanded)
    override fun onHeaderColorChanged(color: Int) = platform.onHeaderColorChanged(color)
    override fun onHeaderCoverageChanged(coverage: Float) = platform.onHeaderCoverageChanged(coverage)
    override fun onPollOption(optionId: Int) = store.accept(CommentsIntent.PollOption(optionId))

    interface PlatformCallbacks {
        fun isRestoringScroll(): Boolean
        fun canHandleCommentAction(): Boolean
        fun onCommentActionOverlayVisibilityChanged()
        fun onLinkPreviewOverlayVisibilityChanged()
        fun scrollToSearchResult(commentId: Int)
        fun collapseSheetForWebsite()
        fun onSheetProgressChanged(expandedFraction: Float)
        fun onSheetSettled(expanded: Boolean)
        fun onHeaderColorChanged(color: Int)
        fun onHeaderCoverageChanged(coverage: Float)
    }
}
