package com.simon.harmonichackernews.ui.comments

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.Html
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.core.graphics.withTranslation
import coil.compose.AsyncImage
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PollOption
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Compose state bridge for [com.simon.harmonichackernews.CommentsFragment]. Networking, cache
 * parsing and the WebView lifecycle intentionally remain in the fragment during this incremental
 * migration. The visible comments surface is a Compose tree layered into the existing bottom-sheet
 * host; immutable list snapshots turn legacy adapter notifications into normal Compose updates.
 */
class CommentsComposeController private constructor(
    private val activity: AppCompatActivity,
    initialStory: Story,
    internal val accountUser: String?,
    internal val listener: Listener,
) {
    internal var story by mutableStateOf(initialStory)
        private set
    internal var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    internal var displaySettings by mutableStateOf<CommentDisplaySettings?>(null)
        private set
    internal var commentsLoaded by mutableStateOf(false)
        private set
    internal var commentsRefreshInProgress by mutableStateOf(false)
        private set
    internal var loadingFailed by mutableStateOf(false)
        private set
    internal var loadingFailedServerError by mutableStateOf(false)
        private set
    internal var showUpdate by mutableStateOf(false)
        private set
    internal var commentsByOpFilterActive by mutableStateOf(false)
        private set
    internal var hasCommentsByOp by mutableStateOf(false)
        private set
    internal var adBlockActive by mutableStateOf(false)
        private set
    internal var integratedWebView by mutableStateOf(false)
        private set
    internal var readerModeAvailable by mutableStateOf(false)
        private set
    internal var readerModeEnabled by mutableStateOf(false)
        private set
    internal var sheetSlideOffset by mutableFloatStateOf(1f)
        private set
    internal var topInsetPx by mutableIntStateOf(0)
        private set
    internal var contentInsetLeftPx by mutableIntStateOf(0)
        private set
    internal var contentInsetRightPx by mutableIntStateOf(0)
        private set
    internal var contentVersion by mutableIntStateOf(0)
        private set
    internal var currentSorting by mutableStateOf("Default")
        private set
    internal var navigationRequest by mutableStateOf<NavigationRequest?>(null)
        private set
    internal var showWebsiteRequest by mutableIntStateOf(0)
        private set
    internal var scrollToCommentRequest by mutableStateOf<ScrollToCommentRequest?>(null)
        private set
    internal var stopScrollRequest by mutableIntStateOf(0)
        private set
    internal var highlightedCommentId by mutableIntStateOf(-1)
        private set
    internal var searchScrollTopTargetId by mutableIntStateOf(-1)
        private set
    internal var predictiveBackActive by mutableStateOf(false)
        private set
    internal var predictiveBackProgress by mutableFloatStateOf(0f)
        private set
    internal var storyVoteLoading by mutableStateOf(false)
        private set
    internal var storyFavoriteLoading by mutableStateOf(false)
        private set
    internal var storySummaryLoading by mutableStateOf(false)
        private set
    internal var suppressedCommentIds by mutableStateOf<Set<Int>>(emptySet())
        private set
    internal var headerPreviewSuppressed by mutableStateOf(false)
        private set

    var firstVisibleCommentId: Int = 0
        private set
    var firstVisibleCommentOffset: Int = 0
        private set

    private var requestSerial = 0
    private var composeView: ComposeView? = null
    private val commentBounds = mutableMapOf<Int, androidx.compose.ui.geometry.Rect>()
    private val transitionSources = mutableSetOf<View>()

    fun updateContent(
        story: Story,
        comments: List<Comment>,
        displaySettings: CommentDisplaySettings,
        commentsLoaded: Boolean,
        commentsRefreshInProgress: Boolean,
        loadingFailed: Boolean,
        loadingFailedServerError: Boolean,
        showUpdate: Boolean,
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
    ) {
        this.story = story
        this.comments = comments.toList()
        this.displaySettings = displaySettings
        this.commentsLoaded = commentsLoaded
        this.commentsRefreshInProgress = commentsRefreshInProgress
        this.loadingFailed = loadingFailed
        this.loadingFailedServerError = loadingFailedServerError
        this.showUpdate = showUpdate
        this.commentsByOpFilterActive = commentsByOpFilterActive
        this.hasCommentsByOp = hasCommentsByOp
        this.adBlockActive = adBlockActive
        this.integratedWebView = integratedWebView
        this.readerModeAvailable = readerModeAvailable
        this.readerModeEnabled = readerModeEnabled
        this.currentSorting = currentSorting
        this.topInsetPx = topInsetPx
        this.contentInsetLeftPx = contentInsetLeftPx
        this.contentInsetRightPx = contentInsetRightPx
        this.storyVoteLoading = storyVoteLoading
        this.storyFavoriteLoading = storyFavoriteLoading
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
        sheetSlideOffset = slideOffset.coerceIn(0f, 1f)
        this.topInsetPx = topInsetPx
    }

    fun updateReaderMode(available: Boolean, enabled: Boolean) {
        readerModeAvailable = available
        readerModeEnabled = enabled
    }

    fun navigateNext(topLevelOnly: Boolean, scaleLongScrollSpeed: Boolean) {
        navigationRequest = NavigationRequest(
            serial = ++requestSerial,
            forward = true,
            topLevelOnly = topLevelOnly,
            animate = SettingsUtils.shouldSmoothScrollComments(activity),
            scaleLongScrollSpeed = scaleLongScrollSpeed,
        )
    }

    fun navigatePrevious(topLevelOnly: Boolean, scaleLongScrollSpeed: Boolean) {
        navigationRequest = NavigationRequest(
            serial = ++requestSerial,
            forward = false,
            topLevelOnly = topLevelOnly,
            animate = SettingsUtils.shouldSmoothScrollComments(activity),
            scaleLongScrollSpeed = scaleLongScrollSpeed,
        )
    }

    internal fun navigateFirst() {
        navigationRequest = NavigationRequest(
            serial = ++requestSerial,
            forward = false,
            topLevelOnly = true,
            animate = SettingsUtils.shouldSmoothScrollComments(activity),
            scaleLongScrollSpeed = true,
            edge = NavigationEdge.First,
        )
    }

    internal fun navigateLast() {
        navigationRequest = NavigationRequest(
            serial = ++requestSerial,
            forward = true,
            topLevelOnly = true,
            animate = SettingsUtils.shouldSmoothScrollComments(activity),
            scaleLongScrollSpeed = true,
            edge = NavigationEdge.Last,
        )
    }

    fun requestWebsite() {
        showWebsiteRequest++
    }

    @JvmOverloads
    fun scrollToComment(commentId: Int, topOffsetPx: Int = topInsetPx, animate: Boolean = true) {
        scrollToCommentRequest = ScrollToCommentRequest(
            serial = ++requestSerial,
            commentId = commentId,
            topOffsetPx = topOffsetPx,
            animate = animate && SettingsUtils.shouldSmoothScrollComments(activity),
            searchResult = false,
        )
    }

    fun scrollToSearchResult(commentId: Int) {
        scrollToCommentRequest = ScrollToCommentRequest(
            serial = ++requestSerial,
            commentId = commentId,
            topOffsetPx = topInsetPx,
            animate = SettingsUtils.shouldSmoothScrollComments(activity),
            searchResult = true,
        )
    }

    internal fun revealSearchResult(commentId: Int, visiblePosition: Int) {
        highlightedCommentId = commentId
        searchScrollTopTargetId = commentId.takeIf { visiblePosition > 10 && !showUpdate } ?: -1
    }

    internal fun clearSearchHighlight(commentId: Int) {
        if (highlightedCommentId == commentId) highlightedCommentId = -1
    }

    internal fun clearSearchScrollTopTarget() {
        searchScrollTopTargetId = -1
    }

    internal fun consumeNavigationRequest(request: NavigationRequest) {
        if (navigationRequest == request) navigationRequest = null
    }

    internal fun consumeScrollToCommentRequest(request: ScrollToCommentRequest) {
        if (scrollToCommentRequest == request) scrollToCommentRequest = null
    }

    internal fun updateCommentBounds(commentId: Int, bounds: androidx.compose.ui.geometry.Rect) {
        if (bounds.width > 0f && bounds.height > 0f) commentBounds[commentId] = bounds
    }

    internal fun removeCommentBounds(commentId: Int) {
        commentBounds.remove(commentId)
    }

    fun createCommentTransitionSource(commentId: Int): View? =
        commentBounds[commentId]?.let { bounds ->
            createTransitionSource(bounds) { visible ->
                suppressedCommentIds = if (visible) {
                    suppressedCommentIds - commentId
                } else {
                    suppressedCommentIds + commentId
                }
            }
        }

    internal fun createHeaderPreviewTransitionSource(
        bounds: androidx.compose.ui.geometry.Rect,
    ): View? = createTransitionSource(bounds) { visible ->
        headerPreviewSuppressed = !visible
    }

    internal fun createTransitionSource(
        bounds: androidx.compose.ui.geometry.Rect,
        onVisibilityChanged: ((Boolean) -> Unit)? = null,
    ): View? {
        val source = composeView ?: return null
        val host = activity.findViewById<View>(android.R.id.content) as? ViewGroup ?: return null
        val hostLocation = IntArray(2)
        host.getLocationInWindow(hostLocation)
        val left = floor(bounds.left).toInt()
        val top = floor(bounds.top).toInt()
        val right = ceil(bounds.right).toInt()
        val bottom = ceil(bounds.bottom).toInt()
        if (right <= left || bottom <= top) return null

        val proxy = ComposeCropTransitionView(
            context = activity,
            source = source,
            cropLeftInWindow = left,
            cropTopInWindow = top,
            onVisibilityChanged = onVisibilityChanged,
        )
        host.addView(proxy, ViewGroup.LayoutParams(right - left, bottom - top))
        proxy.x = (left - hostLocation[0]).toFloat()
        proxy.y = (top - hostLocation[1]).toFloat()
        transitionSources += proxy
        return proxy
    }

    fun clearTransitionSources() {
        transitionSources.toList().forEach { source ->
            source.visibility = View.VISIBLE
            (source.parent as? ViewGroup)?.removeView(source)
        }
        transitionSources.clear()
        suppressedCommentIds = emptySet()
        headerPreviewSuppressed = false
    }

    fun requestStopScroll() {
        stopScrollRequest++
    }

    fun beginPredictiveBack(progress: Float) {
        predictiveBackActive = true
        predictiveBackProgress = progress.coerceIn(0f, 1f)
    }

    fun updatePredictiveBack(progress: Float) {
        predictiveBackProgress = progress.coerceIn(0f, 1f)
    }

    fun endPredictiveBack() {
        predictiveBackActive = false
        predictiveBackProgress = 0f
    }

    internal fun updateScrollPosition(state: LazyListState, visibleComments: List<Comment>) {
        val commentIndex = state.firstVisibleItemIndex - 1
        firstVisibleCommentId = visibleComments.getOrNull(commentIndex)?.id ?: 0
        firstVisibleCommentOffset = state.firstVisibleItemScrollOffset
    }

    internal data class NavigationRequest(
        val serial: Int,
        val forward: Boolean,
        val topLevelOnly: Boolean,
        val animate: Boolean,
        val scaleLongScrollSpeed: Boolean,
        val edge: NavigationEdge? = null,
    )

    internal enum class NavigationEdge { First, Last }

    internal data class ScrollToCommentRequest(
        val serial: Int,
        val commentId: Int,
        val topOffsetPx: Int,
        val animate: Boolean,
        val searchResult: Boolean,
    )

    interface Listener {
        fun onToggleComment(comment: Comment, position: Int)
        fun onShowCommentActions(comment: Comment, source: View)
        fun onReferenceLongClick(
            link: CollectedReferenceLinks.ReferenceLink,
            source: View,
        )
        fun onHeaderClick()
        fun onHeaderPreviewLongClick(source: View)
        fun onHeaderAction(action: Int)
        fun onShareAction(action: Int)
        fun onMoreAction(action: Int)
        fun onSortComments(sortType: String)
        fun onSheetAction(action: Int)
        fun onCollapseSheetForWebsite()
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
        const val MORE_ARCHIVE = 8

        const val SHEET_REFRESH = 0
        const val SHEET_EXPAND = 1
        const val SHEET_BROWSER = 2
        const val SHEET_READER = 3
        const val SHEET_INVERT = 4

        @JvmStatic
        fun install(
            fragment: Fragment,
            contentHost: FrameLayout,
            story: Story,
            accountUser: String?,
            listener: Listener,
        ): CommentsComposeController {
            val activity = fragment.requireActivity() as AppCompatActivity
            val controller = CommentsComposeController(
                activity = activity,
                initialStory = story,
                accountUser = accountUser,
                listener = listener,
            )
            val composeView = ComposeView(activity).apply {
                id = View.generateViewId()
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                setContent {
                    HarmonicTheme {
                        CommentsScreen(controller)
                    }
                }
            }
            controller.composeView = composeView
            val boundedHost = CommentsComposeHost(activity).apply {
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            contentHost.addView(
                boundedHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            return controller
        }

        @JvmStatic
        fun installEmpty(fragment: Fragment, contentHost: ViewGroup) {
            val context = fragment.requireContext()
            contentHost.removeAllViews()
            val composeView = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    HarmonicTheme { EmptyCommentsScreen() }
                }
            }
            contentHost.addView(
                CommentsComposeHost(context).apply {
                    addView(
                        composeView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
}

/** Keeps Compose bounded during speculative weight and BottomSheet measure passes. */
private class CommentsComposeHost(context: android.content.Context) : FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val displayMetrics = resources.displayMetrics
        super.onMeasure(
            boundedSpec(widthMeasureSpec, displayMetrics.widthPixels),
            boundedSpec(heightMeasureSpec, displayMetrics.heightPixels),
        )
    }

    private fun boundedSpec(measureSpec: Int, displaySize: Int): Int {
        val mode = MeasureSpec.getMode(measureSpec)
        val size = MeasureSpec.getSize(measureSpec)
        val safeDisplaySize = displaySize.coerceAtLeast(1)
        return if (mode == MeasureSpec.UNSPECIFIED || size > safeDisplaySize * 2) {
            MeasureSpec.makeMeasureSpec(safeDisplaySize, MeasureSpec.EXACTLY)
        } else {
            measureSpec
        }
    }
}

@SuppressLint("ViewConstructor")
private class ComposeCropTransitionView(
    context: android.content.Context,
    private val source: View,
    private val cropLeftInWindow: Int,
    private val cropTopInWindow: Int,
    private val onVisibilityChanged: ((Boolean) -> Unit)?,
) : View(context) {
    private val sourceLocation = IntArray(2)

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        source.getLocationInWindow(sourceLocation)
        canvas.withTranslation(
            (sourceLocation[0] - cropLeftInWindow).toFloat(),
            (sourceLocation[1] - cropTopInWindow).toFloat(),
        ) {
            source.draw(this)
        }
    }

    override fun setVisibility(visibility: Int) {
        val changed = visibility != this.visibility
        super.setVisibility(visibility)
        if (changed) {
            onVisibilityChanged?.invoke(visibility == VISIBLE)
        }
    }
}

private data class VisibleComment(
    val sourceIndex: Int,
    val comment: Comment,
    val hiddenReplyCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentsScreen(controller: CommentsComposeController) {
    val context = LocalContext.current
    val settings = controller.displaySettings
    if (settings == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HarmonicTheme.colors.background),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(Modifier.size(42.dp))
        }
        return
    }

    val listState = rememberLazyListState()
    val sourceComments = controller.comments
    val visibleComments = remember(sourceComments, controller.contentVersion) {
        buildVisibleComments(sourceComments)
    }
    val sourceView = LocalView.current
    val nestedScrollInterop = rememberNestedScrollInteropConnection()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = navigationBottom + if (settings.showNavigationBar) 88.dp else 16.dp
    val animateComments = SettingsUtils.shouldUseCommentsAnimation(context)
    val showScrollbar = SettingsUtils.shouldUseCommentsScrollbar(context)
    val density = LocalDensity.current
    val contentInsetStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val contentInsetEnd = with(density) { controller.contentInsetRightPx.toDp() }

    LaunchedEffect(listState, visibleComments) {
        snapshotFlow {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
            val coverage = if (header == null || controller.topInsetPx <= 0) {
                0f
            } else {
                val overlap = minOf(header.offset + header.size, controller.topInsetPx) -
                    maxOf(header.offset, 0)
                (overlap.toFloat() / controller.topInsetPx).coerceIn(0f, 1f)
            }
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                coverage,
            )
        }.distinctUntilChanged().collect { (_, _, coverage) ->
            controller.updateScrollPosition(listState, visibleComments.map { item -> item.comment })
            controller.listener.onHeaderCoverageChanged(coverage)
        }
    }

    val navigationRequest = controller.navigationRequest
    LaunchedEffect(navigationRequest, visibleComments) {
        val request = navigationRequest ?: return@LaunchedEffect
        val target = when (request.edge) {
            CommentsComposeController.NavigationEdge.First -> -1
            CommentsComposeController.NavigationEdge.Last ->
                visibleComments.indexOfLast { it.comment.depth == 0 }.coerceAtLeast(0)
            null -> findNavigationTarget(
                state = listState,
                comments = visibleComments,
                forward = request.forward,
                topLevelOnly = request.topLevelOnly,
            )
        }
        val listIndex = target + 1
        val scrollOffset = if (listIndex == 0) 0 else -controller.topInsetPx
        if (request.animate) {
            listState.animateScrollToItem(listIndex, scrollOffset)
        } else {
            listState.scrollToItem(listIndex, scrollOffset)
        }
        controller.consumeNavigationRequest(request)
    }

    val websiteRequest = controller.showWebsiteRequest
    LaunchedEffect(websiteRequest) {
        if (websiteRequest > 0) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
            controller.listener.onCollapseSheetForWebsite()
        }
    }

    val stopScrollRequest = controller.stopScrollRequest
    LaunchedEffect(stopScrollRequest) {
        if (stopScrollRequest > 0) listState.stopScroll()
    }

    val scrollToCommentRequest = controller.scrollToCommentRequest
    LaunchedEffect(scrollToCommentRequest, visibleComments) {
        val request = scrollToCommentRequest ?: return@LaunchedEffect
        val listIndex = if (request.commentId == 0) {
            0
        } else {
            visibleComments.indexOfFirst { it.comment.id == request.commentId }
                .takeIf { it >= 0 }
                ?.plus(1)
        }
        if (listIndex != null) {
            val scrollOffset = -request.topOffsetPx
            if (request.animate) {
                listState.animateScrollToItem(listIndex, scrollOffset)
            } else {
                listState.scrollToItem(listIndex, scrollOffset)
            }
            if (request.searchResult) {
                controller.revealSearchResult(request.commentId, listIndex)
            }
        }
        controller.consumeScrollToCommentRequest(request)
    }

    val highlightedCommentId = controller.highlightedCommentId
    LaunchedEffect(highlightedCommentId) {
        if (highlightedCommentId > 0) {
            delay(1_200)
            controller.clearSearchHighlight(highlightedCommentId)
        }
    }

    val searchScrollTopTargetId = controller.searchScrollTopTargetId
    val searchTargetVisible by remember(searchScrollTopTargetId, listState) {
        derivedStateOf {
            searchScrollTopTargetId > 0 &&
                listState.layoutInfo.visibleItemsInfo.any { it.key == searchScrollTopTargetId }
        }
    }
    LaunchedEffect(searchScrollTopTargetId, listState) {
        if (searchScrollTopTargetId <= 0) return@LaunchedEffect
        var wasVisible = false
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.key == searchScrollTopTargetId }
        }.distinctUntilChanged().collect { visible ->
            if (visible) {
                wasVisible = true
            } else if (wasVisible) {
                controller.clearSearchScrollTopTarget()
            }
        }
    }

    val list: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(HarmonicTheme.colors.background)
                .nestedScroll(nestedScrollInterop),
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "header", contentType = "header") {
                CommentsHeader(
                    controller = controller,
                    settings = settings,
                )
            }

            itemsIndexed(
                items = visibleComments,
                key = { _, item -> item.comment.id },
                contentType = { _, _ -> if (settings.cardStyle) "comment-card" else "comment" },
            ) { _, item ->
                DisposableEffect(item.comment.id) {
                    onDispose { controller.removeCommentBounds(item.comment.id) }
                }
                val userTags = remember(controller.contentVersion) { Utils.getUserTags(context) }
                val tag = item.comment.by?.lowercase()?.trim()?.let(userTags::get)
                CommentItem(
                    comment = item.comment,
                    style = CommentItemStyle(
                        cardStyle = settings.cardStyle,
                        showCardBorder = settings.cardBorder,
                        textSize = settings.preferredTextSize,
                        collectLinks = settings.collectReferenceLinks,
                        emphasizeMeta = settings.highlightCommentMeta,
                        depthIndicatorMode = settings.commentDepthIndicatorMode,
                        showDivider = settings.showDividers,
                        preferredFont = settings.font,
                        animateChanges = animateComments,
                    ),
                    storyAuthor = controller.story.by,
                    accountUser = controller.accountUser,
                    userTag = tag,
                    hiddenReplyCount = item.hiddenReplyCount,
                    collapseParent = settings.collapseParent,
                    showTopLevelIndicator = settings.showTopLevelDepthIndicator,
                    highlighted = item.comment.id == controller.highlightedCommentId,
                    modifier = Modifier
                        .padding(start = contentInsetStart, end = contentInsetEnd)
                        .graphicsLayer(
                            alpha = if (item.comment.id in controller.suppressedCommentIds) 0f else 1f,
                        )
                        .onGloballyPositioned { coordinates ->
                            controller.updateCommentBounds(
                                item.comment.id,
                                coordinates.boundsInWindow(),
                            )
                        }
                        .then(if (animateComments) Modifier.animateItem() else Modifier),
                    onToggleExpanded = {
                        if (settings.swapLongPressTap) {
                            controller.listener.onShowCommentActions(
                                item.comment,
                                controller.createCommentTransitionSource(item.comment.id) ?: sourceView,
                            )
                        } else {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        }
                    },
                    onShowActions = {
                        if (settings.swapLongPressTap) {
                            controller.listener.onToggleComment(item.comment, item.sourceIndex)
                        } else {
                            controller.listener.onShowCommentActions(
                                item.comment,
                                controller.createCommentTransitionSource(item.comment.id) ?: sourceView,
                            )
                        }
                    },
                    onReferenceLongClick = { link ->
                        controller.listener.onReferenceLongClick(
                            link,
                            controller.createCommentTransitionSource(item.comment.id) ?: sourceView,
                        )
                    },
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (controller.integratedWebView) {
            list()
        } else {
            PullToRefreshBox(
                isRefreshing = controller.commentsRefreshInProgress,
                onRefresh = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                modifier = Modifier.fillMaxSize(),
            ) {
                list()
            }
        }

        AnimatedVisibility(
            visible = settings.showNavigationBar && visibleComments.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navigationBottom + 16.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CommentNavigationButtons(
                onPrevious = { controller.navigatePrevious(true, false) },
                onNext = { controller.navigateNext(true, false) },
                onFirst = {
                    controller.navigationRequest?.let(controller::consumeNavigationRequest)
                    controller.navigateFirst()
                },
                onLast = controller::navigateLast,
            )
        }

        if (showScrollbar && controller.sheetSlideOffset >= 0.999f) {
            CommentsScrollbar(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = searchTargetVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = navigationBottom + if (settings.showNavigationBar) 88.dp else 16.dp,
                ),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (SettingsUtils.shouldSmoothScrollComments(context)) {
                        controller.scrollToComment(0)
                    } else {
                        controller.scrollToComment(0, 0, false)
                    }
                    controller.clearSearchScrollTopTarget()
                },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_arrow_upward),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                },
                text = {
                    Text(
                        "Scroll to top",
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                },
                containerColor = HarmonicTheme.colors.overlayButton,
                contentColor = Color.White,
            )
        }
    }
}

