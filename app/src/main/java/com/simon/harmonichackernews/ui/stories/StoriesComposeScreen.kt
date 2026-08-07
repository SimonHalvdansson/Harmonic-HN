@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.simon.harmonichackernews.ui.stories

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.FaviconLoader
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt

/**
 * Compose presentation bridge for the stories screen. The coordinator remains the data/network
 * controller during this migration; adapter notifications are converted to immutable snapshots.
 */
class StoriesComposeController private constructor(
    private val defaultStoryHeightPx: Int,
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
    internal var frontDatePickerRequest by mutableStateOf<FrontDatePickerRequest?>(null)
        private set
    internal var loggedIn by mutableStateOf(false)
        private set
    internal var canCache by mutableStateOf(false)
        private set
    internal var canClearHistory by mutableStateOf(false)
        private set
    internal var cacheProgressVisible by mutableStateOf(false)
        private set
    internal var cacheProgress by mutableIntStateOf(0)
        private set
    internal var cacheProgressMax by mutableIntStateOf(1)
        private set
    internal var cacheProgressStatus by mutableStateOf("Caching stories")
        private set
    internal var contentInsetStartPx by mutableIntStateOf(0)
        private set
    internal var predictiveBackActive by mutableStateOf(false)
        private set
    internal var predictiveBackProgress by mutableFloatStateOf(0f)
        private set
    internal var suppressSearchAutoFocus by mutableStateOf(false)
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
    internal var storyPreviewOverlay by mutableStateOf<StoryPreviewOverlayState?>(null)
        private set
    internal var storyPreviewDismissRequest by mutableIntStateOf(0)
        private set
    internal var storyPreviewPredictiveBackProgress by mutableFloatStateOf(0f)
        private set
    internal var storyPreviewPredictiveBackEdge by mutableIntStateOf(0)
        private set
    internal var storyPreviewPredictiveBackTouchY by mutableFloatStateOf(0f)
        private set
    internal var storyPreviewPredictiveBackSettleRequest by
        mutableStateOf<PredictiveBackSettleRequest?>(null)
        private set
    internal var storyPreviewVoteLoadingId by mutableIntStateOf(-1)
        private set
    internal var storyPreviewFavoriteLoadingId by mutableIntStateOf(-1)
        private set
    internal var visibleStoryPreviewId by mutableIntStateOf(-1)
        private set

    private var requestSerial = 0
    private val storyBounds = mutableMapOf<Int, Rect>()
    private val storyRevisions = mutableMapOf<Int, MutableIntState>()
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
        cacheProgressVisible: Boolean,
        cacheProgress: Int,
        cacheProgressMax: Int,
        cacheProgressStatus: String,
        contentInsetStartPx: Int,
    ) {
        val enteringSearch = !this.searching && searching
        this.mainStories = mainStories.toList()
        this.searchStories = searchStories.toList()
        this.displaySettings = displaySettings
        this.typeLabels = typeLabels.toList()
        this.selectedTypeIndex = selectedTypeIndex
        this.searching = searching
        this.lastSearch = lastSearch
        if (enteringSearch) {
            searchDraft = lastSearch
            suppressSearchAutoFocus = false
        }
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
        this.cacheProgressVisible = cacheProgressVisible
        this.cacheProgress = cacheProgress
        this.cacheProgressMax = cacheProgressMax.coerceAtLeast(1)
        this.cacheProgressStatus = cacheProgressStatus
        this.contentInsetStartPx = contentInsetStartPx
        val currentStoryIds = buildSet(mainStories.size + searchStories.size) {
            mainStories.forEach { add(it.id) }
            searchStories.forEach { add(it.id) }
        }
        storyRevisions.keys.retainAll(currentStoryIds)
        contentVersion++
    }

    internal fun invalidateStory(storyId: Int) {
        val revision = storyRevisions.getOrPut(storyId) { mutableIntStateOf(0) }
        revision.intValue++
    }

    internal fun storyRevision(storyId: Int): Int =
        storyRevisions.getOrPut(storyId) { mutableIntStateOf(0) }.intValue

    fun updateSearchDraft(value: String) {
        searchDraft = value
    }

    fun cacheStories(storyCount: Int) {
        listener.onCacheStoriesConfirmed(
            SettingsUtils.sanitizeStoriesToCache(storyCount),
        )
    }

    fun showFrontDatePicker(initialDay: Long, earliestDay: Long, latestDay: Long) {
        frontDatePickerRequest = FrontDatePickerRequest(
            initialDay = initialDay.coerceIn(earliestDay, latestDay),
            earliestDay = earliestDay,
            latestDay = latestDay,
        )
    }

    internal fun dismissFrontDatePicker() {
        frontDatePickerRequest = null
    }

    internal fun selectFrontDate(day: Long) {
        val request = frontDatePickerRequest ?: return
        frontDatePickerRequest = null
        listener.onFrontDateSelected(day.coerceIn(request.earliestDay, request.latestDay))
    }

    fun beginPredictiveBack(progress: Float) {
        predictiveBackSettleRequest = null
        suppressSearchAutoFocus = true
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
        if (request?.target == 0f) suppressSearchAutoFocus = false
    }

    fun requestScrollBy(dy: Int) {
        if (dy != 0) {
            headerPinnedForPreview = true
            val pendingDy = scrollByRequest?.dy ?: 0
            scrollByRequest = ScrollByRequest(++requestSerial, pendingDy + dy)
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

    fun showStoryPreview(
        stories: List<Story>,
        sourcePositions: IntArray,
        cardColors: IntArray,
        openedStoryId: Int,
    ) {
        if (stories.isEmpty() || stories.size != sourcePositions.size || stories.size != cardColors.size) {
            return
        }
        val initialPage = stories.indexOfFirst { it.id == openedStoryId }.takeIf { it >= 0 } ?: 0
        requestStopStoryPreviewScroll()
        storyPreviewDismissRequest = 0
        storyPreviewPredictiveBackProgress = 0f
        storyPreviewPredictiveBackSettleRequest = null
        storyPreviewOverlay = StoryPreviewOverlayState(
            stories = stories.toList(),
            sourcePositions = sourcePositions.toList(),
            cardColors = cardColors.toList(),
            initialPage = initialPage,
        )
        visibleStoryPreviewId = stories[initialPage].id
        suppressedStoryIds.value = setOf(stories[initialPage].id)
        listener.onStoryPreviewVisibilityChanged(true)
    }

    fun restoreStoryPreview(
        stories: List<Story>,
        sourcePositions: IntArray,
        cardColors: IntArray,
        openedStoryId: Int,
    ) = showStoryPreview(stories, sourcePositions, cardColors, openedStoryId)

    fun isStoryPreviewShowing(): Boolean = storyPreviewOverlay != null

    fun getVisibleStoryPreviewId(): Int = visibleStoryPreviewId

    fun requestDismissStoryPreview() {
        if (storyPreviewOverlay == null || storyPreviewDismissRequest != 0) return
        storyPreviewDismissRequest = ++requestSerial
    }

    fun completeStoryPreviewDismiss() {
        if (storyPreviewOverlay == null) return
        storyPreviewOverlay = null
        storyPreviewDismissRequest = 0
        storyPreviewPredictiveBackProgress = 0f
        storyPreviewPredictiveBackSettleRequest = null
        storyPreviewVoteLoadingId = -1
        storyPreviewFavoriteLoadingId = -1
        visibleStoryPreviewId = -1
        storyPagingAlphas.clear()
        suppressedStoryIds.value = emptySet()
        headerPinnedForPreview = false
        listener.onStoryPreviewVisibilityChanged(false)
    }

    fun startStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        if (storyPreviewOverlay == null || storyPreviewDismissRequest != 0) return
        storyPreviewPredictiveBackSettleRequest = null
        storyPreviewPredictiveBackEdge = edge
        storyPreviewPredictiveBackTouchY = touchY
        storyPreviewPredictiveBackProgress = progress.coerceIn(0f, 1f)
    }

    fun updateStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        startStoryPreviewPredictiveBack(progress, edge, touchY)
    }

    fun cancelStoryPreviewPredictiveBack() {
        if (storyPreviewOverlay == null || storyPreviewPredictiveBackProgress <= 0f) return
        storyPreviewPredictiveBackSettleRequest = PredictiveBackSettleRequest(
            serial = ++requestSerial,
            target = 0f,
        )
    }

    fun isStoryPreviewPredictiveBackActive(): Boolean =
        storyPreviewOverlay != null &&
            (storyPreviewPredictiveBackProgress > 0f ||
                storyPreviewPredictiveBackSettleRequest != null)

    fun commitStoryPreviewPredictiveBack() {
        if (storyPreviewOverlay == null) return
        requestDismissStoryPreview()
    }

    internal fun finishStoryPreviewPredictiveBackSettle(
        request: PredictiveBackSettleRequest,
    ) {
        if (storyPreviewPredictiveBackSettleRequest != request) return
        storyPreviewPredictiveBackProgress = request.target
        storyPreviewPredictiveBackSettleRequest = null
    }

    internal fun sourceBoundsForStory(storyId: Int): Rect? =
        storyBounds[storyId]

    internal fun onStoryPreviewPagePosition(
        lowerPage: Int,
        upperPage: Int,
        offset: Float,
    ) {
        val state = storyPreviewOverlay ?: return
        val lower = state.stories.getOrNull(lowerPage) ?: return
        val upper = state.stories.getOrNull(upperPage) ?: lower
        suppressedStoryIds.value = emptySet()
        setStoryPagingAlphas(
            lower.id,
            if (upperPage == lowerPage) 0f else offset.coerceIn(0f, 1f),
            if (upperPage == lowerPage) -1 else upper.id,
            if (upperPage == lowerPage) 1f else 1f - offset.coerceIn(0f, 1f),
        )
    }

    internal fun onStoryPreviewPageSettled(page: Int) {
        val state = storyPreviewOverlay ?: return
        visibleStoryPreviewId = state.stories.getOrNull(page)?.id ?: return
    }

    internal fun onStoryPreviewNavigate(page: Int, showWebsite: Boolean) {
        val state = storyPreviewOverlay ?: return
        val story = state.stories.getOrNull(page) ?: return
        val sourcePosition = state.sourcePositions.getOrNull(page) ?: return
        if (!listener.onStoryPreviewNavigate(story, sourcePosition, showWebsite)) {
            requestDismissStoryPreview()
        }
    }

    internal fun onStoryPreviewAction(page: Int, action: Int) {
        val state = storyPreviewOverlay ?: return
        val story = state.stories.getOrNull(page) ?: return
        val sourcePosition = state.sourcePositions.getOrNull(page) ?: return
        if (action == STORY_PREVIEW_ACTION_VOTE) storyPreviewVoteLoadingId = story.id
        if (action == STORY_PREVIEW_ACTION_FAVORITE) storyPreviewFavoriteLoadingId = story.id
        listener.onStoryPreviewAction(story, sourcePosition, action)
        if (action == STORY_PREVIEW_ACTION_READ || action == STORY_PREVIEW_ACTION_BOOKMARK) {
            contentVersion++
        }
    }

    fun finishStoryPreviewAction(storyId: Int, action: Int) {
        if (action == STORY_PREVIEW_ACTION_VOTE && storyPreviewVoteLoadingId == storyId) {
            storyPreviewVoteLoadingId = -1
        }
        if (action == STORY_PREVIEW_ACTION_FAVORITE && storyPreviewFavoriteLoadingId == storyId) {
            storyPreviewFavoriteLoadingId = -1
        }
        contentVersion++
    }

    private fun requestStopStoryPreviewScroll() {
        listener.onStoryPreviewStopScroll()
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
                ?.height
                ?.roundToInt()
                ?.coerceAtLeast(1)
                ?: averageStoryHeight()
        }
    }

    private fun averageStoryHeight(): Int {
        val heights = storyBounds.values.mapNotNull { bounds ->
            bounds.height.roundToInt().takeIf { height -> height > 0 }
        }
        return if (heights.isEmpty()) {
            defaultStoryHeightPx
        } else {
            heights.sum() / heights.size
        }
    }

    internal fun updateStoryBounds(storyId: Int, bounds: Rect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        if (storyBounds[storyId] != bounds) storyBounds[storyId] = bounds
    }

    internal fun isStorySuppressed(storyId: Int): Boolean = storyId in suppressedStoryIds.value

    data class ScrollByRequest(val serial: Int, val dy: Int)

    data class PredictiveBackSettleRequest(val serial: Int, val target: Float)

    data class FrontDatePickerRequest(
        val initialDay: Long,
        val earliestDay: Long,
        val latestDay: Long,
    )

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
        fun onFrontDateSelected(day: Long)
        fun onMoreAction(action: Int)
        fun onCacheStoriesConfirmed(storyCount: Int)
        fun onLinkClick(story: Story)
        fun onCommentClick(story: Story)
        fun onCommentStoryClick(story: Story)
        fun onCommentRepliesClick(story: Story)
        fun onStoryLongClick(story: Story)
        fun onVisibleStoryRange(lastVisibleIndex: Int)
        fun onStoryPreviewStopScroll()
        fun onStoryPreviewVisibilityChanged(showing: Boolean)
        fun onStoryPreviewNavigate(story: Story, position: Int, showWebsite: Boolean): Boolean
        fun onStoryPreviewAction(story: Story, position: Int, action: Int)
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

        const val STORY_PREVIEW_ACTION_VOTE = 0
        const val STORY_PREVIEW_ACTION_READ = 1
        const val STORY_PREVIEW_ACTION_BOOKMARK = 2
        const val STORY_PREVIEW_ACTION_FAVORITE = 3

        @JvmStatic
        fun create(
            activity: ComponentActivity,
            listener: Listener,
        ): StoriesComposeController {
            return StoriesComposeController(
                defaultStoryHeightPx = (96f * activity.resources.displayMetrics.density)
                    .roundToInt(),
                listener = listener,
            )
        }
    }
}

