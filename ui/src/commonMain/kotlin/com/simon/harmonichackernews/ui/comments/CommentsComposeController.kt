package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.utils.CollectedReferenceLinks

/** One immutable rendering snapshot shared by Android, desktop, and iOS comments screens. */
data class CommentsScreenState(
    val story: StoryListItemSnapshot,
    val accountUser: String? = null,
    val comments: List<PortableCommentItem> = emptyList(),
    val visibleComments: List<PortableVisibleComment> = emptyList(),
    val displaySettings: CommentDisplaySettings? = null,
    val commentsLoaded: Boolean = false,
    val initialThreadCached: Boolean = false,
    val commentsRefreshInProgress: Boolean = false,
    val loadingFailed: Boolean = false,
    val loadingFailedServerError: Boolean = false,
    val usingOfficialApiFallback: Boolean = false,
    val showUpdate: Boolean = false,
    val lastRefreshed: Long = 0L,
    val commentsByOpFilterActive: Boolean = false,
    val hasCommentsByOp: Boolean = false,
    val adBlockActive: Boolean = false,
    val integratedWebView: Boolean = false,
    val readerModeAvailable: Boolean = false,
    val readerModeEnabled: Boolean = false,
    val showSheetControls: Boolean = true,
    val currentSorting: String = "Default",
    val topInsetPx: Int = 0,
    val contentInsetLeftPx: Int = 0,
    val contentInsetRightPx: Int = 0,
    val storyVoteLoading: Boolean = false,
    val storyFavoriteLoading: Boolean = false,
    val pollVoteInFlightOptionId: Int? = null,
    val storySummaryLoading: Boolean = false,
    val summaryDiagnostics: com.simon.harmonichackernews.summary.StorySummaryDiagnostics? = null,
    val headerPreviewResource: StoryPreviewResourceState? = null,
    val commentFavoriteLoadingId: Int = -1,
    val commentVoteLoadingId: Int = -1,
    val commentVoteLoadingAction: CommentMenuAction? = null,
    val downvotedCommentIds: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val searchResults: List<PortableCommentItem> = emptyList(),
)

