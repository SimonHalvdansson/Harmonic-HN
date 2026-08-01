@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.stories

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withTranslation
import androidx.fragment.app.Fragment
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemElement
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.SettingsUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Compose presentation bridge for the stories screen. The fragment remains the data/network
 * controller during this migration; adapter notifications are converted to immutable snapshots.
 */
class StoriesComposeController private constructor(
    private val activity: AppCompatActivity,
    internal val listener: Listener,
) {
    internal var mainStories by mutableStateOf<List<Story>>(emptyList())
        private set
    internal var searchStories by mutableStateOf<List<Story>>(emptyList())
        private set
    internal var displaySettings by mutableStateOf<StoryDisplaySettings?>(null)
        private set
    internal var typeLabels by mutableStateOf<List<String>>(emptyList())
        private set
    internal var selectedTypeIndex by mutableIntStateOf(0)
        private set
    internal var searching by mutableStateOf(false)
        private set
    internal var searchDraft by mutableStateOf("")
        private set
    internal var lastSearch by mutableStateOf("")
        private set
    internal var searchSortLabel by mutableStateOf("Relevance")
        private set
    internal var searchDateLabel by mutableStateOf("All time")
        private set
    internal var searchPointsLabel by mutableStateOf("Any points")
        private set
    internal var searchCommentsLabel by mutableStateOf("Any comments")
        private set
    internal var searchSortLabels by mutableStateOf<List<String>>(emptyList())
        private set
    internal var searchDateLabels by mutableStateOf<List<String>>(emptyList())
        private set
    internal var searchPointsLabels by mutableStateOf<List<String>>(emptyList())
        private set
    internal var searchCommentsLabels by mutableStateOf<List<String>>(emptyList())
        private set
    internal var searchOnlyClicked by mutableStateOf(false)
        private set
    internal var loading by mutableStateOf(false)
        private set
    internal var refreshing by mutableStateOf(false)
        private set
    internal var loadingFailed by mutableStateOf(false)
        private set
    internal var loadingFailedServerError by mutableStateOf(false)
        private set
    internal var loadingFailedMessage by mutableStateOf("Loading failed")
        private set
    internal var showingCached by mutableStateOf(false)
        private set
    internal var showCachedAction by mutableStateOf(false)
        private set
    internal var showEmptySavedList by mutableStateOf(false)
        private set
    internal var emptySavedListText by mutableStateOf("No saved stories")
        private set
    internal var showEmptySearch by mutableStateOf(false)
        private set
    internal var showUpdate by mutableStateOf(false)
        private set
    internal var lastUpdatedText by mutableStateOf<String?>(null)
        private set
    internal var showLoadMore by mutableStateOf(false)
        private set
    internal var loadMoreLoading by mutableStateOf(false)
        private set
    internal var mainVisibleCount by mutableIntStateOf(Int.MAX_VALUE)
        private set
    internal var searchVisibleCount by mutableIntStateOf(Int.MAX_VALUE)
        private set
    internal var showSavedFilter by mutableStateOf(false)
        private set
    internal var savedFilter by mutableIntStateOf(FILTER_BOTH)
        private set
    internal var showFrontDate by mutableStateOf(false)
        private set
    internal var frontDateLabel by mutableStateOf("")
        private set
    internal var frontPreviousEnabled by mutableStateOf(false)
        private set
    internal var frontNextEnabled by mutableStateOf(false)
        private set
    internal var loggedIn by mutableStateOf(false)
        private set
    internal var canCache by mutableStateOf(false)
        private set
    internal var canClearHistory by mutableStateOf(false)
        private set
    internal var topInsetPx by mutableIntStateOf(0)
        private set
    internal var contentInsetStartPx by mutableIntStateOf(0)
        private set
    internal var bottomInsetPx by mutableIntStateOf(0)
        private set
    internal var predictiveBackActive by mutableStateOf(false)
        private set
    internal var predictiveBackProgress by mutableFloatStateOf(0f)
        private set
    internal var predictiveBackSettleRequest by mutableStateOf<PredictiveBackSettleRequest?>(null)
        private set
    internal var contentVersion by mutableIntStateOf(0)
        private set
    internal var scrollByRequest by mutableStateOf<ScrollByRequest?>(null)
        private set
    internal var headerPinnedForPreview by mutableStateOf(false)
        private set
    internal val storyPagingAlphas = mutableStateMapOf<Int, Float>()

    private var composeView: ComposeView? = null
    private var requestSerial = 0
    private val storyBounds = mutableMapOf<Int, MutableMap<StoryItemElement, Rect>>()
    private val transitionSources = mutableSetOf<View>()
    private val suppressedStoryIds = mutableStateOf<Set<Int>>(emptySet())

    fun updateContent(
        mainStories: List<Story>,
        searchStories: List<Story>,
        displaySettings: StoryDisplaySettings,
        typeLabels: List<String>,
        selectedTypeIndex: Int,
        searching: Boolean,
        lastSearch: String,
        searchSortLabel: String,
        searchDateLabel: String,
        searchPointsLabel: String,
        searchCommentsLabel: String,
        searchSortLabels: Array<String>,
        searchDateLabels: Array<String>,
        searchPointsLabels: Array<String>,
        searchCommentsLabels: Array<String>,
        searchOnlyClicked: Boolean,
        loading: Boolean,
        refreshing: Boolean,
        loadingFailed: Boolean,
        loadingFailedServerError: Boolean,
        loadingFailedMessage: String,
        showingCached: Boolean,
        showCachedAction: Boolean,
        showEmptySavedList: Boolean,
        emptySavedListText: String,
        showEmptySearch: Boolean,
        showUpdate: Boolean,
        lastUpdatedText: String?,
        showLoadMore: Boolean,
        loadMoreLoading: Boolean,
        mainVisibleCount: Int,
        searchVisibleCount: Int,
        showSavedFilter: Boolean,
        savedFilter: Int,
        showFrontDate: Boolean,
        frontDateLabel: String,
        frontPreviousEnabled: Boolean,
        frontNextEnabled: Boolean,
        loggedIn: Boolean,
        canCache: Boolean,
        canClearHistory: Boolean,
        topInsetPx: Int,
        contentInsetStartPx: Int,
        bottomInsetPx: Int,
    ) {
        val enteringSearch = !this.searching && searching
        this.mainStories = mainStories.toList()
        this.searchStories = searchStories.toList()
        this.displaySettings = displaySettings
        this.typeLabels = typeLabels.toList()
        this.selectedTypeIndex = selectedTypeIndex
        this.searching = searching
        this.lastSearch = lastSearch
        if (enteringSearch) searchDraft = lastSearch
        if (!searching && searchDraft.isNotEmpty()) searchDraft = ""
        this.searchSortLabel = searchSortLabel
        this.searchDateLabel = searchDateLabel
        this.searchPointsLabel = searchPointsLabel
        this.searchCommentsLabel = searchCommentsLabel
        this.searchSortLabels = searchSortLabels.toList()
        this.searchDateLabels = searchDateLabels.toList()
        this.searchPointsLabels = searchPointsLabels.toList()
        this.searchCommentsLabels = searchCommentsLabels.toList()
        this.searchOnlyClicked = searchOnlyClicked
        this.loading = loading
        this.refreshing = refreshing
        this.loadingFailed = loadingFailed
        this.loadingFailedServerError = loadingFailedServerError
        this.loadingFailedMessage = loadingFailedMessage
        this.showingCached = showingCached
        this.showCachedAction = showCachedAction
        this.showEmptySavedList = showEmptySavedList
        this.emptySavedListText = emptySavedListText
        this.showEmptySearch = showEmptySearch
        this.showUpdate = showUpdate
        this.lastUpdatedText = lastUpdatedText
        this.showLoadMore = showLoadMore
        this.loadMoreLoading = loadMoreLoading
        this.mainVisibleCount = mainVisibleCount
        this.searchVisibleCount = searchVisibleCount
        this.showSavedFilter = showSavedFilter
        this.savedFilter = savedFilter
        this.showFrontDate = showFrontDate
        this.frontDateLabel = frontDateLabel
        this.frontPreviousEnabled = frontPreviousEnabled
        this.frontNextEnabled = frontNextEnabled
        this.loggedIn = loggedIn
        this.canCache = canCache
        this.canClearHistory = canClearHistory
        this.topInsetPx = topInsetPx
        this.contentInsetStartPx = contentInsetStartPx
        this.bottomInsetPx = bottomInsetPx
        contentVersion++
    }

    fun updateSearchDraft(value: String) {
        searchDraft = value
    }

    fun beginPredictiveBack(progress: Float) {
        predictiveBackSettleRequest = null
        predictiveBackActive = true
        predictiveBackProgress = progress.coerceIn(0f, 1f)
    }

    fun updatePredictiveBack(progress: Float) {
        predictiveBackProgress = progress.coerceIn(0f, 1f)
    }

    fun cancelPredictiveBack() {
        predictiveBackSettleRequest = PredictiveBackSettleRequest(
            serial = ++requestSerial,
            target = 0f,
        )
    }

    fun commitPredictiveBack() {
        predictiveBackSettleRequest = PredictiveBackSettleRequest(
            serial = ++requestSerial,
            target = 1f,
        )
    }

    internal fun endPredictiveBack(request: PredictiveBackSettleRequest? = null) {
        if (request != null && predictiveBackSettleRequest != request) return
        predictiveBackSettleRequest = null
        predictiveBackActive = false
        predictiveBackProgress = 0f
    }

    fun requestScrollBy(dy: Int) {
        if (dy != 0) {
            headerPinnedForPreview = true
            scrollByRequest = ScrollByRequest(++requestSerial, dy)
        }
    }

    internal fun unpinPreviewHeader() {
        headerPinnedForPreview = false
    }

    internal fun consumeScrollBy(request: ScrollByRequest) {
        if (scrollByRequest == request) scrollByRequest = null
    }

    fun setStoryPagingAlphas(
        firstStoryId: Int,
        firstAlpha: Float,
        secondStoryId: Int,
        secondAlpha: Float,
    ) {
        storyPagingAlphas.clear()
        if (firstStoryId > 0) storyPagingAlphas[firstStoryId] = firstAlpha
        if (secondStoryId > 0) storyPagingAlphas[secondStoryId] = secondAlpha
    }

    fun clearStoryPagingAlphas() {
        storyPagingAlphas.clear()
    }

    fun getStoryPagingDistance(firstStoryId: Int, secondStoryId: Int): Int {
        val activeStories = if (searching) searchStories else mainStories
        val first = activeStories.indexOfFirst { it.id == firstStoryId }
        val second = activeStories.indexOfFirst { it.id == secondStoryId }
        if (first < 0 || second < 0 || first == second) return averageStoryHeight()
        val start = minOf(first, second)
        val end = maxOf(first, second)
        return (start until end).sumOf { index ->
            storyBounds[activeStories[index].id]
                ?.get(StoryItemElement.Container)
                ?.height
                ?.roundToInt()
                ?.coerceAtLeast(1)
                ?: averageStoryHeight()
        }
    }

    private fun averageStoryHeight(): Int {
        val heights = storyBounds.values.mapNotNull {
            it[StoryItemElement.Container]?.height?.roundToInt()?.takeIf { height -> height > 0 }
        }
        return if (heights.isEmpty()) {
            (96f * activity.resources.displayMetrics.density).roundToInt()
        } else {
            heights.sum() / heights.size
        }
    }

    internal fun updateStoryBounds(storyId: Int, element: StoryItemElement, bounds: Rect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        storyBounds.getOrPut(storyId) { mutableMapOf() }[element] = bounds
    }

    fun createStoryTransitionSource(storyId: Int): View? {
        // Paging changes the active story while the overlay remains open. Unlike RecyclerView
        // children these proxy views live in the activity overlay, so retire the previous page's
        // proxies before creating the new shared-element source.
        clearTransitionSources()
        return storyBounds[storyId]?.get(StoryItemElement.Container)?.let { bounds ->
            createTransitionSource(bounds, false) { visible ->
                if (!visible) suppressedStoryIds.value = suppressedStoryIds.value + storyId
            }
        }
    }

    fun createStoryImageTransitionSource(storyId: Int): ImageView? =
        storyBounds[storyId]?.get(StoryItemElement.Preview)?.let { bounds ->
            createTransitionSource(bounds, true, null) as? ImageView
        }

    fun createStoryTitleTransitionSource(storyId: Int): View? =
        storyBounds[storyId]?.get(StoryItemElement.Title)?.let {
            createTransitionSource(it, false, null)
        }

    fun createStorySummaryTransitionSource(storyId: Int): View? =
        storyBounds[storyId]?.get(StoryItemElement.Summary)?.let {
            createTransitionSource(it, false, null)
        }

    fun createStoryMetaTransitionSource(storyId: Int): View? =
        storyBounds[storyId]?.get(StoryItemElement.Meta)?.let {
            createTransitionSource(it, false, null)
        }

    private fun createTransitionSource(
        bounds: Rect,
        image: Boolean,
        onVisibilityChanged: ((Boolean) -> Unit)?,
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
        val proxy = if (image) {
            ComposeCropTransitionImageView(activity, source, left, top, right - left, bottom - top)
        } else {
            ComposeCropTransitionView(activity, source, left, top, onVisibilityChanged)
        }
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
        suppressedStoryIds.value = emptySet()
    }

    internal fun isStorySuppressed(storyId: Int): Boolean = storyId in suppressedStoryIds.value

    data class ScrollByRequest(val serial: Int, val dy: Int)

    data class PredictiveBackSettleRequest(val serial: Int, val target: Float)

    interface Listener {
        fun onTypeSelected(index: Int)
        fun onOpenSearch()
        fun onCloseSearch()
        fun onSearch(query: String)
        fun onSearchOption(kind: Int, index: Int)
        fun onToggleOnlyClicked()
        fun onRefresh()
        fun onShowCached()
        fun onLoadMore()
        fun onSavedFilterSelected(filter: Int)
        fun onShiftFrontDate(days: Int)
        fun onPickFrontDate()
        fun onMoreAction(action: Int)
        fun onLinkClick(story: Story)
        fun onCommentClick(story: Story)
        fun onCommentStoryClick(story: Story)
        fun onCommentRepliesClick(story: Story)
        fun onStoryLongClick(story: Story)
        fun onVisibleStoryRange(lastVisibleIndex: Int)
    }

    companion object {
        const val SEARCH_OPTION_SORT = 0
        const val SEARCH_OPTION_DATE = 1
        const val SEARCH_OPTION_POINTS = 2
        const val SEARCH_OPTION_COMMENTS = 3

        const val MORE_SETTINGS = 0
        const val MORE_LOGIN = 1
        const val MORE_PROFILE = 2
        const val MORE_CACHE = 3
        const val MORE_SUBMIT = 4
        const val MORE_CLEAR_HISTORY = 5

        const val FILTER_STORIES = 0
        const val FILTER_BOTH = 1
        const val FILTER_COMMENTS = 2

        @JvmStatic
        fun install(
            fragment: Fragment,
            contentHost: ViewGroup,
            listener: Listener,
        ): StoriesComposeController {
            val activity = fragment.requireActivity() as AppCompatActivity
            val controller = StoriesComposeController(activity, listener)
            val composeView = ComposeView(activity).apply {
                id = View.generateViewId()
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    HarmonicTheme { StoriesScreen(controller) }
                }
            }
            controller.composeView = composeView
            val boundedHost = StoriesComposeHost(activity).apply {
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
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            boundedHost.bringToFront()
            return controller
        }
    }
}