@Composable
private fun CommentsScrollbar(state: LazyListState, modifier: Modifier = Modifier) {
    val metrics by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems <= 0 || visibleItems.isEmpty() || visibleItems.size >= totalItems) {
                return@derivedStateOf null
            }

            val first = visibleItems.first()
            val averageItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            val firstFraction = if (first.size == 0) 0f else {
                (-first.offset).coerceAtLeast(0).toFloat() / first.size
            }
            ScrollbarMetrics(
                scrollPosition = (first.index + firstFraction) / totalItems,
                visibleFraction = (layoutInfo.viewportSize.height / (averageItemSize * totalItems))
                    .coerceIn(0.04f, 1f),
            )
        }
    }
    val currentMetrics = metrics ?: return
    val thumbColor = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.55f)
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val widthPx = with(density) { 3.dp.toPx() }
        val endPaddingPx = with(density) { 1.dp.toPx() }
        val minimumHeightPx = with(density) { 24.dp.toPx() }
        val thumbHeight = (size.height * currentMetrics.visibleFraction).coerceAtLeast(minimumHeightPx)
        val top = ((size.height - thumbHeight) * currentMetrics.scrollPosition)
            .coerceIn(0f, size.height - thumbHeight)
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(size.width - widthPx - endPaddingPx, top),
            size = androidx.compose.ui.geometry.Size(widthPx, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(widthPx / 2f),
        )
    }
}