internal data class StoryPreviewOverlayState(
    val stories: List<Story>,
    val sourcePositions: List<Int>,
    val cardColors: List<Int>,
    val initialPage: Int,
)

private val StoriesEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

@Composable
internal fun StoriesScreen(controller: StoriesComposeController) {
    val settings = controller.displaySettings ?: return
    val mainState = rememberLazyListState()
    val searchState = rememberLazyListState()
    val progress = controller.predictiveBackProgress.coerceIn(0f, 1f)
    val predictive = controller.predictiveBackActive
    val standardSearchProgress by animateFloatAsState(
        targetValue = if (controller.searching) 1f else 0f,
        animationSpec = if (!controller.searching && controller.suppressSearchAutoFocus) {
            androidx.compose.animation.core.snap()
        } else {
            tween(180, easing = StoriesEasing)
        },
        label = "stories search transition",
    )

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
        val predictiveSearchFade = (progress / 0.5f).coerceIn(0f, 1f)
        val predictiveMainFade = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val searchAlpha = if (predictive) 1f - predictiveSearchFade else standardSearchProgress
        val mainAlpha = if (predictive) predictiveMainFade else 1f - standardSearchProgress
        val mainActive = !predictive && !controller.searching
        val searchActive = !predictive && controller.searching

        StableStoriesLayer(
            active = mainActive,
            modifier = Modifier
                .zIndex(if (mainActive) 1f else 0f)
                .graphicsLayer {
                    alpha = mainAlpha
                    translationY = if (predictive) {
                        24.dp.toPx() * (1f - predictiveMainFade)
                    } else {
                        24.dp.toPx() * standardSearchProgress
                    }
                },
        ) {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.mainStories,
                listState = mainState,
                searchMode = false,
            )
        }

        StableStoriesLayer(
            active = searchActive,
            modifier = Modifier
                .zIndex(if (searchActive) 1f else 0f)
                .graphicsLayer {
                    alpha = searchAlpha
                    translationY = if (predictive) {
                        24.dp.toPx() * predictiveSearchFade
                    } else {
                        24.dp.toPx() * (1f - standardSearchProgress)
                    }
                },
        ) {
            StoriesList(
                controller = controller,
                settings = settings,
                stories = controller.searchStories,
                listState = searchState,
                searchMode = true,
            )
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

    controller.frontDatePickerRequest?.let { request ->
        FrontPageDatePickerDialog(
            request = request,
            onDismiss = controller::dismissFrontDatePicker,
            onSelected = controller::selectFrontDate,
        )
    }
}