/**
 * Weighted FragmentContainerViews can receive an unbounded speculative height during the first
 * LinearLayout measure pass. Cap only that invalid pass; subsequent exact window measurements
 * replace it normally.
 */
private class StoriesComposeHost(context: android.content.Context) : FrameLayout(context) {
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
        source.getLocationInWindow(sourceLocation)
        canvas.withTranslation(
            (sourceLocation[0] - cropLeftInWindow).toFloat(),
            (sourceLocation[1] - cropTopInWindow).toFloat(),
        ) { source.draw(this) }
    }

    override fun setVisibility(visibility: Int) {
        val changed = visibility != this.visibility
        super.setVisibility(visibility)
        if (changed) onVisibilityChanged?.invoke(visibility == VISIBLE)
    }
}

@SuppressLint("ViewConstructor")
private class ComposeCropTransitionImageView(
    context: android.content.Context,
    source: View,
    cropLeftInWindow: Int,
    cropTopInWindow: Int,
    width: Int,
    height: Int,
) : androidx.appcompat.widget.AppCompatImageView(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        val sourceLocation = IntArray(2)
        source.getLocationInWindow(sourceLocation)
        val bitmap = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        val canvas = Canvas(bitmap)
        canvas.withTranslation(
            (sourceLocation[0] - cropLeftInWindow).toFloat(),
            (sourceLocation[1] - cropTopInWindow).toFloat(),
        ) { source.draw(this) }
        setImageDrawable(bitmap.toDrawable(resources))
        scaleType = ImageView.ScaleType.CENTER_CROP
    }
}