private data class ScrollbarMetrics(
    val scrollPosition: Float,
    val visibleFraction: Float,
)

@Composable
internal fun EmptyCommentsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_newspaper),
            contentDescription = null,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .size(48.dp),
            tint = Color.Unspecified,
        )
        Text(
            "Open a story",
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun buildVisibleComments(source: List<Comment>): List<VisibleComment> {
    if (source.size <= 1) return emptyList()
    val byId = source.associateBy { it.id }
    return source.mapIndexedNotNull { index, comment ->
        if (index == 0 || !isCommentVisible(comment, byId)) return@mapIndexedNotNull null
        var lastChild = index
        for (candidate in index + 1 until source.size) {
            if (source[candidate].depth <= comment.depth) break
            lastChild = candidate
        }
        VisibleComment(
            sourceIndex = index,
            comment = comment,
            hiddenReplyCount = lastChild - index,
        )
    }
}

private fun isCommentVisible(comment: Comment, byId: Map<Int, Comment>): Boolean {
    var current = comment
    repeat(byId.size) {
        if (current.parent == -1) return true
        val parent = byId[current.parent] ?: return true
        if (!parent.expanded) return false
        current = parent
    }
    return true
}

private fun findNavigationTarget(
    state: LazyListState,
    comments: List<VisibleComment>,
    forward: Boolean,
    topLevelOnly: Boolean,
): Int {
    if (comments.isEmpty()) return 0
    val first = (state.firstVisibleItemIndex - 1).coerceIn(0, comments.lastIndex)
    val range = if (forward) {
        (first + 1)..comments.lastIndex
    } else {
        (first - 1 downTo 0)
    }
    for (index in range) {
        if (!topLevelOnly || comments[index].comment.depth == 0) return index
    }
    return first
}

@Composable
private fun CommentsHeader(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val story = controller.story
    val colors = HarmonicTheme.colors
    val headerTypography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    var loadedTint by remember(story.id, story.previewImageUrl, story.faviconTintSourceUrl) {
        mutableStateOf<Int?>(
            when {
                story.previewImageTintColorLoaded -> story.previewImageTintColor
                story.faviconTintColorLoaded -> story.faviconTintColor
                else -> null
            },
        )
    }
    val normalBackground = colors.background
    val targetBackground = if (settings.tintHeader) {
        loadedTint?.let(::Color) ?: normalBackground
    } else {
        normalBackground
    }
    val headerBackground by animateColorAsState(
        targetValue = targetBackground,
        label = "comments header tint",
    )
    val visibleHeaderBackground = lerpColor(
        normalBackground,
        headerBackground,
        controller.sheetSlideOffset,
    )
    LaunchedEffect(visibleHeaderBackground) {
        controller.listener.onHeaderColorChanged(visibleHeaderBackground.toArgb())
    }
    val topSpacer = with(density) {
        (controller.topInsetPx * controller.sheetSlideOffset).roundToInt().toDp()
    }
    val sideMarginStart = with(density) { controller.contentInsetLeftPx.toDp() }
    val sideMarginEnd = with(density) { controller.contentInsetRightPx.toDp() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(visibleHeaderBackground),
    ) {
        Spacer(Modifier.height(topSpacer))
        if (controller.integratedWebView) {
            SheetControls(
                readerModeAvailable = controller.readerModeAvailable,
                readerModeEnabled = controller.readerModeEnabled,
                showInvert = settings.showInvert,
                progress = 1f - controller.sheetSlideOffset,
                contentAlpha = if (controller.predictiveBackActive) {
                    1f - controller.predictiveBackProgress * 0.7f
                } else {
                    1f
                },
                onAction = controller.listener::onSheetAction,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = sideMarginStart, end = sideMarginEnd)
                .combinedClickable(
                    enabled = story.isLink,
                    onClick = controller.listener::onHeaderClick,
                    onLongClick = null,
                )
                .padding(top = dimensionResource(R.dimen.comments_header_top_padding)),
        ) {
            val showShimmer = !story.loaded && story.title.isNullOrBlank() && !controller.loadingFailed
            AnimatedContent(
                targetState = showShimmer,
                modifier = Modifier.graphicsLayer(
                    alpha = if (controller.predictiveBackActive) {
                        controller.predictiveBackProgress * 0.7f
                    } else {
                        1f
                    },
                ),
                transitionSpec = {
                    (fadeIn(tween(160)) togetherWith fadeOut(tween(160))).using(
                        SizeTransform(clip = false) { _, _ -> tween(220) },
                    )
                },
                label = "comments story header reveal",
            ) { loadingHeader ->
                if (loadingHeader) {
                    HeaderShimmer()
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        HeaderPreviewImage(
                            story = story,
                            visible = settings.showHeaderPreviewImage,
                            suppressed = controller.headerPreviewSuppressed,
                            tintBaseColor = PreviewImageTintUtils.getTintBaseColor(context),
                            onTintLoaded = { loadedTint = it },
                            onClick = controller.listener::onHeaderClick,
                            onLongClick = { bounds ->
                                controller.listener.onHeaderPreviewLongClick(
                                    controller.createHeaderPreviewTransitionSource(bounds)
                                        ?: controllerSourceView(context),
                                )
                            },
                        )
                        Text(
                            text = story.pdfTitle ?: story.videoTitle ?: story.title.orEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .semantics { heading() },
                            color = colors.storyNormal,
                            fontFamily = headerTypography.family,
                            fontWeight = FontWeight.Bold,
                            fontSize = headerTypography.commentsHeaderTitleSize.sp,
                            style = legacyTextStyle,
                        )
                        HeaderLinkInfo(story = story, settings = settings)
                        HeaderStoryBody(
                            story = story,
                            settings = settings,
                            onReferenceLongClick = { link, bounds ->
                                controller.listener.onReferenceLongClick(
                                    link,
                                    controller.createTransitionSource(bounds)
                                        ?: controllerSourceView(context),
                                )
                            },
                        )
                        LinkPreviewContent(story)
                        PollOptions(story.pollOptionArrayList, controller.listener::onPollOption)
                        StorySummary(story, settings)
                        HeaderMeta(story, settings)
                        HeaderActions(controller, settings)
                        OpFilterBanner(controller)
                        HeaderStatus(controller)
                    }
                }
            }
        }
        val fadeColors = remember(visibleHeaderBackground, normalBackground) {
            List(5) { index ->
                val position = index / 4f
                val smoothstep = position * position * (3f - 2f * position)
                lerpColor(visibleHeaderBackground, normalBackground, smoothstep)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val overlap = 6.dp.roundToPx()
                    layout(placeable.width, (placeable.height - overlap).coerceAtLeast(0)) {
                        placeable.place(0, -overlap)
                    }
                }
                .background(Brush.verticalGradient(fadeColors)),
        )
    }
}