@Composable
private fun StableStoriesLayer(
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        content()
        if (!active) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    },
            )
        }
    }
}

@Composable
private fun FrontPageDatePickerDialog(
    request: StoriesComposeController.FrontDatePickerRequest,
    onDismiss: () -> Unit,
    onSelected: (Long) -> Unit,
) {
    val selectableDates = remember(request.earliestDay, request.latestDay) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis in request.earliestDay..request.latestDay
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = request.initialDay,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onSelected) },
                enabled = state.selectedDateMillis != null,
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        FrontPageDatePickerContent(state = state)
    }
}

@Composable
private fun FrontPageDatePickerContent(
    state: androidx.compose.material3.DatePickerState,
) {
    DatePicker(
        state = state,
        title = {
            Text(
                text = "Select front page day",
                modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun FrontPageDatePickerContentPreview() {
    HarmonicTheme {
        FrontPageDatePickerContent(
            state = rememberDatePickerState(initialSelectedDateMillis = 1_700_000_000_000L),
        )
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
    val context = LocalContext.current
    val visibleCount = if (searchMode) controller.searchVisibleCount else controller.mainVisibleCount
    val visibleStories = remember(stories, visibleCount, controller.contentVersion) {
        stories.take(visibleCount.coerceAtLeast(0).coerceAtMost(stories.size))
    }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val startInset = with(density) { controller.contentInsetStartPx.toDp() }
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
                    start = startInset + safeStart,
                    top = headerHeight,
                    end = safeEnd,
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
                        var revealed by remember(story.id) { mutableStateOf(false) }
                        LaunchedEffect(story.id) { revealed = true }
                        val revealAlpha by animateFloatAsState(
                            targetValue = if (revealed) 1f else 0f,
                            animationSpec = tween(220, easing = StoriesEasing),
                            label = "loaded story reveal",
                        )
                        val contentVersion = controller.contentVersion
                        val storyRevision = controller.storyRevision(story.id)
                        val model = remember(story, index, settings, contentVersion, storyRevision) {
                            story.toUiModel(index, settings, context)
                        }
                        val style = remember(story, settings, contentVersion, storyRevision) {
                            settings.toItemStyle(story)
                        }
                        val itemModifier = Modifier
                            .animateItem()
                            .graphicsLayer(
                                alpha = (if (suppressed) 0f else pagingAlpha) * revealAlpha,
                            )
                        StoryItem(
                            model = model,
                            style = style,
                            modifier = itemModifier,
                            listItem = true,
                            onLinkClick = { controller.listener.onLinkClick(story) },
                            onLinkLongClick = { controller.listener.onStoryLongClick(story) },
                            onCommentClick = { controller.listener.onCommentClick(story) },
                            onBoundsChanged = { bounds ->
                                controller.updateStoryBounds(story.id, bounds)
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
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val safeStart = safeDrawingPadding.calculateStartPadding(layoutDirection)
    val safeEnd = safeDrawingPadding.calculateEndPadding(layoutDirection)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
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
        val sideStart = 16.dp + startInset + safeStart
        val sideEnd = 16.dp + safeEnd
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

        AnimatedVisibility(
            visible = !searchMode && controller.cacheProgressVisible,
            enter = fadeIn(tween(180, easing = StoriesEasing)) + expandVertically(),
            exit = fadeOut(tween(140, easing = StoriesEasing)) + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sideStart, top = 8.dp, end = sideEnd),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = controller.cacheProgressStatus,
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 12.sp,
                )
                LinearProgressIndicator(
                    progress = {
                        controller.cacheProgress.toFloat() / controller.cacheProgressMax
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
                    .clip(RoundedCornerShape(4.dp))
                    .combinedClickable(
                        onClick = { typesExpanded = true },
                        onLongClick = null,
                    )
                    .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
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
    LaunchedEffect(controller.searching, controller.suppressSearchAutoFocus) {
        if (controller.searching && !controller.suppressSearchAutoFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
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
    HarmonicDropdownMenu(
        expanded = expanded,
        onDismiss = dismiss,
        modifier = Modifier.width(196.dp),
    ) {
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

private fun Story.toUiModel(
    position: Int,
    settings: StoryDisplaySettings,
    context: android.content.Context,
): StoryItemUiModel {
    val fullDomain = runCatching { getDisplayDomain(true) }.getOrNull().orEmpty()
    val shortDomain = runCatching { getDisplayDomain(false) }.getOrNull().orEmpty()
    val favicon = runCatching { FaviconLoader.getFaviconUrl(url, settings.faviconProvider) }.getOrNull()
    val tintBaseColor = PreviewImageTintUtils.getTintBaseColor(context)
    val paletteTintMode = SettingsUtils.getPaletteTintConfigKey(settings.paletteTintMode)
    val currentPreviewTint = PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(
        this,
        tintBaseColor,
        paletteTintMode,
    )
    val currentFaviconTint = faviconTintColorLoaded &&
        faviconTintBaseColor == tintBaseColor &&
        PreviewImageTintUtils.isTintModeCurrent(faviconTintMode, paletteTintMode) &&
        faviconTintSourceUrl == favicon
    return StoryItemUiModel(
        index = "${position + 1}.",
        title = title ?: if (loadingFailed) "Tap to retry" else "Loading…",
        summary = linkSummaryDescription ?: summary.orEmpty(),
        points = score,
        domain = fullDomain,
        domainWithoutTopLevel = shortDomain,
        age = timeFormatted,
        commentCount = descendants,
        faviconRes = R.drawable.ic_public,
        previewImageRes = null,
        faviconUrl = favicon,
        previewImageUrl = previewImageUrl,
        faviconTintArgb = faviconTintColor.takeIf { currentFaviconTint },
        previewImageTintArgb = previewImageTintColor.takeIf { currentPreviewTint },
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
    paletteTintConfigKey = paletteTintMode,
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