private val StoriesEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
private fun StoriesScreen(controller: StoriesComposeController) {
    val settings = controller.displaySettings ?: return
    val mainState = rememberLazyListState()
    val searchState = rememberLazyListState()
    val progress = controller.predictiveBackProgress.coerceIn(0f, 1f)
    val predictive = controller.predictiveBackActive

    val settleRequest = controller.predictiveBackSettleRequest
    LaunchedEffect(settleRequest?.serial) {
        val request = settleRequest ?: return@LaunchedEffect
        val start = controller.predictiveBackProgress.coerceIn(0f, 1f)
        val distance = kotlin.math.abs(request.target - start)
        val animation = Animatable(start)
        animation.animateTo(
            targetValue = request.target,
            animationSpec = tween(
                durationMillis = (180 * distance).roundToInt().coerceAtLeast(1),
                easing = StoriesEasing,
            ),
        ) {
            controller.updatePredictiveBack(value)
        }
        if (request.target == 1f) {
            controller.listener.onCloseSearch()
            withFrameNanos { }
        }
        controller.endPredictiveBack(request)
    }

    val scrollRequest = controller.scrollByRequest
    LaunchedEffect(scrollRequest) {
        scrollRequest?.let { request ->
            (if (controller.searching) searchState else mainState).scrollBy(request.dy.toFloat())
            controller.consumeScrollBy(request)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background),
    ) {
        if (predictive) {
            val searchProgress = (progress / 0.5f).coerceIn(0f, 1f)
            val mainProgress = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.searchStories,
                listState = searchState,
                searchMode = true,
                modifier = Modifier.graphicsLayer {
                    alpha = 1f - searchProgress
                    translationY = 24.dp.toPx() * searchProgress
                },
            )
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.mainStories,
                listState = mainState,
                searchMode = false,
                modifier = Modifier.graphicsLayer {
                    alpha = mainProgress
                    translationY = 24.dp.toPx() * (1f - mainProgress)
                },
            )
        } else {
            AnimatedContent(
                targetState = controller.searching,
                transitionSpec = {
                    (fadeIn(tween(180, easing = StoriesEasing)) +
                        slideInVertically(tween(180, easing = StoriesEasing)) { it / 10 })
                        .togetherWith(
                            fadeOut(tween(140, easing = StoriesEasing)) +
                                slideOutVertically(tween(140, easing = StoriesEasing)) { it / 10 },
                        )
                },
                label = "stories search mode",
            ) { searchMode ->
                StoriesList(
                    controller = controller,
                    settings = settings,
                    stories = if (searchMode) controller.searchStories else controller.mainStories,
                    listState = if (searchMode) searchState else mainState,
                    searchMode = searchMode,
                )
            }
        }

        AnimatedVisibility(
            visible = controller.showUpdate && !controller.searching,
            enter = fadeIn(tween(180, easing = StoriesEasing)),
            exit = fadeOut(tween(140, easing = StoriesEasing)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = controller.listener::onRefresh,
                icon = {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
                },
                text = { Text("Tap to update", fontWeight = FontWeight.Bold) },
            )
        }
    }
}