class CommentsComposeController private constructor(
    shouldSmoothScroll: () -> Boolean,
    private val savedItemState: SavedItemStateReader,
    initialStory: StoryListItemSnapshot,
    initialThreadCached: Boolean,
    val initialShowWebsite: Boolean,
    initialScrollRestorationPending: Boolean,
    accountUser: String?,
    val listener: Listener,
) {
    var screenState by mutableStateOf(
        CommentsScreenState(
            story = initialStory,
            accountUser = accountUser,
            initialThreadCached = initialThreadCached,
        ),
    )
        private set

    /** True only while the current refresh was initiated by the pull-to-refresh gesture. */
    var pullToRefreshInProgress by mutableStateOf(false)
        private set

    val story: StoryListItemSnapshot get() = screenState.story
    val accountUser: String? get() = screenState.accountUser
    val comments: List<PortableCommentItem> get() = screenState.comments
    val visibleComments: List<PortableVisibleComment> get() = screenState.visibleComments
    val displaySettings: CommentDisplaySettings? get() = screenState.displaySettings
    val commentsLoaded: Boolean get() = screenState.commentsLoaded
    val initialThreadCached: Boolean get() = screenState.initialThreadCached
    val commentsRefreshInProgress: Boolean get() = screenState.commentsRefreshInProgress
    val loadingFailed: Boolean get() = screenState.loadingFailed
    val loadingFailedServerError: Boolean get() = screenState.loadingFailedServerError
    val usingOfficialApiFallback: Boolean get() = screenState.usingOfficialApiFallback
    val showUpdate: Boolean get() = screenState.showUpdate
    val lastRefreshed: Long get() = screenState.lastRefreshed
    val commentsByOpFilterActive: Boolean get() = screenState.commentsByOpFilterActive
    val hasCommentsByOp: Boolean get() = screenState.hasCommentsByOp
    val adBlockActive: Boolean get() = screenState.adBlockActive
    val integratedWebView: Boolean get() = screenState.integratedWebView
    val readerModeAvailable: Boolean get() = screenState.readerModeAvailable
    val readerModeEnabled: Boolean get() = screenState.readerModeEnabled
    val showSheetControls: Boolean get() = screenState.showSheetControls
    val currentSorting: String get() = screenState.currentSorting
    val storyVoteLoading: Boolean get() = screenState.storyVoteLoading
    val storyFavoriteLoading: Boolean get() = screenState.storyFavoriteLoading
    val pollVoteInFlightOptionId: Int? get() = screenState.pollVoteInFlightOptionId
    val storySummaryLoading: Boolean get() = screenState.storySummaryLoading
    val summaryDiagnostics: com.simon.harmonichackernews.summary.StorySummaryDiagnostics?
        get() = screenState.summaryDiagnostics
    val headerPreviewResource: StoryPreviewResourceState?
        get() = screenState.headerPreviewResource
    val searchQuery: String get() = screenState.searchQuery
    val searchResults: List<PortableCommentItem> get() = screenState.searchResults
    val contentInsetLeftPx: Int get() = screenState.contentInsetLeftPx
    val contentInsetRightPx: Int get() = screenState.contentInsetRightPx
    var statusBarHeaderColor by mutableStateOf<Color?>(null)
        private set
    var statusBarHeaderCoverage by mutableFloatStateOf(0f)
        private set
    var contentVersion by mutableIntStateOf(0)
        private set
    var headerPreviewSuppressed by mutableStateOf(false)
        private set
    var headerMenuVisible by mutableStateOf(false)
        private set
    var headerMenuDismissRequestVersion by mutableIntStateOf(0)
        private set
    var webViewFullscreen by mutableStateOf(false)
        private set
    var isScrolledToTop by mutableStateOf(true)
        private set
    /** Prevents pane transitions from sampling item 0 before a cached position is restored. */
    var initialScrollRestorationPending by mutableStateOf(initialScrollRestorationPending)
        private set
    var firstVisibleCommentId: Int = 0
        private set
    var firstVisibleCommentOffset: Int = 0
        private set

    private val interactionStore = CommentsInteractionStore(initialShowWebsite, shouldSmoothScroll)
    private var interactionState by mutableStateOf(interactionStore.state)
    private var commentActionSourceBounds: androidx.compose.ui.geometry.Rect? = null
    private var pendingCommentActionSourceGeometry:
        Pair<Int, CommentActionSourceGeometry>? = null
    private var commentActionSourceGeometry: CommentActionSourceGeometry? = null
    private var pendingCommentActionAfterDismiss:
        Pair<PortableCommentItem, CommentMenuAction>? = null
    private var sourceCoveredByCommentActionTransition by mutableStateOf(false)
    private var linkPreviewSourceBounds by
        mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    private var linkPreviewSourceIsReferenceRow by mutableStateOf(false)
    private var linkPreviewSourceContainerColor by mutableStateOf<Color?>(null)
    private var linkPreviewSourceContentLayer by mutableStateOf<GraphicsLayer?>(null)
    private var linkPreviewSourceImageAspectRatio by mutableStateOf<Float?>(null)
    private var linkPreviewSourceCovered by mutableStateOf(false)

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
            CommentActionOverlayState(it, commentActionSourceBounds, commentActionSourceGeometry)
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
    val commentActionVoteLoadingAction: CommentMenuAction?
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
                sourceIsReferenceRow = linkPreviewSourceIsReferenceRow,
                sourceContainerColor = linkPreviewSourceContainerColor,
                sourceContentLayer = linkPreviewSourceContentLayer,
            )
            is CommentLinkPreview.Image -> CommentLinkPreviewOverlayState.Image(
                imageUrl = preview.imageUrl,
                description = preview.description,
                sourceBounds = linkPreviewSourceBounds,
                backgroundColor = preview.backgroundColor,
                sourceContentLayer = linkPreviewSourceContentLayer,
                imageAspectRatio = linkPreviewSourceImageAspectRatio,
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
    val suppressedHeaderReferenceUrl: String?
        get() = (interactionState.linkPreview as? CommentLinkPreview.Reference)
            ?.takeIf {
                it.headerReference && linkPreviewSourceIsReferenceRow &&
                    linkPreviewSourceCovered
            }
            ?.originalUrl

    private fun syncInteractionState() {
        interactionState = interactionStore.state
    }

    fun isBookmarked(itemId: Int): Boolean = savedItemState.isBookmarked(itemId)

    fun isFavorited(itemId: Int): Boolean = savedItemState.isFavorited(itemId)

    fun isUpvoted(itemId: Int, isComment: Boolean): Boolean =
        savedItemState.isUpvoted(itemId, isComment)

    fun updateContent(state: CommentsScreenState) {
        val updateBecameVisible = !screenState.showUpdate && state.showUpdate
        // CommentsScreenStateFactory already exposes immutable presentation snapshots. Copying
        // all three thread lists here made every load and state change allocate and traverse the
        // full thread again on the UI thread.
        screenState = state
        interactionStore.updateTopInset(state.topInsetPx)
        interactionStore.synchronizeCommentActionState(
            favoriteLoadingId = state.commentFavoriteLoadingId,
            voteLoadingId = state.commentVoteLoadingId,
            voteLoadingAction = state.commentVoteLoadingAction,
            downvotedIds = state.downvotedCommentIds,
        )
        if (updateBecameVisible) interactionStore.clearSearchScrollTopTarget()
        syncInteractionState()
        contentVersion++
    }

    fun updateHeaderPreviewSuppressed(suppressed: Boolean) {
        headerPreviewSuppressed = suppressed
    }

    fun updateHeaderMenuVisibility(visible: Boolean) {
        headerMenuVisible = visible
    }

    fun isHeaderMenuShowing(): Boolean = headerMenuVisible

    fun requestDismissHeaderMenu() {
        if (headerMenuVisible) headerMenuDismissRequestVersion++
    }

    fun refreshContent() {
        screenState = screenState.copy(comments = screenState.comments.toList())
        contentVersion++
    }

    fun beginPullToRefresh() {
        pullToRefreshInProgress = true
    }

    fun finishPullToRefresh() {
        pullToRefreshInProgress = false
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
        screenState = screenState.copy(
            readerModeAvailable = available,
            readerModeEnabled = enabled,
        )
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

    fun showCommentActions(
        comment: PortableCommentItem,
        sourceBounds: androidx.compose.ui.geometry.Rect? = null,
    ) {
        // Long presses can arrive from multiple comments before Compose has a chance to render
        // the overlay. Keep the overlay single-owner so a rejected second request cannot leave
        // its source comment suppressed without a corresponding dialog.
        if (!interactionStore.showCommentActions(comment, stopScroll = true)) return
        commentActionSourceBounds = sourceBounds
        commentActionSourceGeometry = pendingCommentActionSourceGeometry
            ?.takeIf { (commentId, geometry) ->
                commentId == comment.id &&
                    sourceBounds != null &&
                    geometry.container == sourceBounds
            }
            ?.second
        pendingCommentActionSourceGeometry = null
        sourceCoveredByCommentActionTransition = false
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(true)
    }

    fun restoreCommentActions(comment: PortableCommentItem) {
        if (!interactionStore.showCommentActions(comment, stopScroll = false)) return
        commentActionSourceBounds = null
        commentActionSourceGeometry = null
        sourceCoveredByCommentActionTransition = false
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(true)
    }

    fun isCommentActionOverlayShowing(): Boolean = commentActionOverlay != null

    fun getVisibleCommentActionId(): Int = commentActionOverlay?.comment?.id ?: -1

    fun requestDismissCommentActions() {
        interactionStore.requestDismissCommentActions()
        syncInteractionState()
    }

    fun dismissCommentActionsThen(
        comment: PortableCommentItem,
        action: CommentMenuAction,
    ) {
        if (commentActionOverlay == null || pendingCommentActionAfterDismiss != null) return
        pendingCommentActionAfterDismiss = comment to action
        requestDismissCommentActions()
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

    fun completeCommentActionDismiss(dispatchPendingAction: Boolean = true) {
        if (!interactionStore.completeCommentActionDismiss()) return
        val pendingAction = pendingCommentActionAfterDismiss
        pendingCommentActionAfterDismiss = null
        commentActionSourceBounds = null
        commentActionSourceGeometry = null
        sourceCoveredByCommentActionTransition = false
        syncInteractionState()
        listener.onCommentActionOverlayVisibilityChanged(false)
        if (dispatchPendingAction) {
            pendingAction?.let { (comment, action) ->
                listener.onCommentAction(comment, action)
            }
        }
    }

    fun updateCommentActionSourceGeometry(
        commentId: Int,
        geometry: CommentActionSourceGeometry,
    ) {
        if (geometry.container.width <= 0f || geometry.container.height <= 0f) return
        pendingCommentActionSourceGeometry = commentId to geometry
    }

    /** Keeps the real comment visible until a progress-zero overlay covers the same pixels. */
    fun shouldKeepCommentActionSourceVisible(commentId: Int): Boolean =
        commentActionOverlay?.comment?.id == commentId &&
            !sourceCoveredByCommentActionTransition

    fun setCommentActionSourceCovered(covered: Boolean) {
        sourceCoveredByCommentActionTransition = covered
    }

    fun setCommentActionFavoriteLoading(commentId: Int, loading: Boolean) {
        interactionStore.setCommentActionFavoriteLoading(commentId, loading)
        syncInteractionState()
        contentVersion++
    }

    fun setCommentActionVoteLoading(commentId: Int, action: CommentMenuAction) {
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

    fun selectSearchResult(comment: PortableCommentItem) {
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

    private fun updateLinkPreviewSource(
        sourceBounds: Rect?,
        sourceIsReferenceRow: Boolean,
        sourceContainerColor: Color?,
        sourceContentLayer: GraphicsLayer?,
        sourceImageAspectRatio: Float?,
    ) {
        linkPreviewSourceBounds = sourceBounds
        linkPreviewSourceIsReferenceRow = sourceIsReferenceRow
        linkPreviewSourceContainerColor = sourceContainerColor
        linkPreviewSourceContentLayer = sourceContentLayer
        linkPreviewSourceImageAspectRatio = sourceImageAspectRatio
        linkPreviewSourceCovered = false
        headerPreviewSuppressed = false
    }

    fun showReferencePreview(
        link: CollectedReferenceLinks.ReferenceLink,
        sourceBounds: androidx.compose.ui.geometry.Rect?,
        sourceCommentId: Int = -1,
        headerReference: Boolean = false,
        sourceContainerColor: Color? = null,
        sourceContentLayer: GraphicsLayer? = null,
    ) {
        val url = link.url?.takeIf(String::isNotBlank) ?: return
        showReferencePreview(
            url = url,
            title = firstNotBlank(link.resolvedTitle, link.label, url),
            resolvedTitle = link.resolvedTitle,
            sourceBounds = sourceBounds,
            sourceCommentId = sourceCommentId,
            headerReference = headerReference,
            sourceIsReferenceRow = true,
            sourceContainerColor = sourceContainerColor,
            sourceContentLayer = sourceContentLayer,
        )
    }

    fun showReferencePreview(
        url: String,
        title: String?,
        resolvedTitle: String? = null,
        sourceBounds: androidx.compose.ui.geometry.Rect? = null,
        sourceCommentId: Int = -1,
        headerReference: Boolean = false,
        sourceIsReferenceRow: Boolean = false,
        sourceContainerColor: Color? = null,
        sourceContentLayer: GraphicsLayer? = null,
    ) {
        if (!interactionStore.showReferencePreview(
            originalUrl = url,
            fallbackTitle = firstNotBlank(title, url),
            resolvedTitle = resolvedTitle,
            sourceCommentId = sourceCommentId.takeIf { it > 0 },
            headerReference = headerReference,
        )) return
        updateLinkPreviewSource(
            sourceBounds = sourceBounds,
            sourceIsReferenceRow = sourceIsReferenceRow,
            sourceContainerColor = sourceContainerColor,
            sourceContentLayer = sourceContentLayer,
            sourceImageAspectRatio = null,
        )
        syncInteractionState()
        listener.onLinkPreviewOverlayVisibilityChanged(true)
    }

    fun showImagePreview(
        imageUrl: String,
        description: String?,
        sourceBounds: androidx.compose.ui.geometry.Rect? = null,
        backgroundColor: Int,
        sourceContentLayer: GraphicsLayer? = null,
        imageAspectRatio: Float? = null,
    ) {
        if (!interactionStore.showImagePreview(
            imageUrl = imageUrl,
            description = description.orEmpty(),
            backgroundColor = backgroundColor,
        )) return
        val validImageAspectRatio = imageAspectRatio?.takeIf { it.isFinite() && it > 0f }
        updateLinkPreviewSource(
            sourceBounds = sourceBounds,
            sourceIsReferenceRow = false,
            sourceContainerColor = null,
            sourceContentLayer = sourceContentLayer,
            sourceImageAspectRatio = validImageAspectRatio,
        )
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

    fun coverLinkPreviewReferenceSource() {
        if (!linkPreviewSourceIsReferenceRow) return
        coverLinkPreviewSource()
    }

    fun coverLinkPreviewSource() {
        if (linkPreviewSourceCovered) return
        when {
            linkPreviewSourceIsReferenceRow -> Unit
            interactionState.linkPreview is CommentLinkPreview.Image &&
                linkPreviewSourceContentLayer != null -> headerPreviewSuppressed = true
            else -> return
        }
        linkPreviewSourceCovered = true
    }

    fun suppressedReferenceUrlForComment(commentId: Int): String? =
        (interactionState.linkPreview as? CommentLinkPreview.Reference)
            ?.takeIf {
                it.sourceCommentId == commentId && linkPreviewSourceIsReferenceRow &&
                    linkPreviewSourceCovered
            }
            ?.originalUrl

    fun completeLinkPreviewDismiss() {
        if (!interactionStore.completeLinkPreviewDismiss()) return
        updateLinkPreviewSource(
            sourceBounds = null,
            sourceIsReferenceRow = false,
            sourceContainerColor = null,
            sourceContentLayer = null,
            sourceImageAspectRatio = null,
        )
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

    fun updateScrollPosition(state: LazyListState, visibleComments: List<PortableVisibleComment>) {
        val commentIndex = state.firstVisibleItemIndex - 1
        firstVisibleCommentId = visibleComments.getOrNull(commentIndex)?.comment?.id ?: 0
        firstVisibleCommentOffset = state.firstVisibleItemScrollOffset
        isScrolledToTop = state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        listener.onScrollPositionChanged(firstVisibleCommentId, firstVisibleCommentOffset)
    }

    fun completeInitialScrollRestoration() {
        initialScrollRestorationPending = false
    }

    interface Listener {
        fun onToggleComment(comment: PortableCommentItem, position: Int)
        fun onScrollPositionChanged(commentId: Int, offset: Int) {}
        fun onCommentAction(comment: PortableCommentItem, action: CommentMenuAction)
        fun onCommentActionOverlayVisibilityChanged(showing: Boolean)
        fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean)
        fun onHeaderClick()
        fun onHeaderPreviewImageResult(imageUrl: String, success: Boolean)
        fun onHeaderPreviewTintExtracted(
            sourceUrl: String,
            baseColorArgb: Int,
            paletteConfigKey: String,
            tintColorArgb: Int,
        ): Int?
        fun onHeaderAction(action: CommentsHeaderAction)
        fun onShareAction(action: CommentsShareAction)
        fun onMoreAction(action: CommentsMoreAction)
        fun onSearchResultSelected(comment: PortableCommentItem)
        fun onSearchQueryChanged(query: String)
        fun onSortComments(sortType: String)
        fun onSheetAction(action: CommentsSheetAction)
        fun onCollapseSheetForWebsite()
        fun onSheetProgressChanged(expandedFraction: Float)
        fun onSheetSettled(expanded: Boolean)
        fun onHeaderColorChanged(color: Int)
        fun onHeaderCoverageChanged(coverage: Float)
        fun onPollOption(optionId: Int)
    }

    companion object {
        fun create(
            shouldSmoothScroll: () -> Boolean,
            story: StoryListItemSnapshot,
            initialThreadCached: Boolean = false,
            showWebsite: Boolean,
            initialScrollRestorationPending: Boolean = false,
            accountUser: String?,
            savedItemState: SavedItemStateReader,
            listener: Listener,
        ): CommentsComposeController = CommentsComposeController(
            shouldSmoothScroll = shouldSmoothScroll,
            savedItemState = savedItemState,
            initialStory = story,
            initialThreadCached = initialThreadCached,
            initialShowWebsite = showWebsite,
            initialScrollRestorationPending = initialScrollRestorationPending,
            accountUser = accountUser,
            listener = listener,
        )

    }
}

data class CommentActionOverlayState(
    val comment: PortableCommentItem,
    val sourceBounds: androidx.compose.ui.geometry.Rect?,
    val sourceGeometry: CommentActionSourceGeometry? = null,
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
        val sourceIsReferenceRow: Boolean,
        val sourceContainerColor: Color?,
        val sourceContentLayer: GraphicsLayer?,
    ) : CommentLinkPreviewOverlayState

    data class Image(
        val imageUrl: String,
        val description: String,
        override val sourceBounds: androidx.compose.ui.geometry.Rect?,
        val backgroundColor: Int,
        val sourceContentLayer: GraphicsLayer?,
        val imageAspectRatio: Float?,
    ) : CommentLinkPreviewOverlayState
}

private fun firstNotBlank(vararg values: String?): String =
    values.firstOrNull { !it.isNullOrBlank() }.orEmpty()