private fun controllerSourceView(context: android.content.Context): View =
    (context as? AppCompatActivity)?.window?.decorView ?: View(context)

@Composable
private fun SheetControls(
    readerModeAvailable: Boolean,
    readerModeEnabled: Boolean,
    showInvert: Boolean,
    progress: Float,
    contentAlpha: Float,
    onAction: (Int) -> Unit,
) {
    val colors = HarmonicTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .align(Alignment.CenterHorizontally)
                .size(width = 50.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.storyDisabled.copy(alpha = 0.6f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height((56f * progress).dp)
                .graphicsLayer(alpha = progress * contentAlpha)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetButtonSlot(R.drawable.ic_refresh, "Refresh website") {
                onAction(CommentsComposeController.SHEET_REFRESH)
            }
            SheetButtonSlot(R.drawable.ic_arrow_upward, "Show comments") {
                onAction(CommentsComposeController.SHEET_EXPAND)
            }
            SheetButtonSlot(R.drawable.ic_public, "Open in browser") {
                onAction(CommentsComposeController.SHEET_BROWSER)
            }
            if (readerModeAvailable) {
                SheetButtonSlot(
                    R.drawable.ic_chrome_reader_mode,
                    if (readerModeEnabled) "Reader mode on" else "Reader mode",
                    tint = if (readerModeEnabled) MaterialTheme.colorScheme.secondary else colors.drawable,
                ) {
                    onAction(CommentsComposeController.SHEET_READER)
                }
            }
            if (showInvert) {
                SheetButtonSlot(R.drawable.ic_invert_colors, "Invert colors") {
                    onAction(CommentsComposeController.SHEET_INVERT)
                }
            }
        }
    }
}