@Composable
private fun StoriesList(
    controller: StoriesComposeController,
    settings: StoryDisplaySettings,
    stories: List<Story>,
    listState: LazyListState,
    searchMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleCount = if (searchMode) controller.searchVisibleCount else controller.mainVisibleCount
    val visibleStories = remember(stories, visibleCount, controller.contentVersion) {
        stories.take(visibleCount.coerceAtLeast(0).coerceAtMost(stories.size))
    }
    val bottomPadding = with(LocalDensity.current) { controller.bottomInsetPx.toDp() }
    val density = LocalDensity.current
    var headerHeightPx by remember(searchMode) { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val headerPinnedForPreview = controller.headerPinnedForPreview
    val headerCollapsePx by remember(listState, headerPinnedForPreview, headerHeightPx) {
        derivedStateOf {
            if (headerPinnedForPreview) {
                0
            } else if (listState.firstVisibleItemIndex > 0) {
                headerHeightPx
            } else {
                listState.firstVisibleItemScrollOffset.coerceAtMost(headerHeightPx)
            }
        }
    }
    val userScrollConnection = remember(controller) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    controller.unpinPreviewHeader()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(listState, searchMode) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { last -> controller.listener.onVisibleStoryRange(last.coerceAtLeast(0)) }
    }

    PullToRefreshBox(
        isRefreshing = controller.refreshing && !searchMode,
        onRefresh = controller.listener::onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(userScrollConnection),
                contentPadding = PaddingValues(
                    top = headerHeight,
                    bottom = bottomPadding + if (controller.showUpdate) 88.dp else 8.dp,
                ),
            ) {
                itemsIndexed(
                    items = visibleStories,
                    key = { _, story -> "${if (searchMode) "search" else "main"}-${story.id}" },
                    contentType = { _, story -> if (story.isComment) "comment" else "story" },
                ) { index, story ->
                    if (story.isComment) {
                        SavedCommentStoryItem(
                            story = story,
                            settings = settings,
                            onStory = { controller.listener.onCommentStoryClick(story) },
                            onReplies = { controller.listener.onCommentRepliesClick(story) },
                            modifier = Modifier.animateItem(),
                        )
                    } else if (!story.loaded && !story.loadingFailed) {
                        StoryLoadingItem(modifier = Modifier.animateItem())
                    } else {
                        val pagingAlpha = controller.storyPagingAlphas[story.id] ?: 1f
                        val suppressed = controller.isStorySuppressed(story.id)
                        StoryItem(
                            model = story.toUiModel(index, settings),
                            style = settings.toItemStyle(story),
                            modifier = Modifier
                                .animateItem()
                                .graphicsLayer(alpha = if (suppressed) 0f else pagingAlpha),
                            listItem = true,
                            onLinkClick = { controller.listener.onLinkClick(story) },
                            onLinkLongClick = { controller.listener.onStoryLongClick(story) },
                            onCommentClick = { controller.listener.onCommentClick(story) },
                            onElementBoundsChanged = { element, bounds ->
                                controller.updateStoryBounds(story.id, element, bounds)
                            },
                        )
                    }
                }

                if (controller.showLoadMore) {
                    item(key = "${if (searchMode) "search" else "main"}-load-more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (controller.loadMoreLoading) {
                                LoadingIndicator(modifier = Modifier.size(40.dp))
                            } else {
                                OutlinedButton(onClick = controller.listener::onLoadMore) {
                                    Text("Load more")
                                }
                            }
                        }
                    }
                }
            }

            StoriesHeader(
                controller = controller,
                searchMode = searchMode,
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer(translationY = -headerCollapsePx.toFloat())
                    .onGloballyPositioned { headerHeightPx = it.size.height },
            )
        }
    }
}

