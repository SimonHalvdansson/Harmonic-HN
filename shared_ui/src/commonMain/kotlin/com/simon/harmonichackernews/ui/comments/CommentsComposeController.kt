package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.utils.CollectedReferenceLinks

class CommentsComposeController private constructor(
    shouldSmoothScroll: () -> Boolean,
    private val savedItemState: SavedItemStateReader,
    initialStory: Story,
    val initialShowWebsite: Boolean,
    val accountUser: String?,
    val listener: Listener,
) {
    var story by mutableStateOf(initialStory)
        private set
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var visibleComments by mutableStateOf<List<VisibleComment>>(emptyList())
        private set
    var displaySettings by mutableStateOf<CommentDisplaySettings?>(null)
        private set
    var commentsLoaded by mutableStateOf(false)
        private set
    var commentsRefreshInProgress by mutableStateOf(false)
        private set
    var loadingFailed by mutableStateOf(false)
        private set
    var loadingFailedServerError by mutableStateOf(false)
        private set
    var showUpdate by mutableStateOf(false)
        private set
    var lastRefreshed by mutableStateOf(0L)
        private set
    var commentsByOpFilterActive by mutableStateOf(false)
        private set
    var hasCommentsByOp by mutableStateOf(false)
        private set
    var adBlockActive by mutableStateOf(false)
        private set
    var integratedWebView by mutableStateOf(false)
        private set
    var readerModeAvailable by mutableStateOf(false)
        private set
    var readerModeEnabled by mutableStateOf(false)
        private set
    var statusBarHeaderColor by mutableStateOf<Color?>(null)
        private set
    var statusBarHeaderCoverage by mutableFloatStateOf(0f)
        private set
    var contentInsetLeftPx by mutableIntStateOf(0)
        private set
    var contentInsetRightPx by mutableIntStateOf(0)
        private set
    var contentVersion by mutableIntStateOf(0)
        private set
    var currentSorting by mutableStateOf("Default")
        private set
    var storyVoteLoading by mutableStateOf(false)
        private set
    var storyFavoriteLoading by mutableStateOf(false)
        private set
    var storySummaryLoading by mutableStateOf(false)
        private set
    var headerPreviewSuppressed by mutableStateOf(false)
        private set
    var suppressedHeaderReferenceUrl by mutableStateOf<String?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var searchResults by mutableStateOf<List<Comment>>(emptyList())
        private set
    var webViewFullscreen by mutableStateOf(false)
        private set
    var firstVisibleCommentId: Int = 0
        private set
    var firstVisibleCommentOffset: Int = 0
        private set

    private val interactionStore = CommentsInteractionStore(initialShowWebsite, shouldSmoothScroll)
    private var interactionState by mutableStateOf(interactionStore.state)
    private val commentBounds = mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
    private var commentActionSourceBounds: androidx.compose.ui.geometry.Rect? = null
    private var linkPreviewSourceBounds by
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    val sheetSlideOffset: Float get() = interactionState.sheetSlideOffset
    val topInsetPx: Int get() = interactionState.topInsetPx
    val sheetRequest: CommentSheetRequest? get() = interactionState.sheetRequest
    val navigationRequest: CommentNavigationRequest?
        get() = interactionState.navigationRequest
    val showWebsiteRequest: Int get() = interactionState.websiteRequestVersion
    val scrollToCommentRequest: CommentScrollRequest? get() = interactionState.scrollRequest
    val stopScrollRequest: Int get() = interactionState.stopScrollRequestVersion
    val highlightedCommentId: Int get() = interactionState.highlightedCommentId
    val searchScrollTopTargetId: Int get() = interactionState.searchScrollTopTargetId
    val predictiveBackActive: Boolean get() = interactionState.predictiveBackActive
    val predictiveBackProgress: Float get() = interactionState.predictiveBackProgress
    val suppressedCommentIds: Set<Int> get() = interactionState.suppressedCommentIds
    val searchDialogVisible: Boolean get() = interactionState.searchDialogVisible
    val commentActionOverlay: CommentActionOverlayState?
        get() = interactionState.commentAction?.let {
            CommentActionOverlayState(it, commentActionSourceBounds)
        }
    val commentActionDismissRequest: Int
        get() = interactionState.commentActionDismissRequestVersion
    val commentActionPredictiveBackProgress: Float
        get() = interactionState.commentActionPredictiveBackProgress
    val commentActionPredictiveBackEdge: Int
        get() = interactionState.commentActionPredictiveBackEdge
    val commentActionFavoriteLoadingId: Int
        get() = interactionState.commentActionFavoriteLoadingId
    val commentActionVoteLoadingId: Int
        get() = interactionState.commentActionVoteLoadingId
    val commentActionVoteLoadingAction: Int
        get() = interactionState.commentActionVoteLoadingAction
    val commentActionDownvotedIds: Set<Int>
        get() = interactionState.commentActionDownvotedIds
    val linkPreviewOverlay: CommentLinkPreviewOverlayState?
        get() = when (val preview = interactionState.linkPreview) {
            is CommentLinkPreview.Reference -> CommentLinkPreviewOverlayState.Reference(
                originalUrl = preview.originalUrl,
                fallbackTitle = preview.fallbackTitle,
                resolvedTitle = preview.resolvedTitle,
                sourceBounds = linkPreviewSourceBounds,
                sourceCommentId = preview.sourceCommentId,
                headerReference = preview.headerReference,
            )
            is CommentLinkPreview.Image -> CommentLinkPreviewOverlayState.Image(
                imageUrl = preview.imageUrl,
                description = preview.description,
                sourceBounds = linkPreviewSourceBounds,
                backgroundColor = preview.backgroundColor,
            )
            null -> null
        }
    val linkPreviewDismissRequest: Int
        get() = interactionState.linkPreviewDismissRequestVersion
    val linkPreviewPredictiveBackProgress: Float
        get() = interactionState.linkPreviewPredictiveBackProgress
    val linkPreviewPredictiveBackEdge: Int
        get() = interactionState.linkPreviewPredictiveBackEdge
    val linkPreviewPredictiveBackSettleRequest: CommentPredictiveBackSettleRequest?
        get() = interactionState.linkPreviewPredictiveBackSettleRequest
    val linkPreviewVisibleUrl: String?
        get() = interactionState.linkPreviewVisibleUrl

    private fun syncInteractionState() {
        interactionState = interactionStore.state
    }

    fun isBookmarked(itemId: Int): Boolean = savedItemState.isBookmarked(itemId)

    fun isFavorited(itemId: Int): Boolean = savedItemState.isFavorited(itemId)

    fun isUpvoted(itemId: Int, isComment: Boolean): Boolean =
        savedItemState.isUpvoted(itemId, isComment)

    fun updateContent(
        story: Story,
        comments: List<Comment>,
        displaySettings: CommentDisplaySettings,
        commentsLoaded: Boolean,
        commentsRefreshInProgress: Boolean,
        loadingFailed: Boolean,
        loadingFailedServerError: Boolean,
        showUpdate: Boolean,
        lastRefreshed: Long,
        commentsByOpFilterActive: Boolean,
        hasCommentsByOp: Boolean,
        adBlockActive: Boolean,
        integratedWebView: Boolean,
        readerModeAvailable: Boolean,
        readerModeEnabled: Boolean,
        currentSorting: String,
        topInsetPx: Int,
        contentInsetLeftPx: Int,
        contentInsetRightPx: Int,
        storyVoteLoading: Boolean,
        storyFavoriteLoading: Boolean,
        searchQuery: String,
        searchResults: List<Comment>,
        visibleComments: List<VisibleComment>,
    ) {
        this.story = story
        this.comments = comments.toList()
        this.displaySettings = displaySettings
        this.commentsLoaded = commentsLoaded
        this.commentsRefreshInProgress = commentsRefreshInProgress
        this.loadingFailed = loadingFailed
        this.loadingFailedServerError = loadingFailedServerError
        this.showUpdate = showUpdate
        this.lastRefreshed = lastRefreshed
        this.commentsByOpFilterActive = commentsByOpFilterActive
        this.hasCommentsByOp = hasCommentsByOp
        this.adBlockActive = adBlockActive
        this.integratedWebView = integratedWebView
        this.readerModeAvailable = readerModeAvailable
        this.readerModeEnabled = readerModeEnabled
        this.currentSorting = currentSorting
        interactionStore.updateTopInset(topInsetPx)
        this.contentInsetLeftPx = contentInsetLeftPx
        this.contentInsetRightPx = contentInsetRightPx
        this.storyVoteLoading = storyVoteLoading
        this.storyFavoriteLoading = storyFavoriteLoading
        this.searchQuery = searchQuery
        this.searchResults = searchResults.toList()
        this.visibleComments = visibleComments.toList()
        syncInteractionState()
        contentVersion++
    }

    fun updateStorySummaryLoading(loading: Boolean) {
        storySummaryLoading = loading
    }

    fun updateHeaderPreviewSuppressed(suppressed: Boolean) {
        headerPreviewSuppressed = suppressed
    }

    fun refreshContent() {
        comments = comments.toList()
        contentVersion++
    }

    fun updateSheet(slideOffset: Float, topInsetPx: Int) {
        interactionStore.updateSheet(slideOffset, topInsetPx)
        syncInteractionState()
    }

    fun updateStatusBarHeaderColor(color: Color) {
        statusBarHeaderColor = color
    }

    fun updateStatusBarHeaderCoverage(coverage: Float) {
        statusBarHeaderCoverage = coverage.coerceIn(0f, 1f)
    }

    fun requestExpandSheet() {
        interactionStore.requestSheet(expanded = true)
        syncInteractionState()
    }

    fun requestCollapseSheet() {
        interactionStore.requestSheet(expanded = false)
        syncInteractionState()
    }

    fun consumeSheetRequest(request: CommentSheetRequest) {
        interactionStore.consumeSheetRequest(request)
        syncInteractionState()
    }

    fun isSheetExpanded(): Boolean = sheetSlideOffset >= 0.999f

    fun isWebsiteVisible(): Boolean = integratedWebView && sheetSlideOffset <= 0.001f

    fun updateReaderMode(available: Boolean, enabled: Boolean) {
        readerModeAvailable = available
        readerModeEnabled = enabled
    }

    fun updateWebViewFullscreen(fullscreen: Boolean) {
        webViewFullscreen = fullscreen
    }

    fun navigateNext(topLevelOnly: Boolean, scaleLongScrollSpeed: Boolean) {
        interactionStore.navigate(
            forward = true,
            topLevelOnly = topLevelOnly,
            scaleLongScrollSpeed = scaleLongScrollSpeed,
        )
        syncInteractionState()
    }

    fun navigatePrevious(topLevelOnly: Boolean, scaleLongScrollSpeed: Boolean) {
        interactionStore.navigate(
            forward = false,
            topLevelOnly = topLevelOnly,
            scaleLongScrollSpeed = scaleLongScrollSpeed,
        )
        syncInteractionState()
    }

    fun navigateFirst() {
        interactionStore.navigate(
            forward = false,
            topLevelOnly = true,
            scaleLongScrollSpeed = true,
            edge = CommentNavigationEdge.First,
        )
        syncInteractionState()
    }

    fun navigateLast() {
        interactionStore.navigate(
            forward = true,
            topLevelOnly = true,
            scaleLongScrollSpeed = true,
            edge = CommentNavigationEdge.Last,
        )
        syncInteractionState()
    }

    fun requestWebsite() {
        interactionStore.requestWebsite()
        syncInteractionState()
    }

    fun scrollToComment(commentId: Int, topOffsetPx: Int = topInsetPx, animate: Boolean = true) {
        interactionStore.scrollToComment(
            commentId = commentId,
            topOffsetPx = topOffsetPx,
            animate = animate,
            searchResult = false,
        )
        syncInteractionState()
    }

    fun scrollToSearchResult(commentId: Int) {
        interactionStore.scrollToComment(
            commentId = commentId,
            topOffsetPx = topInsetPx,
            animate = true,
            searchResult = true,
        )
        syncInteractionState()
    }

    fun showCommentSearch() {
        interactionStore.showCommentSearch()
        syncInteractionState()
    }

    fun showCommentActions(comment: Comment) {
        // Long presses can arrive from multiple comments before Compose has a chance to render
        // the overlay. Keep the overlay single-owner so a rejected second request cannot leave
        // its source comment suppressed without a corresponding dialog.
        if (!interactionStore.showCommentActions(comment, stopScroll = true)) return
        commentActionSourceBounds = commentBounds[comment.id]
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(true)
    }

    fun restoreCommentActions(comment: Comment) {
        if (!interactionStore.showCommentActions(comment, stopScroll = false)) return
        commentActionSourceBounds = null
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(true)
    }

    fun isCommentActionOverlayShowing(): Boolean = commentActionOverlay != null

    fun getVisibleCommentActionId(): Int = commentActionOverlay?.comment?.id ?: -1

    fun requestDismissCommentActions() {
        interactionStore.requestDismissCommentActions()
        syncInteractionState()
    }

    fun updateCommentActionPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        interactionStore.updateCommentActionPredictiveBack(progress, edge, touchY)
        syncInteractionState()
    }

    fun cancelCommentActionPredictiveBack() {
        interactionStore.cancelCommentActionPredictiveBack()
        syncInteractionState()
    }

    fun isCommentActionPredictiveBackActive(): Boolean =
        commentActionOverlay != null && commentActionPredictiveBackProgress > 0f

    fun commitCommentActionPredictiveBack() {
        interactionStore.commitCommentActionPredictiveBack()
        syncInteractionState()
    }

    fun completeCommentActionDismiss() {
        if (!interactionStore.completeCommentActionDismiss()) return
        commentActionSourceBounds = null
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(false)
    }

    fun setCommentActionFavoriteLoading(commentId: Int, loading: Boolean) {
        interactionStore.setCommentActionFavoriteLoading(commentId, loading)
        syncInteractionState()
        contentVersion++
    }

    fun setCommentActionVoteLoading(commentId: Int, action: Int) {
        interactionStore.setCommentActionVoteLoading(commentId, action)
        syncInteractionState()
        contentVersion++
    }

    fun isCommentActionVoteLoading(commentId: Int): Boolean =
        commentActionVoteLoadingId == commentId

    fun isCommentActionDownvoted(commentId: Int): Boolean =
        commentId in commentActionDownvotedIds

    fun finishCommentActionVote(commentId: Int, downvoted: Boolean) {
        interactionStore.finishCommentActionVote(commentId, downvoted)
        syncInteractionState()
        contentVersion++
    }

    fun refreshCommentActionState() {
        contentVersion++
    }

    fun dismissCommentSearch() {
        interactionStore.dismissCommentSearch()
        syncInteractionState()
        listener.onSearchQueryChanged("")
    }

    fun selectSearchResult(comment: Comment) {
        interactionStore.dismissCommentSearch()
        syncInteractionState()
        listener.onSearchQueryChanged("")
        listener.onSearchResultSelected(comment)
    }

    fun updateSearchQuery(query: String) {
        listener.onSearchQueryChanged(query)
    }

    fun revealSearchResult(commentId: Int, visiblePosition: Int) {
        interactionStore.revealSearchResult(commentId, visiblePosition, showUpdate)
        syncInteractionState()
    }

    fun clearSearchHighlight(commentId: Int) {
        interactionStore.clearSearchHighlight(commentId)
        syncInteractionState()
    }

    fun clearSearchScrollTopTarget() {
        interactionStore.clearSearchScrollTopTarget()
        syncInteractionState()
    }

    fun consumeNavigationRequest(request: CommentNavigationRequest) {
        interactionStore.consumeNavigationRequest(request)
        syncInteractionState()
    }

    fun consumeScrollToCommentRequest(request: CommentScrollRequest) {
        interactionStore.consumeScrollRequest(request)
        syncInteractionState()
    }

    fun updateCommentBounds(commentId: Int, bounds: androidx.compose.ui.geometry.Rect) {
        if (bounds.width > 0f && bounds.height > 0f) commentBounds[commentId] = bounds
    }

    fun removeCommentBounds(commentId: Int) {
        commentBounds.remove(commentId)
    }

    fun commentBoundsFor(commentId: Int): androidx.compose.ui.geometry.Rect? =
        commentBounds[commentId]

    fun showReferencePreview(
        link: CollectedReferenceLinks.ReferenceLink,
        sourceBounds: androidx.compose.ui.geometry.Rect?,
        sourceCommentId: Int = -1,
        headerReference: Boolean = false,
    ) {
        val url = link.url?.takeIf(String::isNotBlank) ?: return
        showReferencePreview(
            url = url,
            title = firstNotBlank(link.resolvedTitle, link.label, url),
            resolvedTitle = link.resolvedTitle,
            sourceBounds = sourceBounds,
            sourceCommentId = sourceCommentId,
            headerReference = headerReference,
        )
    }

    fun showReferencePreview(
        url: String,
        title: String?,
        resolvedTitle: String? = null,
        sourceBounds: androidx.compose.ui.geometry.Rect? = null,
        sourceCommentId: Int = -1,
        headerReference: Boolean = false,
    ) {
        if (!interactionStore.showReferencePreview(
            originalUrl = url,
            fallbackTitle = firstNotBlank(title, url),
            resolvedTitle = resolvedTitle,
            sourceCommentId = sourceCommentId.takeIf { it > 0 },
            headerReference = headerReference,
        )) return
        linkPreviewSourceBounds = sourceBounds
        syncInteractionState()
        listener.onLinkPreviewOverlayVisibilityChanged(true)
    }

    fun showImagePreview(
        imageUrl: String,
        description: String?,
        sourceBounds: androidx.compose.ui.geometry.Rect? = null,
        backgroundColor: Int,
    ) {
        if (!interactionStore.showImagePreview(
            imageUrl = imageUrl,
            description = description.orEmpty(),
            backgroundColor = backgroundColor,
        )) return
        linkPreviewSourceBounds = sourceBounds
        syncInteractionState()
        listener.onLinkPreviewOverlayVisibilityChanged(true)
    }

    fun isLinkPreviewOverlayShowing(): Boolean = interactionState.linkPreview != null

    fun isLinkPreviewReferenceShowing(): Boolean =
        interactionState.linkPreview is CommentLinkPreview.Reference

    fun isLinkPreviewImageShowing(): Boolean =
        interactionState.linkPreview is CommentLinkPreview.Image

    fun getLinkPreviewFallbackTitle(): String? =
        (interactionState.linkPreview as? CommentLinkPreview.Reference)?.fallbackTitle

    fun updateLinkPreviewVisibleUrl(originalUrl: String, resolvedUrl: String?) {
        interactionStore.updateLinkPreviewVisibleUrl(originalUrl, resolvedUrl)
        syncInteractionState()
    }

    fun requestDismissLinkPreview() {
        interactionStore.requestDismissLinkPreview()
        syncInteractionState()
    }

    fun completeLinkPreviewDismiss() {
        if (!interactionStore.completeLinkPreviewDismiss()) return
        linkPreviewSourceBounds = null
        syncInteractionState()
        listener.onLinkPreviewOverlayVisibilityChanged(false)
    }

    fun startLinkPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        interactionStore.updateLinkPreviewPredictiveBack(progress, edge, touchY)
        syncInteractionState()
    }

    fun updateLinkPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) =
        startLinkPreviewPredictiveBack(progress, edge, touchY)

    fun cancelLinkPreviewPredictiveBack() {
        interactionStore.cancelLinkPreviewPredictiveBack()
        syncInteractionState()
    }

    fun isLinkPreviewPredictiveBackActive(): Boolean =
        linkPreviewOverlay != null &&
            (linkPreviewPredictiveBackProgress > 0f ||
                linkPreviewPredictiveBackSettleRequest != null)

    fun commitLinkPreviewPredictiveBack() {
        if (linkPreviewOverlay != null) requestDismissLinkPreview()
    }

    fun finishLinkPreviewPredictiveBackSettle(
        request: CommentPredictiveBackSettleRequest,
    ) {
        interactionStore.finishLinkPreviewPredictiveBackSettle(request)
        syncInteractionState()
    }

    fun requestStopScroll() {
        interactionStore.requestStopScroll()
        syncInteractionState()
    }

    fun beginPredictiveBack(progress: Float) {
        interactionStore.beginPredictiveBack(progress)
        syncInteractionState()
    }

    fun updatePredictiveBack(progress: Float) {
        interactionStore.updatePredictiveBack(progress)
        syncInteractionState()
    }

    fun endPredictiveBack() {
        interactionStore.endPredictiveBack()
        syncInteractionState()
    }

    fun updateScrollPosition(state: LazyListState, visibleComments: List<Comment>) {
        val commentIndex = state.firstVisibleItemIndex - 1
        firstVisibleCommentId = visibleComments.getOrNull(commentIndex)?.id ?: 0
        firstVisibleCommentOffset = state.firstVisibleItemScrollOffset
        listener.onScrollPositionChanged(firstVisibleCommentId, firstVisibleCommentOffset)
    }

    interface Listener {
        fun onToggleComment(comment: Comment, position: Int)
        fun onScrollPositionChanged(commentId: Int, offset: Int) {}
        fun onCommentAction(comment: Comment, action: Int)
        fun onCommentActionOverlayVisibilityChanged(showing: Boolean)
        fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean)
        fun onHeaderClick()
        fun onHeaderPreviewLoaded()
        fun onHeaderPreviewLoadFailed()
        fun onHeaderAction(action: Int)
        fun onShareAction(action: Int)
        fun onMoreAction(action: Int)
        fun onSearchResultSelected(comment: Comment)
        fun onSearchQueryChanged(query: String)
        fun onSortComments(sortType: String)
        fun onSheetAction(action: Int)
        fun onCollapseSheetForWebsite()
        fun onSheetProgressChanged(expandedFraction: Float)
        fun onSheetSettled(expanded: Boolean)
        fun onHeaderColorChanged(color: Int)
        fun onHeaderCoverageChanged(coverage: Float)
        fun onPollOption(optionId: Int)
    }

    companion object {
        const val HEADER_ACTION_USER = 0
        const val HEADER_ACTION_REPLY = 1
        const val HEADER_ACTION_VOTE = 2
        const val HEADER_ACTION_FAVORITE = 3
        const val HEADER_ACTION_BOOKMARK = 4
        const val HEADER_ACTION_SUMMARIZE = 5
        const val HEADER_ACTION_REFRESH = 6

        const val SHARE_ARTICLE = 0
        const val SHARE_ARTICLE_TITLE = 1
        const val SHARE_HN = 2
        const val SHARE_HN_TITLE = 3
        const val SHARE_ALL = 4

        const val MORE_REFRESH = 0
        const val MORE_OPEN_PARENT = 1
        const val MORE_OPEN_TOP_LEVEL = 2
        const val MORE_TOGGLE_BOOKMARK = 3
        const val MORE_SEARCH = 4
        const val MORE_COMMENTS_BY_OP = 5
        const val MORE_OPEN_BROWSER = 6
        const val MORE_DISABLE_ADBLOCK = 7
        const val MORE_ARCHIVE_ORG = 8
        const val MORE_ARCHIVE_IS = 9
        const val MORE_ARCHIVE_TODAY = 10

        const val SHEET_REFRESH = 0
        const val SHEET_EXPAND = 1
        const val SHEET_BROWSER = 2
        const val SHEET_READER = 3
        const val SHEET_INVERT = 4

        const val COMMENT_ACTION_USER = 0
        const val COMMENT_ACTION_SHARE = 1
        const val COMMENT_ACTION_COPY = 2
        const val COMMENT_ACTION_BOOKMARK = 4
        const val COMMENT_ACTION_FAVORITE = 5
        const val COMMENT_ACTION_UPVOTE = 6
        const val COMMENT_ACTION_UNVOTE = 7
        const val COMMENT_ACTION_DOWNVOTE = 8
        const val COMMENT_ACTION_REPLY = 9

        fun create(
            shouldSmoothScroll: () -> Boolean,
            story: Story,
            showWebsite: Boolean,
            accountUser: String?,
            savedItemState: SavedItemStateReader,
            listener: Listener,
        ): CommentsComposeController = CommentsComposeController(
            shouldSmoothScroll = shouldSmoothScroll,
            savedItemState = savedItemState,
            initialStory = story,
            initialShowWebsite = showWebsite,
            accountUser = accountUser,
            listener = listener,
        )

    }
}

data class CommentActionOverlayState(
    val comment: Comment,
    val sourceBounds: androidx.compose.ui.geometry.Rect?,
)

sealed interface CommentLinkPreviewOverlayState {
    val sourceBounds: androidx.compose.ui.geometry.Rect?

    data class Reference(
        val originalUrl: String,
        val fallbackTitle: String,
        val resolvedTitle: String?,
        override val sourceBounds: androidx.compose.ui.geometry.Rect?,
        val sourceCommentId: Int?,
        val headerReference: Boolean,
    ) : CommentLinkPreviewOverlayState

    data class Image(
        val imageUrl: String,
        val description: String,
        override val sourceBounds: androidx.compose.ui.geometry.Rect?,
        val backgroundColor: Int,
    ) : CommentLinkPreviewOverlayState
}

private fun firstNotBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