@Composable
private fun RowScope.SheetButtonSlot(
    icon: Int,
    description: String,
    tint: Color = HarmonicTheme.colors.drawable,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = Alignment.Center,
    ) {
        SheetButton(icon, description, tint, onClick)
    }
}

@Composable
private fun SheetButton(
    icon: Int,
    description: String,
    tint: Color = HarmonicTheme.colors.drawable,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )
    }
}

@Composable
private fun HeaderShimmer() {
    val colors = HarmonicTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            Modifier
                .size(width = 260.dp, height = 31.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHighest),
        )
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(width = 112.dp, height = 16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceContainerHighest),
        )
        Row(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 50.dp, height = 14.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceContainerHighest),
                )
            }
        }
        Spacer(Modifier.height(42.dp))
    }
}

@Composable
private fun HeaderPreviewImage(
    story: Story,
    visible: Boolean,
    suppressed: Boolean,
    tintBaseColor: Int,
    onTintLoaded: (Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val context = LocalContext.current
    var previewUrl by remember(story.id, story.previewImageUrl) {
        mutableStateOf(story.previewImageUrl)
    }
    var bounds by remember(story.id) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    DisposableEffect(story.id, story.url, visible) {
        val request = if (visible && story.isLink && !story.url.isNullOrBlank()) {
            StoryPreviewImageLoader.loadPreviewContent(context, story.id, story.url, false) { url, _ ->
                story.previewImageUrl = url
                story.previewImageUrlLoaded = true
                previewUrl = url
            }
        } else {
            null
        }
        onDispose { request?.cancel() }
    }
    AnimatedVisibility(
        visible = visible && !previewUrl.isNullOrBlank() && !story.previewImageLoadFailed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        AsyncImage(
            model = previewUrl,
            contentDescription = "Story preview image",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .height(176.dp)
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer(alpha = if (suppressed) 0f else 1f)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongClick(bounds) },
                ),
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                calculateTint(state.result.drawable, context, tintBaseColor)?.let(onTintLoaded)
            },
        )
    }
}

private fun calculateTint(
    drawable: Drawable,
    context: android.content.Context,
    baseColor: Int,
): Int? = runCatching {
    PreviewImageTintUtils.calculateCardTint(
        baseColor,
        drawable,
        SettingsUtils.getPreferredPaletteTintConfigKey(context),
    )
}.getOrNull()

@Composable
private fun HeaderLinkInfo(story: Story, settings: CommentDisplaySettings) {
    if (!story.loaded || !story.isLink || story.isComment || story.url.isNullOrBlank()) return
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(preferredFont = settings.font)
    val domain = remember(story.url) {
        runCatching { story.getDisplayDomain(true) }.getOrDefault("")
    }
    val favicon = remember(story.url, settings.faviconProvider) {
        runCatching { FaviconLoader.getFaviconUrl(story.url, settings.faviconProvider) }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (settings.showThumbnail) {
            AsyncImage(
                model = favicon,
                fallback = painterResource(R.drawable.ic_public),
                error = painterResource(R.drawable.ic_public),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(17.dp),
            )
        }
        Text(
            text = "($domain)",
            color = colors.storyDisabled,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = legacyTextStyle,
        )
    }
}

@Composable
private fun HeaderStoryBody(
    story: Story,
    settings: CommentDisplaySettings,
    onReferenceLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
    ) -> Unit,
) {
    if (story.text.isNullOrBlank()) return
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(
        preferredFont = settings.font,
        commentTextSize = settings.preferredTextSize,
    )
    val references = remember(story.text, settings.collectReferenceLinks) {
        if (settings.collectReferenceLinks) CollectedReferenceLinks.parse(story.text) else null
    }
    val bodyHtml = if (references?.hasLinks() == true) references.bodyHtml else story.text
    val linkStyles = remember(colors.link) {
        TextLinkStyles(
            style = SpanStyle(colors.link, textDecoration = TextDecoration.Underline),
        )
    }
    val linkListener = remember(context) {
        LinkInteractionListener { link ->
            if (link is LinkAnnotation.Url) Utils.openLinkMaybeHN(context, link.url)
        }
    }
    val annotated = remember(bodyHtml, linkStyles, linkListener) {
        htmlToAnnotated(bodyHtml.orEmpty(), linkStyles, linkListener)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 4.dp, bottom = 3.dp),
    ) {
        if (annotated.isNotEmpty()) {
            Text(
                text = annotated,
                color = colors.storyNormal,
                fontFamily = typography.family,
                fontSize = typography.commentTextSize.sp,
                style = legacyTextStyle,
            )
        }
        references?.links.orEmpty().forEach { link ->
            HeaderReferenceRow(link, settings, onReferenceLongClick)
        }
    }
}