@Composable
private fun StoriesHeader(
    controller: StoriesComposeController,
    searchMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val topInset = with(density) { controller.topInsetPx.toDp() }
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
    val compact = controller.displaySettings?.compactHeader == true
    val topSpacing = if (compact) 20.dp else 40.dp
    val bottomSpacing = if (compact) 10.dp else 26.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.background)
            .padding(
                top = topInset + topSpacing,
                bottom = bottomSpacing,
            )
            .animateContentSize(tween(220, easing = StoriesEasing)),
    ) {
        val sideStart = 16.dp + startInset
        val sideEnd = 16.dp
        if (searchMode) {
            SearchHeader(controller, sideStart, sideEnd)
        } else {
            MainHeader(
                controller,
                modifier = Modifier.padding(start = sideStart, end = sideEnd),
            )
        }

        AnimatedVisibility(visible = !searchMode && controller.showSavedFilter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 10.dp, end = sideEnd),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SavedFilterButton("Stories", StoriesComposeController.FILTER_STORIES, controller, Modifier.weight(1f))
                SavedFilterButton("Both", StoriesComposeController.FILTER_BOTH, controller, Modifier.weight(1f))
                SavedFilterButton("Comments", StoriesComposeController.FILTER_COMMENTS, controller, Modifier.weight(1f))
            }
        }

        AnimatedVisibility(visible = !searchMode && controller.showFrontDate) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 10.dp, end = sideEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { controller.listener.onShiftFrontDate(-1) },
                    enabled = controller.frontPreviousEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_left), "Previous front page day")
                }
                OutlinedButton(
                    onClick = controller.listener::onPickFrontDate,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_calendar_today), null)
                    Spacer(Modifier.width(8.dp))
                    Text(controller.frontDateLabel, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { controller.listener.onShiftFrontDate(1) },
                    enabled = controller.frontNextEnabled,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_chevron_right), "Next front page day")
                }
            }
        }

        controller.lastUpdatedText?.takeIf { !searchMode }?.let { value ->
            Text(
                text = value,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = sideStart, top = 4.dp, end = sideEnd),
            )
        }

        Box(Modifier.padding(start = sideStart, end = sideEnd)) {
            HeaderStatus(controller, searchMode)
        }
    }
}

@Composable
private fun MainHeader(
    controller: StoriesComposeController,
    modifier: Modifier = Modifier,
) {
    var typesExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    val settings = controller.displaySettings ?: return
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    val density = LocalDensity.current
    val selectedTextSize = with(density) {
        when {
            booleanResource(R.bool.extra_compact_stories_dropdown_selected_text) ->
                (typography.storiesDropdownSelectedSize * 0.8f).dp.toSp()
            booleanResource(R.bool.compact_stories_dropdown_selected_text) ->
                typography.storiesDropdownCompactSelectedSize.dp.toSp()
            else -> typography.storiesDropdownSelectedSize.dp.toSp()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .combinedClickable(
                        onClick = { typesExpanded = true },
                        onLongClick = null,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (controller.showingCached) {
                        "Cached stories"
                    } else {
                        controller.typeLabels.getOrNull(controller.selectedTypeIndex) ?: "Stories"
                    },
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = typography.family,
                    fontWeight = FontWeight.Bold,
                    fontSize = selectedTextSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(40.dp))
                Icon(
                    painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Choose story list",
                    modifier = Modifier.size(24.dp),
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            HarmonicDropdownMenu(
                expanded = typesExpanded,
                onDismiss = { typesExpanded = false },
                modifier = Modifier.width(196.dp),
            ) {
                controller.typeLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            HarmonicMenuText(
                                text = label,
                                color = HarmonicTheme.colors.storyNormal,
                                fontFamily = typography.family,
                                fontWeight = FontWeight.Bold,
                                fontSize = typography.storiesDropdownItemSize.sp,
                            )
                        },
                        onClick = {
                            typesExpanded = false
                            controller.listener.onTypeSelected(index)
                        },
                    )
                }
            }
        }
        IconButton(onClick = controller.listener::onOpenSearch) {
            Icon(
                painterResource(R.drawable.ic_search),
                "Search",
                tint = HarmonicTheme.colors.drawable,
            )
        }
        Box {
            IconButton(onClick = { moreExpanded = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    "More options",
                    tint = HarmonicTheme.colors.drawable,
                )
            }
            StoriesMoreMenu(controller, moreExpanded) { moreExpanded = false }
        }
    }
}