@Composable
private fun HeaderReferenceRow(
    link: CollectedReferenceLinks.ReferenceLink,
    settings: CommentDisplaySettings,
    onLongClick: (
        CollectedReferenceLinks.ReferenceLink,
        androidx.compose.ui.geometry.Rect,
    ) -> Unit,
) {
    val context = LocalContext.current
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(preferredFont = settings.font)
    var bounds by remember(link.url) { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, colors.commentDivider, RoundedCornerShape(6.dp))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                onClick = { Utils.openLinkMaybeHN(context, link.url) },
                onLongClick = { onLongClick(link, bounds) },
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = runCatching {
                FaviconLoader.getFaviconUrl(link.url, settings.faviconProvider)
            }.getOrNull(),
            fallback = painterResource(R.drawable.ic_public),
            error = painterResource(R.drawable.ic_public),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(17.dp),
        )
        if (link.hasNumber()) {
            Text(
                link.markerLabel,
                modifier = Modifier.padding(end = 8.dp),
                color = colors.storyDisabled,
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
        Text(
            ReferenceLinkRowUtils.getReferenceLinkLabel(link),
            modifier = Modifier.weight(1f),
            color = colors.storyNormal,
            fontFamily = typography.family,
            fontSize = settings.preferredTextSize.coerceAtLeast(12f).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LinkPreviewContent(story: Story) {
    val previewType = when {
        story.repoInfo != null -> "github"
        story.gitLabInfo != null -> "gitlab"
        story.stackExchangeInfo != null -> "stackexchange"
        story.arxivInfo != null -> "arxiv"
        story.wikiInfo != null -> "wikipedia"
        story.nitterInfo != null -> "nitter"
        story.linkPreviewLoading -> "loading"
        else -> "none"
    }
    AnimatedVisibility(
        visible = previewType != "none",
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val colors = HarmonicTheme.colors
        AnimatedContent(
            targetState = previewType,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, colors.storyDisabled, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            transitionSpec = {
                (fadeIn(tween(160)) togetherWith fadeOut(tween(160))).using(
                    SizeTransform(clip = false) { _, _ -> tween(220) },
                )
            },
            label = "comments link preview",
        ) {
            when (it) {
                "github" -> GitHubPreview(story)
                "gitlab" -> GitLabPreview(story)
                "stackexchange" -> StackExchangePreview(story)
                "arxiv" -> ArxivPreview(story)
                "wikipedia" -> WikipediaPreview(story)
                "nitter" -> NitterPreview(story)
                else -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(Modifier.size(44.dp))
                }
            }
        }
    }
}

@Composable
private fun PreviewHeader(text: String) {
    Text(
        text.uppercase(),
        color = HarmonicTheme.colors.storyNormal,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
    )
}

@Composable
private fun PreviewBody(text: String, bold: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    if (text.isBlank()) return
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
        color = HarmonicTheme.colors.storyNormal,
        fontFamily = ProductSansFontFamily,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = 15.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PreviewInfoRow(
    icon: Int,
    text: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    if (text.isNullOrBlank()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 22.dp)
            .then(
                if (onClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = null)
                } else {
                    Modifier
                },
            )
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            text,
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewInfoColumns(left: @Composable ColumnScope.() -> Unit, right: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(2f), content = left)
        Column(Modifier.weight(3f), content = right)
    }
}

@Composable
private fun GitHubPreview(story: Story) {
    val context = LocalContext.current
    val info = story.repoInfo ?: return
    Column {
        PreviewHeader("${info.owner} / ${info.name}")
        PreviewBody(info.about.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(R.drawable.ic_star, info.formatStars())
                PreviewInfoRow(R.drawable.ic_visibility, info.formatWatching())
                PreviewInfoRow(R.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(R.drawable.ic_link, info.getShortenedUrl()) {
                    Utils.openLinkMaybeHN(context, info.website)
                }
                PreviewInfoRow(R.drawable.ic_attribution, info.license)
                PreviewInfoRow(R.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
private fun GitLabPreview(story: Story) {
    val context = LocalContext.current
    val info = story.gitLabInfo ?: return
    Column {
        PreviewHeader("${info.namespace} / ${info.name}")
        PreviewBody(info.description.orEmpty())
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(R.drawable.ic_star, info.formatStars())
                PreviewInfoRow(R.drawable.ic_fork_right, info.formatForks())
            },
            right = {
                PreviewInfoRow(R.drawable.ic_link, info.getShortenedUrl()) {
                    Utils.openLinkMaybeHN(context, info.website)
                }
                PreviewInfoRow(R.drawable.ic_visibility, info.formatVisibility())
                PreviewInfoRow(R.drawable.ic_library_books, info.language)
            },
        )
    }
}

@Composable
private fun StackExchangePreview(story: Story) {
    val info = story.stackExchangeInfo ?: return
    Column {
        PreviewHeader("Stack Exchange:")
        PreviewBody(info.title.orEmpty(), bold = true)
        PreviewBody(info.formatBy().orEmpty(), maxLines = 20)
        PreviewInfoColumns(
            left = {
                PreviewInfoRow(R.drawable.ic_star, info.formatScore())
                PreviewInfoRow(R.drawable.ic_comment, info.formatAnswerCount())
                PreviewInfoRow(R.drawable.ic_visibility, info.formatViewCount())
            },
            right = {
                PreviewInfoRow(R.drawable.ic_check, info.formatAnswerState())
                PreviewInfoRow(R.drawable.ic_library_books, info.formatTags())
                PreviewInfoRow(R.drawable.ic_person, info.formatAuthor())
            },
        )
    }
}

@Composable
private fun ArxivPreview(story: Story) {
    val context = LocalContext.current
    val info = story.arxivInfo ?: return
    Column {
        PreviewHeader("Abstract:")
        PreviewBody(info.arxivAbstract.orEmpty())
        PreviewInfoRow(R.drawable.ic_calendar_today, runCatching(info::formatDate).getOrNull())
        PreviewInfoRow(
            when (info.authors?.size ?: 0) {
                1 -> R.drawable.ic_person
                2 -> R.drawable.ic_group
                else -> R.drawable.ic_groups
            },
            runCatching(info::concatNames).getOrNull(),
        )
        PreviewInfoRow(R.drawable.ic_library_books, runCatching(info::formatSubjects).getOrNull())
        Button(
            onClick = { Utils.downloadPDF(context, info.getPDFURL()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp),
        ) {
            Icon(painterResource(R.drawable.ic_file_download), contentDescription = null)
            Text(
                "Download PDF",
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun WikipediaPreview(story: Story) {
    val info = story.wikiInfo ?: return
    Column {
        PreviewHeader("Wikipedia summary:")
        PreviewBody(Html.fromHtml(info.summary.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString(), maxLines = 40)
    }
}

@Composable
private fun NitterPreview(story: Story) {
    val context = LocalContext.current
    val info = story.nitterInfo ?: return
    Column {
        PreviewHeader("${info.userName.orEmpty()} ${info.userTag.orEmpty()}")
        PreviewBody(Html.fromHtml(info.text.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString())
        if (!info.imgSrc.isNullOrBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = { Utils.launchCustomTab(context, story.url) },
                        onLongClick = null,
                    ),
            ) {
                AsyncImage(
                    model = info.imgSrc,
                    contentDescription = if (info.hasVideo) "Tweet video" else "Tweet image",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
                if (info.hasVideo) {
                    Text(
                        "VIDEO",
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.White,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                Row {
                    PreviewCompactInfo(R.drawable.ic_calendar_today, info.date)
                    PreviewCompactInfo(R.drawable.ic_reply, info.replyCount)
                }
                Row {
                    PreviewCompactInfo(R.drawable.ic_action_retweet, info.reposts)
                    PreviewCompactInfo(R.drawable.ic_thumb_up, info.likes)
                }
            }
            Button(
                onClick = { Utils.launchCustomTab(context, story.url) },
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_link_preview_x), contentDescription = null)
                Text(
                    "Open on X",
                    modifier = Modifier.padding(start = 8.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun PreviewCompactInfo(icon: Int, text: String?) {
    if (text.isNullOrBlank()) return
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(width = 15.dp, height = 16.dp),
        tint = HarmonicTheme.colors.drawable,
    )
    Text(
        text,
        modifier = Modifier.padding(start = 2.dp, end = 8.dp),
        color = HarmonicTheme.colors.storyNormal,
        fontFamily = ProductSansFontFamily,
        fontSize = 13.sp,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PollOptions(options: List<PollOption>?, onVote: (Int) -> Unit) {
    if (options == null) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            if (option.loaded) {
                OutlinedButton(
                    onClick = { onVote(option.id) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("${option.text} (${option.points} ${if (option.points == 1) "point" else "points"})")
                }
            } else {
                LoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(42.dp),
                )
            }
        }
    }
}

@Composable
private fun StorySummary(
    story: Story,
    settings: CommentDisplaySettings,
) {
    AnimatedVisibility(
        visible = !story.summary.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_auto_awesome),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(14.dp),
                )
                Text(
                    "Summary",
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = HarmonicTheme.colors.storyNormal,
                )
            }
            Text(
                story.summary.orEmpty(),
                modifier = Modifier.padding(top = 4.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = rememberContentTypography(settings.font).family,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun HeaderMeta(story: Story, settings: CommentDisplaySettings) {
    if (!story.loaded) return
    val colors = HarmonicTheme.colors
    val typography = rememberContentTypography(settings.font)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 17.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!story.isComment) {
                HeaderMetaItem(R.drawable.ic_thumb_up, story.score.toString(), typography)
            }
            HeaderMetaItem(R.drawable.ic_comment, story.descendants.toString(), typography)
            HeaderMetaItem(R.drawable.ic_schedule, story.timeFormatted, typography)
            HeaderMetaItem(R.drawable.ic_account_circle, story.by.orEmpty(), typography)
        }
        Spacer(Modifier.weight(1f))
        if (story.isLink) {
            Icon(
                painterResource(R.drawable.ic_link),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = HarmonicTheme.colors.drawable,
            )
        }
    }
}

@Composable
private fun HeaderMetaItem(icon: Int, label: String, typography: com.simon.harmonichackernews.ui.content.ContentTypography) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = HarmonicTheme.colors.drawable,
        )
        Text(
            label,
            modifier = Modifier.padding(start = 3.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = typography.family,
            fontSize = typography.commentsHeaderMetaSize.sp,
            style = legacyTextStyle,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderActions(
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
) {
    val context = LocalContext.current
    val story = controller.story
    val bookmarksEnabled = SettingsUtils.shouldUseBookmarks(context)
    val hasAccount = settings.hasAccountDetails
    val canReply = hasAccount && !Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time)
    var shareExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val upvoted = Utils.isUpvoted(context, story.id, story.isComment)
    val favorited = Utils.isFavorited(context, story.id)
    val bookmarked = Utils.isBookmarked(context, story.id)
    val actions = buildList {
        add(HeaderAction(R.drawable.ic_account_circle, "User", CommentsComposeController.HEADER_ACTION_USER))
        if (canReply) add(HeaderAction(R.drawable.ic_comment, if (story.isComment) "Reply to comment" else "Reply to post", CommentsComposeController.HEADER_ACTION_REPLY))
        if (hasAccount) add(HeaderAction(if (upvoted) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up, if (upvoted) "Remove vote" else "Vote", CommentsComposeController.HEADER_ACTION_VOTE, controller.storyVoteLoading))
        if (hasAccount) add(HeaderAction(if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star, if (favorited) "Remove favorite" else "Favorite", CommentsComposeController.HEADER_ACTION_FAVORITE, controller.storyFavoriteLoading))
        if (bookmarksEnabled && !hasAccount) add(HeaderAction(if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark, if (bookmarked) "Remove bookmark" else "Bookmark", CommentsComposeController.HEADER_ACTION_BOOKMARK))
        if (story.isLink && settings.canProvideSummary && !story.summaryGeneratedSuccessfully) add(HeaderAction(R.drawable.ic_auto_awesome, "Summarize", CommentsComposeController.HEADER_ACTION_SUMMARIZE, controller.storySummaryLoading))
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.comments_header_action_padding)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.Center,
    ) {
        actions.forEach { action ->
            HeaderActionButton(action) {
                controller.listener.onHeaderAction(action.action)
            }
        }
        Box(
            Modifier.size(width = 48.dp, height = 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { shareExpanded = true }) {
                Icon(
                    painterResource(R.drawable.ic_share),
                    contentDescription = "Share",
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            ShareMenu(
                expanded = shareExpanded,
                isLink = story.isLink,
                onDismiss = { shareExpanded = false },
                onAction = controller.listener::onShareAction,
            )
        }
        if (!hasAccount) {
            IconButton(
                onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                modifier = Modifier.size(width = 48.dp, height = 58.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    contentDescription = "Refresh",
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
        }
        Box(
            Modifier.size(width = 48.dp, height = 58.dp),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { moreExpanded = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    contentDescription = "More options",
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            MoreMenu(
                expanded = moreExpanded,
                sortExpanded = sortExpanded,
                controller = controller,
                settings = settings,
                bookmarksEnabled = bookmarksEnabled,
                onDismiss = {
                    moreExpanded = false
                    sortExpanded = false
                },
                onSortExpanded = { sortExpanded = true },
            )
        }
    }
}

private data class HeaderAction(
    val icon: Int,
    val label: String,
    val action: Int,
    val loading: Boolean = false,
)

private data class HeaderActionVisual(
    val icon: Int,
    val label: String,
    val loading: Boolean,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderActionButton(
    action: HeaderAction,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = !action.loading,
        modifier = Modifier.size(width = 48.dp, height = 58.dp),
    ) {
        AnimatedContent(
            targetState = HeaderActionVisual(action.icon, action.label, action.loading),
            transitionSpec = {
                (fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.72f)) togetherWith
                    (fadeOut(tween(90)) + scaleOut(tween(90), targetScale = 0.72f))
            },
            label = "${action.label} loading transition",
        ) { visual ->
            if (visual.loading) {
                LoadingIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = visual.label },
                )
            } else {
                Icon(
                    painterResource(visual.icon),
                    contentDescription = visual.label,
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
        }
    }
}

@Composable
private fun ShareMenu(
    expanded: Boolean,
    isLink: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss) {
        @Composable fun action(label: String, id: Int) {
            DropdownMenuItem(
                text = { CommentsMenuText(label) },
                onClick = {
                    onDismiss()
                    onAction(id)
                },
            )
        }
        if (isLink) {
            action("Article link", CommentsComposeController.SHARE_ARTICLE)
            action("Article link and title", CommentsComposeController.SHARE_ARTICLE_TITLE)
        }
        action("HN link", CommentsComposeController.SHARE_HN)
        action("HN link and title", CommentsComposeController.SHARE_HN_TITLE)
        if (isLink) action("Article + HN link and title", CommentsComposeController.SHARE_ALL)
    }
}

@Composable
@SuppressLint("LocalContextResourcesRead")
private fun MoreMenu(
    expanded: Boolean,
    sortExpanded: Boolean,
    controller: CommentsComposeController,
    settings: CommentDisplaySettings,
    bookmarksEnabled: Boolean,
    onDismiss: () -> Unit,
    onSortExpanded: () -> Unit,
) {
    val story = controller.story
    val commentsCount = controller.comments.size
    val context = LocalContext.current
    val bookmarked = Utils.isBookmarked(context, story.id)
    HarmonicDropdownMenu(expanded = expanded, onDismiss = onDismiss) {
        @Composable fun action(label: String, icon: Int, id: Int) {
            DropdownMenuItem(
                text = { CommentsMenuText(label) },
                leadingIcon = {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = {
                    onDismiss()
                    controller.listener.onMoreAction(id)
                },
            )
        }
        if (settings.hasAccountDetails) action("Refresh", R.drawable.ic_refresh, CommentsComposeController.MORE_REFRESH)
        if (story.isComment && story.parentId > 0) action("Open parent", R.drawable.ic_reply, CommentsComposeController.MORE_OPEN_PARENT)
        if (story.isComment && story.commentMasterId > 0) action("Open top level", R.drawable.ic_arrow_upward, CommentsComposeController.MORE_OPEN_TOP_LEVEL)
        if (settings.hasAccountDetails && bookmarksEnabled) {
            action(
                if (bookmarked) "Remove bookmark" else "Bookmark",
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                CommentsComposeController.MORE_TOGGLE_BOOKMARK,
            )
        }
        if (commentsCount > 1) action("Search comments", R.drawable.ic_search, CommentsComposeController.MORE_SEARCH)
        if (commentsCount > 2) {
            DropdownMenuItem(
                text = { CommentsMenuText("Sort comments") },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_filter_list),
                        contentDescription = null,
                        tint = HarmonicTheme.colors.drawable,
                    )
                },
                onClick = onSortExpanded,
            )
        }
        if (!controller.commentsByOpFilterActive && controller.hasCommentsByOp) {
            action("Comments by OP", R.drawable.ic_person, CommentsComposeController.MORE_COMMENTS_BY_OP)
        }
        action("Open in browser", R.drawable.ic_open_in_browser, CommentsComposeController.MORE_OPEN_BROWSER)
        if (controller.adBlockActive) {
            action("Disable AdBlock", R.drawable.ic_block, CommentsComposeController.MORE_DISABLE_ADBLOCK)
        }
        if (story.isLink) action("View on archive", R.drawable.ic_history, CommentsComposeController.MORE_ARCHIVE)
    }
    HarmonicDropdownMenu(expanded = sortExpanded, onDismiss = onDismiss) {
        val options = context.resources.getStringArray(R.array.comment_sorting)
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    CommentsMenuText(
                        if (option == controller.currentSorting) "✓ $option" else option,
                    )
                },
                onClick = {
                    onDismiss()
                    controller.listener.onSortComments(option)
                },
            )
        }
    }
}

@Composable
private fun HarmonicDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = HarmonicTheme.colors.popupMenuBackground,
        content = content,
    )
}

@Composable
private fun CommentsMenuText(text: String) {
    Text(
        text = text,
        color = HarmonicTheme.colors.textPrimary,
        fontFamily = ProductSansFontFamily,
        fontSize = 16.sp,
        style = legacyTextStyle,
    )
}

@Composable
private fun OpFilterBanner(controller: CommentsComposeController) {
    AnimatedVisibility(
        visible = controller.commentsByOpFilterActive,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HarmonicTheme.colors.surfaceContainerHigh)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Showing comments by OP",
                modifier = Modifier.weight(1f),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { controller.listener.onMoreAction(CommentsComposeController.MORE_COMMENTS_BY_OP) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Show all comments")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderStatus(controller: CommentsComposeController) {
    val showLoading = !controller.loadingFailed &&
        (!controller.commentsLoaded || controller.commentsRefreshInProgress)
    val showEmpty = !controller.loadingFailed && controller.commentsLoaded &&
        controller.comments.size <= 1
    AnimatedContent(
        targetState = when {
            controller.loadingFailed -> "failed"
            showLoading -> "loading"
            showEmpty -> "empty"
            controller.showUpdate -> "refresh"
            else -> "none"
        },
        transitionSpec = {
            (fadeIn() + expandVertically()).togetherWith(fadeOut() + shrinkVertically())
        },
        label = "comments header status",
    ) { state ->
        when (state) {
            "loading" -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (controller.commentsLoaded) 16.dp else 44.dp, bottom = 18.dp),
                contentAlignment = Alignment.Center,
            ) { LoadingIndicator(Modifier.size(42.dp)) }
            "failed" -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(R.drawable.ic_cloud_off), null, Modifier.size(40.dp))
                Text(
                    if (controller.loadingFailedServerError) "Loading failed" else "No internet connection",
                    modifier = Modifier.padding(top = 6.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Try again") }
            }
            "empty" -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(painterResource(R.drawable.ic_comment), null, Modifier.size(42.dp))
                Text(
                    if (controller.story.isComment) "No replies" else "No comments",
                    modifier = Modifier.padding(top = 4.dp),
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
            "refresh" -> OutlinedButton(
                onClick = { controller.listener.onHeaderAction(CommentsComposeController.HEADER_ACTION_REFRESH) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_refresh), null)
                Text("Tap to refresh", Modifier.padding(start = 8.dp))
            }
            else -> Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun CommentNavigationButtons(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
) {
    Row(
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(HarmonicTheme.colors.overlayButton)
            .border(1.dp, HarmonicTheme.colors.outlineVariant, RoundedCornerShape(28.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .combinedClickable(onClick = onPrevious, onLongClick = onFirst),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_up_dark), "Previous top-level comment", tint = Color.Unspecified)
        }
        Icon(
            painterResource(R.drawable.ic_explore_dark),
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(28.dp),
            tint = Color.Unspecified,
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .combinedClickable(onClick = onNext, onLongClick = onLast),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_down_dark), "Next top-level comment", tint = Color.Unspecified)
        }
    }
}

private fun htmlToAnnotated(
    html: String,
    linkStyles: TextLinkStyles,
    listener: LinkInteractionListener,
): AnnotatedString = runCatching {
    AnnotatedString.fromHtml(preserveLegacyParagraphSpacing(html), linkStyles, listener)
}.getOrElse {
    AnnotatedString(Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString())
}

private fun preserveLegacyParagraphSpacing(html: String): String = html
    .replace(Regex("<p\\s*>", RegexOption.IGNORE_CASE), "<br><br>")
    .replace(Regex("</p>\\s*<p", RegexOption.IGNORE_CASE), "</p><br><p")
    .replace(Regex("</div>\\s*<div", RegexOption.IGNORE_CASE), "</div><br><div")

private fun lerpColor(start: Color, end: Color, fraction: Float): Color = Color(
    red = start.red + (end.red - start.red) * fraction,
    green = start.green + (end.green - start.green) * fraction,
    blue = start.blue + (end.blue - start.blue) * fraction,
    alpha = start.alpha + (end.alpha - start.alpha) * fraction,
)

private val legacyTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun CommentsHeaderPreview() {
    val story = remember {
        Story().apply {
            id = 1
            loaded = true
            isLink = true
            title = "Nvidia RTX Spark"
            url = "https://nvidia.com"
            by = "shenli3514"
            score = 428
            descendants = 417
            time = (System.currentTimeMillis() / 1000L).toInt() - 3600
            text = "A small preview of the story text with <a href=\"https://example.com\">a link</a>."
        }
    }
    val context = LocalContext.current
    HarmonicTheme {
        Column(Modifier.background(HarmonicTheme.colors.background)) {
            Text(
                story.title,
                Modifier.padding(16.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
            )
            HeaderMeta(story, CommentDisplaySettings.from(context, true, false, true, false))
        }
    }
}