@Composable
private fun SearchHeader(
    controller: StoriesComposeController,
    sideStart: androidx.compose.ui.unit.Dp,
    sideEnd: androidx.compose.ui.unit.Dp,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Column {
        Row(
            modifier = Modifier.padding(start = sideStart, end = sideEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = controller.searchDraft,
                onValueChange = controller::updateSearchDraft,
                placeholder = { Text("Search posts") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                    controller.listener.onSearch(controller.searchDraft)
                    keyboard?.hide()
                    focusManager.clearFocus()
                }),
                shape = RoundedCornerShape(32.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .weight(1f)
                    .focusRequester(focusRequester),
            )
            IconButton(onClick = {
                keyboard?.hide()
                focusManager.clearFocus()
                controller.listener.onCloseSearch()
            }) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    "Close search",
                    tint = HarmonicTheme.colors.drawable,
                )
            }
        }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(start = sideStart + 4.dp, end = sideEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SearchOptionChip(controller.searchSortLabel, controller.searchSortLabels) {
                    controller.listener.onSearchOption(StoriesComposeController.SEARCH_OPTION_SORT, it)
                }
            }
            item {
                SearchOptionChip(controller.searchDateLabel, controller.searchDateLabels) {
                    controller.listener.onSearchOption(StoriesComposeController.SEARCH_OPTION_DATE, it)
                }
            }
            item {
                SearchOptionChip(controller.searchPointsLabel, controller.searchPointsLabels) {
                    controller.listener.onSearchOption(StoriesComposeController.SEARCH_OPTION_POINTS, it)
                }
            }
            item {
                SearchOptionChip(controller.searchCommentsLabel, controller.searchCommentsLabels) {
                    controller.listener.onSearchOption(StoriesComposeController.SEARCH_OPTION_COMMENTS, it)
                }
            }
            item {
                FilterChip(
                    selected = controller.searchOnlyClicked,
                    onClick = controller.listener::onToggleOnlyClicked,
                    label = { Text("From history") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_history), null, Modifier.size(18.dp)) },
                )
            }
        }
    }
}

@Composable
private fun SearchOptionChip(label: String, labels: List<String>, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text(label) },
            border = BorderStroke(1.dp, HarmonicTheme.colors.drawable),
        )
        HarmonicDropdownMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            modifier = Modifier.width(196.dp),
        ) {
            labels.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { HarmonicMenuText(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                    trailingIcon = {
                        Box(
                            modifier = Modifier.width(70.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            RadioButton(
                                selected = option == label,
                                onClick = null,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StoriesMoreMenu(
    controller: StoriesComposeController,
    expanded: Boolean,
    dismiss: () -> Unit,
) {
    HarmonicDropdownMenu(expanded = expanded, onDismiss = dismiss) {
        if (controller.loggedIn) {
            MoreItem("Profile", StoriesComposeController.MORE_PROFILE, controller, dismiss)
            MoreItem("Submit", StoriesComposeController.MORE_SUBMIT, controller, dismiss)
        }
        MoreItem(if (controller.loggedIn) "Log out" else "Log in", StoriesComposeController.MORE_LOGIN, controller, dismiss)
        if (controller.canCache) {
            MoreItem("Cache stories", StoriesComposeController.MORE_CACHE, controller, dismiss)
        }
        if (controller.canClearHistory) {
            MoreItem("Clear history", StoriesComposeController.MORE_CLEAR_HISTORY, controller, dismiss)
        }
        MoreItem("Settings", StoriesComposeController.MORE_SETTINGS, controller, dismiss)
    }
}

@Composable
private fun MoreItem(
    label: String,
    action: Int,
    controller: StoriesComposeController,
    dismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { HarmonicMenuText(label) },
        onClick = {
            dismiss()
            controller.listener.onMoreAction(action)
        },
    )
}

@Composable
private fun SavedFilterButton(
    label: String,
    value: Int,
    controller: StoriesComposeController,
    modifier: Modifier,
) {
    FilterChip(
        selected = controller.savedFilter == value,
        onClick = { controller.listener.onSavedFilterSelected(value) },
        label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        modifier = modifier,
    )
}

@Composable
private fun HeaderStatus(controller: StoriesComposeController, searchMode: Boolean) {
    AnimatedVisibility(
        visible = controller.loading,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator(modifier = Modifier.size(48.dp))
        }
    }
    AnimatedVisibility(
        visible = controller.loadingFailed || controller.loadingFailedServerError,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_cloud_off), null, Modifier.size(40.dp))
            Text(
                if (controller.loadingFailedServerError) "Server error" else controller.loadingFailedMessage,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = controller.listener::onRefresh) { Text("Retry") }
                if (controller.showCachedAction && !searchMode) {
                    OutlinedButton(onClick = controller.listener::onShowCached) { Text("Show cached") }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = !searchMode && controller.showEmptySavedList,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
    ) {
        EmptyState(controller.emptySavedListText, R.drawable.ic_bookmark)
    }
    AnimatedVisibility(
        visible = searchMode && controller.showEmptySearch,
        enter = fadeIn(tween(180, easing = StoriesEasing)),
        exit = fadeOut(tween(140, easing = StoriesEasing)),
    ) {
        EmptyState("No stories found", R.drawable.ic_search)
    }
}

@Composable
private fun EmptyState(text: String, icon: Int) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), null, Modifier.size(48.dp), tint = HarmonicTheme.colors.storyDisabled)
        Text(text, color = HarmonicTheme.colors.storyDisabled, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StoryLoadingItem(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.fillMaxWidth(0.78f).height(16.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth(0.5f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

@Composable
private fun SavedCommentStoryItem(
    story: Story,
    settings: StoryDisplaySettings,
    onStory: () -> Unit,
    onReplies: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typography = rememberContentTypography(settings.font, settings.storyTextSize)
    Surface(
        color = if (settings.cardStyle) MaterialTheme.colorScheme.surfaceContainerHigh else HarmonicTheme.colors.background,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = if (settings.cardStyle) 1.dp else 0.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "On “${story.commentMasterTitle ?: "Loading story…"}”",
                fontFamily = typography.family,
                fontWeight = FontWeight.Bold,
                fontSize = typography.storyTitleSize.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.combinedClickable(onClick = onStory, onLongClick = null),
            )
            Text(
                text = runCatching { AnnotatedString.fromHtml(story.text.orEmpty()) }
                    .getOrElse { AnnotatedString(story.text.orEmpty()) },
                fontFamily = typography.family,
                fontSize = settings.commentTextSize.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onStory) { Text("Story") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onReplies) {
                    Icon(painterResource(R.drawable.ic_comment), null)
                    Spacer(Modifier.width(6.dp))
                    Text("Replies")
                }
            }
        }
    }
}

private fun Story.toUiModel(position: Int, settings: StoryDisplaySettings): StoryItemUiModel {
    val fullDomain = runCatching { getDisplayDomain(true) }.getOrDefault("")
    val shortDomain = runCatching { getDisplayDomain(false) }.getOrDefault(fullDomain)
    val favicon = runCatching { FaviconLoader.getFaviconUrl(url, settings.faviconProvider) }.getOrNull()
    return StoryItemUiModel(
        index = "${position + 1}.",
        title = title ?: if (loadingFailed) "Tap to retry" else "Loading…",
        summary = linkSummaryDescription ?: summary.orEmpty(),
        points = score,
        domain = fullDomain,
        domainWithoutTopLevel = shortDomain,
        age = getTimeFormatted(),
        commentCount = descendants,
        faviconRes = R.drawable.ic_public,
        previewImageRes = null,
        faviconUrl = favicon,
        previewImageUrl = previewImageUrl,
        faviconTintArgb = faviconTintColor.takeIf { faviconTintColorLoaded },
        previewImageTintArgb = previewImageTintColor.takeIf { previewImageTintColorLoaded },
    )
}

private fun StoryDisplaySettings.toItemStyle(story: Story) = StoryItemStyle(
    previewImageMode = previewImageMode,
    borderlessLargeImage = borderlessLargePreviewImage,
    compact = compactView,
    showSummary = showSummary && !story.linkSummaryDescription.isNullOrBlank(),
    showFavicon = thumbnails,
    showPoints = showPoints,
    compactPoints = compactPoints,
    includeTopLevelDomain = includeTopLevelDomain,
    showCommentCount = showCommentsCount,
    showIndex = showIndex,
    commentsOnLeft = leftAlign,
    tintCard = tintCardUsingPreview,
    cardStyle = cardStyle,
    useHotnessIcon = hotness > 0 && story.score + story.descendants > hotness,
    preferredFont = font,
    textSize = storyTextSize,
    dimmed = grayOutClicked && story.clicked,
)

@Preview(name = "Phone", device = Devices.PIXEL_7, showBackground = true)
@Preview(name = "Fold inner", widthDp = 673, heightDp = 841, showBackground = true)
@Preview(name = "Tablet pane", widthDp = 600, heightDp = 960, showBackground = true)
@Composable
private fun StoryItemFormFactorPreview() {
    HarmonicTheme {
        StoryItem(
            model = SettingsStoryPreviewModel,
            style = StoryItemStyle(
                previewImageMode = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL,
                borderlessLargeImage = false,
                compact = false,
                showSummary = true,
                showFavicon = true,
                showPoints = true,
                compactPoints = false,
                includeTopLevelDomain = true,
                showCommentCount = true,
                showIndex = true,
                commentsOnLeft = false,
                tintCard = true,
                cardStyle = false,
                useHotnessIcon = false,
                preferredFont = "googlesansflexrounded",
                textSize = SettingsUtils.DEFAULT_STORY_TEXT_SIZE,
            ),
        )
    }
}
