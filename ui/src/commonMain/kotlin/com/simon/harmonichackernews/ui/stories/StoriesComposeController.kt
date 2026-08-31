package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StorySearchOption
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.presentation.StoryPredictiveBackSettleRequest
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState
import com.simon.harmonichackernews.presentation.StoryScrollRequest
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.StoryCachePreferences
import org.jetbrains.compose.resources.DrawableResource

/** One immutable rendering snapshot shared by every stories-screen host. */
data class StoriesScreenState(
    val mainStories: List<StoryListItemSnapshot> = emptyList(),
    val searchStories: List<StoryListItemSnapshot> = emptyList(),
    val previewResources: Map<Int, StoryPreviewResourceState> = emptyMap(),
    val previewVoteLoadingIds: Set<Int> = emptySet(),
    val previewFavoriteLoadingIds: Set<Int> = emptySet(),
    val displaySettings: StoryDisplaySettings? = null,
    val typeLabels: List<String> = emptyList(),
    val selectedTypeIndex: Int = 0,
    val searching: Boolean = false,
    val lastSearch: String = "",
    val searchSortLabel: String = "Relevance",
    val searchDateLabel: String = "All time",
    val searchPointsLabel: String = "Any points",
    val searchCommentsLabel: String = "Any comments",
    val searchSortLabels: List<String> = emptyList(),
    val searchDateLabels: List<String> = emptyList(),
    val searchPointsLabels: List<String> = emptyList(),
    val searchCommentsLabels: List<String> = emptyList(),
    val searchOnlyClicked: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingFailed: Boolean = false,
    val loadingFailedServerError: Boolean = false,
    val loadingFailedMessage: String = "Loading failed",
    val showingCached: Boolean = false,
    val showCachedAction: Boolean = false,
    val showEmptySavedList: Boolean = false,
    val emptySavedListText: String = "No saved stories",
    val emptySavedListIcon: DrawableResource = Res.drawable.ic_bookmark,
    val showEmptySearch: Boolean = false,
    val showUpdate: Boolean = false,
    val lastUpdatedText: String? = null,
    val showLoadMore: Boolean = false,
    val loadMoreLoading: Boolean = false,
    val mainVisibleCount: Int = Int.MAX_VALUE,
    val searchVisibleCount: Int = Int.MAX_VALUE,
    val showSavedFilter: Boolean = false,
    val savedFilter: SavedItemFilter = SavedItemFilter.BOTH,
    val showFrontDate: Boolean = false,
    val frontDateLabel: String = "",
    val frontPreviousEnabled: Boolean = false,
    val frontNextEnabled: Boolean = false,
    val loggedIn: Boolean = false,
    val canCache: Boolean = false,
    val canClearHistory: Boolean = false,
    val cacheProgressVisible: Boolean = false,
    val cacheProgress: Int = 0,
    val cacheProgressMax: Int = 1,
    val cacheProgressStatus: String = "Caching stories",
    val contentInsetStartPx: Int = 0,
)

private fun StoriesScreenState.withoutContent(): StoriesScreenState = copy(
    mainStories = emptyList(),
    searchStories = emptyList(),
    previewResources = emptyMap(),
    displaySettings = null,
)

class StoriesComposeController private constructor(
    defaultStoryHeightPx: Int,
    private val savedItemState: SavedItemStateReader,
    val listener: Listener,
) {
    private var mainStoriesState by mutableStateOf<List<StoryListItemSnapshot>>(emptyList())
    private var searchStoriesState by mutableStateOf<List<StoryListItemSnapshot>>(emptyList())
    private var previewResourcesSnapshot: Map<Int, StoryPreviewResourceState> = emptyMap()
    private val previewResourceStates = mutableMapOf<Int, MutableState<StoryPreviewResourceState?>>()
    private val previewImageKnownAbsentIds = mutableSetOf<Int>()
    private var displaySettingsState by mutableStateOf<StoryDisplaySettings?>(null)
    private var shellState by mutableStateOf(StoriesScreenState().withoutContent())

    /** True only while the current refresh was initiated by the pull-to-refresh gesture. */
    var pullToRefreshInProgress by mutableStateOf(false)
        private set

    val mainStories: List<StoryListItemSnapshot> get() = mainStoriesState
    val searchStories: List<StoryListItemSnapshot> get() = searchStoriesState
    val previewResources: Map<Int, StoryPreviewResourceState> get() = previewResourcesSnapshot
    fun previewResource(storyId: Int): StoryPreviewResourceState? =
        previewResourceStates.getOrPut(storyId) {
            mutableStateOf(previewResourcesSnapshot[storyId])
        }.value
    val displaySettings: StoryDisplaySettings? get() = displaySettingsState
    val typeLabels: List<String> get() = shellState.typeLabels
    val selectedTypeIndex: Int get() = shellState.selectedTypeIndex
    val lastSearch: String get() = shellState.lastSearch
    val searchSortLabel: String get() = shellState.searchSortLabel
    val searchDateLabel: String get() = shellState.searchDateLabel
    val searchPointsLabel: String get() = shellState.searchPointsLabel
    val searchCommentsLabel: String get() = shellState.searchCommentsLabel
    val searchSortLabels: List<String> get() = shellState.searchSortLabels
    val searchDateLabels: List<String> get() = shellState.searchDateLabels
    val searchPointsLabels: List<String> get() = shellState.searchPointsLabels
    val searchCommentsLabels: List<String> get() = shellState.searchCommentsLabels
    val searchOnlyClicked: Boolean get() = shellState.searchOnlyClicked
    val loading: Boolean get() = shellState.loading
    val refreshing: Boolean get() = shellState.refreshing
    val loadingFailed: Boolean get() = shellState.loadingFailed
    val loadingFailedServerError: Boolean get() = shellState.loadingFailedServerError
    val loadingFailedMessage: String get() = shellState.loadingFailedMessage
    val showingCached: Boolean get() = shellState.showingCached
    val showCachedAction: Boolean get() = shellState.showCachedAction
    val showEmptySavedList: Boolean get() = shellState.showEmptySavedList
    val emptySavedListText: String get() = shellState.emptySavedListText
    val emptySavedListIcon: DrawableResource get() = shellState.emptySavedListIcon
    val showEmptySearch: Boolean get() = shellState.showEmptySearch
    val showUpdate: Boolean get() = shellState.showUpdate
    val lastUpdatedText: String? get() = shellState.lastUpdatedText
    val showLoadMore: Boolean get() = shellState.showLoadMore
    val loadMoreLoading: Boolean get() = shellState.loadMoreLoading
    val mainVisibleCount: Int get() = shellState.mainVisibleCount
    val searchVisibleCount: Int get() = shellState.searchVisibleCount
    val showSavedFilter: Boolean get() = shellState.showSavedFilter
    val savedFilter: SavedItemFilter get() = shellState.savedFilter
    val showFrontDate: Boolean get() = shellState.showFrontDate
    val frontDateLabel: String get() = shellState.frontDateLabel
    val frontPreviousEnabled: Boolean get() = shellState.frontPreviousEnabled
    val frontNextEnabled: Boolean get() = shellState.frontNextEnabled
    val loggedIn: Boolean get() = shellState.loggedIn
    val canCache: Boolean get() = shellState.canCache
    val canClearHistory: Boolean get() = shellState.canClearHistory
    val cacheProgressVisible: Boolean get() = shellState.cacheProgressVisible
    val cacheProgress: Int get() = shellState.cacheProgress
    val cacheProgressMax: Int get() = shellState.cacheProgressMax
    val cacheProgressStatus: String get() = shellState.cacheProgressStatus
    val contentInsetStartPx: Int get() = shellState.contentInsetStartPx
    var contentVersion by mutableIntStateOf(0)
        private set
    var scrollToTopRequestVersion by mutableIntStateOf(0)
        private set
    private var scrollToTopAfterRefresh = false
    private var refreshInProgressObserved = false
    var tapToUpdateExitRequestVersion by mutableIntStateOf(0)
        private set
    var tapToUpdateExitInProgress by mutableStateOf(false)
        private set
    var tapToUpdateRefreshStarted by mutableStateOf(false)
        private set
    var headerMenuVisible by mutableStateOf(false)
        private set
    var headerMenuDismissRequestVersion by mutableIntStateOf(0)
        private set

    private val interactionStore = StoriesInteractionStore(defaultStoryHeightPx)
    private var interactionState by mutableStateOf(interactionStore.state)
    private var scrollByRequestState by mutableStateOf<StoryScrollRequest?>(null)
    private val storyBounds = mutableMapOf<Int, Rect>()
    private val storyPreviewSourceGeometries = mutableMapOf<Int, StoryPreviewSourceGeometry>()
    private val storyRevisions = mutableMapOf<Int, MutableIntState>()
    private val storyPreviewReadStates = mutableMapOf<Int, MutableState<Boolean>>()
    private val storyPagingAlphaStates = mutableMapOf<Int, MutableFloatState>()
    private var pagingLowerStoryId = -1
    private var pagingUpperStoryId = -1
    private var sourceCoveredByStoryPreviewTransition by mutableStateOf(false)

    val searching: Boolean get() = interactionState.searching
    val searchDraft: String get() = interactionState.searchDraft
    val frontDatePickerRequest: StoryFrontDatePickerRequest?
        get() = interactionState.frontDatePickerRequest
    val predictiveBackActive: Boolean get() = interactionState.predictiveBackActive
    val predictiveBackProgress: Float get() = interactionState.predictiveBackProgress
    val suppressSearchAutoFocus: Boolean get() = interactionState.suppressSearchAutoFocus
    val predictiveBackSettleRequest: StoryPredictiveBackSettleRequest?
        get() = interactionState.predictiveBackSettleRequest
    val scrollByRequest: StoryScrollRequest? get() = scrollByRequestState
    val storyPagingAlphas: Map<Int, Float> get() = interactionState.storyPagingAlphas
    val storyPreviewOverlay: StoryPreviewOverlayState?
        get() = interactionState.storyPreviewOverlay
    val storyPreviewDismissRequest: Int
        get() = interactionState.storyPreviewDismissRequestVersion
    val storyPreviewPredictiveBackProgress: Float
        get() = interactionState.storyPreviewPredictiveBackProgress
    val storyPreviewPredictiveBackEdge: Int
        get() = interactionState.storyPreviewPredictiveBackEdge
    val storyPreviewPredictiveBackSettleRequest: StoryPredictiveBackSettleRequest?
        get() = interactionState.storyPreviewPredictiveBackSettleRequest
    fun isStoryPreviewVoteLoading(storyId: Int): Boolean =
        storyId in interactionState.storyPreviewVoteLoadingIds
    fun isStoryPreviewFavoriteLoading(storyId: Int): Boolean =
        storyId in interactionState.storyPreviewFavoriteLoadingIds
    val visibleStoryPreviewId: Int get() = interactionState.visibleStoryPreviewId

    private fun syncInteractionState() {
        interactionState = interactionStore.state
        scrollByRequestState = interactionStore.state.scrollRequest
    }

    fun updateContent(state: StoriesScreenState) {
        // StoriesScreenStateFactory receives immutable store snapshots. Preserve those list/map
        // instances so header-only publications don't traverse and copy the entire feed on the UI
        // thread, and so row-level remember keys can distinguish unchanged content cheaply.
        val normalized = if (state.cacheProgressMax > 0) {
            state
        } else {
            state.copy(cacheProgressMax = 1)
        }
        if (scrollToTopAfterRefresh && normalized.refreshing) {
            refreshInProgressObserved = true
        }
        val requestedRefreshCompleted = scrollToTopAfterRefresh &&
            refreshInProgressObserved && !normalized.refreshing
        val listsChanged = mainStoriesState != normalized.mainStories ||
            searchStoriesState != normalized.searchStories
        val interactionContentChanged = listsChanged ||
            shellState.searching != normalized.searching ||
            shellState.lastSearch != normalized.lastSearch
        val previewActionStateChanged =
            shellState.previewVoteLoadingIds != normalized.previewVoteLoadingIds ||
                shellState.previewFavoriteLoadingIds != normalized.previewFavoriteLoadingIds
        mainStoriesState = normalized.mainStories
        searchStoriesState = normalized.searchStories
        val currentStoryIds = if (listsChanged) {
            buildSet<Int>(normalized.mainStories.size + normalized.searchStories.size) {
                normalized.mainStories.forEach { add(it.id) }
                normalized.searchStories.forEach { add(it.id) }
            }
        } else {
            null
        }
        displaySettingsState = normalized.displaySettings
        updatePreviewResources(normalized.previewResources, currentStoryIds)
        shellState = normalized.withoutContent()
        if (interactionContentChanged) {
            interactionStore.updateContent(
                normalized.mainStories,
                normalized.searchStories,
                normalized.searching,
                normalized.lastSearch,
            )
        }
        if (previewActionStateChanged) {
            interactionStore.reconcileStoryPreviewActionLoading(
                normalized.previewVoteLoadingIds,
                normalized.previewFavoriteLoadingIds,
            )
        }
        val hasRetainedStoryUiState = storyBounds.isNotEmpty() ||
            storyPreviewSourceGeometries.isNotEmpty() || storyRevisions.isNotEmpty() ||
            storyPagingAlphaStates.isNotEmpty()
        if (listsChanged && hasRetainedStoryUiState) {
            checkNotNull(currentStoryIds)
            storyRevisions.keys.retainAll(currentStoryIds)
            storyPagingAlphaStates.keys.retainAll(currentStoryIds)
            storyBounds.keys.retainAll(currentStoryIds)
            storyPreviewSourceGeometries.keys.retainAll(currentStoryIds)
        }
        if (interactionContentChanged || previewActionStateChanged) syncInteractionState()
        contentVersion++
        if (requestedRefreshCompleted) {
            scrollToTopAfterRefresh = false
            refreshInProgressObserved = false
            if (tapToUpdateRefreshStarted) {
                tapToUpdateRefreshStarted = false
                tapToUpdateExitInProgress = false
            }
            scrollToTopRequestVersion++
        }
    }

    private fun updatePreviewResources(
        next: Map<Int, StoryPreviewResourceState>,
        currentStoryIds: Set<Int>?,
    ) {
        next.forEach { (storyId, resource) ->
            val terminalImageMiss = resource.imageUrl.isNullOrBlank() &&
                !resource.loading &&
                (resource.imageUrlResolved || resource.summaryResolved ||
                    resource.contentLoadFailed)
            if (terminalImageMiss) {
                previewImageKnownAbsentIds += storyId
            }
        }
        previewResourceStates.forEach { (storyId, state) ->
            val resource = next[storyId]
            if (state.value != resource) state.value = resource
        }
        if (currentStoryIds != null) {
            previewResourceStates.keys.retainAll(currentStoryIds)
            previewImageKnownAbsentIds.retainAll(currentStoryIds)
        }
        previewResourcesSnapshot = next
    }

    fun isStoryPreviewImageKnownAbsent(storyId: Int): Boolean =
        storyId in previewImageKnownAbsentIds

    fun invalidateStory(storyId: Int) {
        val revision = storyRevisions.getOrPut(storyId) { mutableIntStateOf(0) }
        revision.intValue++
    }

    fun storyRevision(storyId: Int): Int =
        storyRevisions.getOrPut(storyId) { mutableIntStateOf(0) }.intValue

    fun updateSearchDraft(value: String) {
        interactionStore.updateSearchDraft(value)
        syncInteractionState()
    }

    fun updateHeaderMenuVisibility(visible: Boolean) {
        headerMenuVisible = visible
    }

    fun beginPullToRefresh() {
        pullToRefreshInProgress = true
    }

    /** Refreshes the active feed and requests an exact top anchor after its new data is applied. */
    fun refresh() {
        scrollToTopAfterRefresh = true
        refreshInProgressObserved = refreshing
        listener.onRefresh(showMainLoadingIndicator = false)
    }

    /** Starts a UI-only removal pass; the network refresh is deferred until its exit finishes. */
    fun beginTapToUpdateExit() {
        if (tapToUpdateExitInProgress || refreshing || loading) return
        scrollToTopAfterRefresh = false
        refreshInProgressObserved = false
        tapToUpdateRefreshStarted = false
        tapToUpdateExitInProgress = true
        tapToUpdateExitRequestVersion++
    }

    fun completeTapToUpdateExit() {
        if (!tapToUpdateExitInProgress || tapToUpdateRefreshStarted) return
        tapToUpdateRefreshStarted = true
        scrollToTopAfterRefresh = true
        refreshInProgressObserved = refreshing
        // Keep the current snapshots in the store while the replacement feed loads. The UI layer
        // is already fully faded, so clearing 500 rows here only creates avoidable cache hydration
        // and layout work on the UI thread.
        listener.onRefresh(showMainLoadingIndicator = false)
    }

    fun cancelTapToUpdateExit() {
        if (tapToUpdateRefreshStarted) return
        tapToUpdateExitInProgress = false
    }

    fun finishPullToRefresh() {
        pullToRefreshInProgress = false
    }

    fun isHeaderMenuShowing(): Boolean = headerMenuVisible

    fun requestDismissHeaderMenu() {
        if (headerMenuVisible) headerMenuDismissRequestVersion++
    }

    fun cacheStories(storyCount: Int, downloadWebViewContents: Boolean) {
        listener.onCacheStoriesConfirmed(
            StoryCachePreferences.sanitizeCount(storyCount),
            downloadWebViewContents,
        )
    }

    fun showFrontDatePicker(initialDay: Long, earliestDay: Long, latestDay: Long) {
        interactionStore.showFrontDatePicker(initialDay, earliestDay, latestDay)
        syncInteractionState()
    }

    fun dismissFrontDatePicker() {
        interactionStore.dismissFrontDatePicker()
        syncInteractionState()
    }

    fun selectFrontDate(day: Long) {
        val selectedDay = interactionStore.selectFrontDate(day) ?: return
        syncInteractionState()
        listener.onFrontDateSelected(selectedDay)
    }

    fun beginPredictiveBack(progress: Float) {
        interactionStore.beginPredictiveBack(progress)
        syncInteractionState()
    }

    /**
     * Handles a host back gesture without exposing search workflow to the platform shell.
     * Hosts only translate their native back event into the progress value.
     */
    fun startSearchBack(progress: Float): Boolean {
        if (!searching) return false
        beginPredictiveBack(progress)
        return true
    }

    fun updatePredictiveBack(progress: Float) {
        interactionStore.updatePredictiveBack(progress)
        syncInteractionState()
    }

    fun updateSearchBack(progress: Float): Boolean {
        if (!searching) return false
        if (predictiveBackActive) {
            updatePredictiveBack(progress)
        } else {
            beginPredictiveBack(progress)
        }
        return true
    }

    fun cancelPredictiveBack() {
        interactionStore.settlePredictiveBack(target = 0f)
        syncInteractionState()
    }

    fun cancelSearchBack(): Boolean {
        if (!predictiveBackActive) return false
        cancelPredictiveBack()
        return true
    }

    fun commitPredictiveBack() {
        interactionStore.settlePredictiveBack(target = 1f)
        syncInteractionState()
    }

    fun finishSearchBack(): Boolean {
        if (!searching) return false
        if (predictiveBackActive) {
            commitPredictiveBack()
        } else {
            listener.onCloseSearch()
        }
        return true
    }

    fun endPredictiveBack(request: StoryPredictiveBackSettleRequest? = null) {
        interactionStore.endPredictiveBack(request)
        syncInteractionState()
    }

    fun requestScrollBy(dy: Int) {
        interactionStore.requestScrollBy(dy)
        scrollByRequestState = interactionStore.state.scrollRequest
    }

    fun consumeScrollBy(request: StoryScrollRequest, consumedDy: Int) {
        interactionStore.consumeScrollRequest(request, consumedDy)
        scrollByRequestState = interactionStore.state.scrollRequest
    }

    fun clearStoryPagingAlphas() {
        resetStoryPagingAlphaStates()
        interactionStore.clearStoryPagingAlphas()
        syncInteractionState()
    }

    fun showStoryPreview(
        stories: List<StoryListItemSnapshot>,
        cardColors: List<Int>,
        openedStoryId: Int,
    ) {
        if (!interactionStore.showStoryPreview(
                stories,
                cardColors,
                openedStoryId,
            )
        ) return
        resetStoryPagingAlphaStates()
        storyPreviewReadStates.clear()
        stories.forEach { story ->
            storyPreviewReadStates[story.id] = mutableStateOf(story.clicked)
        }
        sourceCoveredByStoryPreviewTransition = false
        requestStopStoryPreviewScroll()
        syncInteractionState()
        listener.onStoryPreviewVisibilityChanged(true)
    }

    fun showStoryPreview(
        stories: List<StoryListItemSnapshot>,
        cardColors: IntArray,
        openedStoryId: Int,
    ) = showStoryPreview(stories, cardColors.toList(), openedStoryId)

    fun showStoryPreview(deck: com.simon.harmonichackernews.presentation.StoryPreviewDeck) =
        showStoryPreview(deck.stories, deck.cardColors, deck.openedStoryId)

    fun restoreStoryPreview(
        stories: List<StoryListItemSnapshot>,
        cardColors: IntArray,
        openedStoryId: Int,
    ) = showStoryPreview(stories, cardColors, openedStoryId)

    fun isStoryPreviewShowing(): Boolean = storyPreviewOverlay != null

    fun requestDismissStoryPreview() {
        interactionStore.requestDismissStoryPreview()
        syncInteractionState()
    }

    fun completeStoryPreviewDismiss() {
        if (!interactionStore.completeStoryPreviewDismiss()) return
        resetStoryPagingAlphaStates()
        sourceCoveredByStoryPreviewTransition = false
        syncInteractionState()
        listener.onStoryPreviewVisibilityChanged(false)
    }

    fun startStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        interactionStore.updateStoryPreviewPredictiveBack(progress, edge, touchY)
        syncInteractionState()
    }

    fun updateStoryPreviewPredictiveBack(progress: Float, edge: Int, touchY: Float) {
        startStoryPreviewPredictiveBack(progress, edge, touchY)
    }

    fun cancelStoryPreviewPredictiveBack() {
        interactionStore.cancelStoryPreviewPredictiveBack()
        syncInteractionState()
    }

    fun isStoryPreviewPredictiveBackActive(): Boolean =
        storyPreviewOverlay != null &&
            (storyPreviewPredictiveBackProgress > 0f ||
                storyPreviewPredictiveBackSettleRequest != null)

    fun commitStoryPreviewPredictiveBack() {
        if (storyPreviewOverlay == null) return
        requestDismissStoryPreview()
    }

    fun finishStoryPreviewPredictiveBackSettle(
        request: StoryPredictiveBackSettleRequest,
    ) {
        interactionStore.finishStoryPreviewPredictiveBackSettle(request)
        syncInteractionState()
    }

    fun sourceBoundsForStory(storyId: Int): Rect? =
        sourceGeometryForStory(storyId)?.container

    fun sourceGeometryForStory(storyId: Int): StoryPreviewSourceGeometry? =
        storyPreviewSourceGeometries[storyId]
            ?: storyBounds[storyId]?.let(::StoryPreviewSourceGeometry)

    fun onStoryPreviewPagePosition(
        lowerPage: Int,
        upperPage: Int,
        offset: Float,
    ) {
        val stories = interactionStore.state.storyPreviewOverlay?.stories ?: return
        val lowerStoryId = stories.getOrNull(lowerPage)?.id ?: return
        val upperStoryId = stories.getOrNull(upperPage)?.id ?: lowerStoryId
        val normalizedOffset = offset.coerceIn(0f, 1f)

        if (pagingLowerStoryId != lowerStoryId && pagingLowerStoryId != upperStoryId) {
            setStoryPagingAlpha(pagingLowerStoryId, 1f)
        }
        if (pagingUpperStoryId != lowerStoryId && pagingUpperStoryId != upperStoryId) {
            setStoryPagingAlpha(pagingUpperStoryId, 1f)
        }
        setStoryPagingAlpha(
            lowerStoryId,
            if (upperStoryId == lowerStoryId) 0f else normalizedOffset,
        )
        if (upperStoryId != lowerStoryId) {
            setStoryPagingAlpha(upperStoryId, 1f - normalizedOffset)
        }
        pagingLowerStoryId = lowerStoryId
        pagingUpperStoryId = upperStoryId

        // This is a one-time transition. Subsequent drag samples only invalidate the two alpha
        // states above instead of publishing a new whole-screen interaction snapshot each frame.
        if (interactionState.suppressedStoryIds.isNotEmpty()) {
            interactionStore.beginStoryPreviewPaging()
            syncInteractionState()
        }
    }

    fun storyPagingAlphaState(storyId: Int): FloatState =
        storyPagingAlphaStates.getOrPut(storyId) { mutableFloatStateOf(1f) }

    private fun setStoryPagingAlpha(storyId: Int, alpha: Float) {
        if (storyId < 0) return
        storyPagingAlphaStates.getOrPut(storyId) { mutableFloatStateOf(1f) }.floatValue = alpha
    }

    private fun resetStoryPagingAlphaStates() {
        setStoryPagingAlpha(pagingLowerStoryId, 1f)
        if (pagingUpperStoryId != pagingLowerStoryId) {
            setStoryPagingAlpha(pagingUpperStoryId, 1f)
        }
        pagingLowerStoryId = -1
        pagingUpperStoryId = -1
    }

    fun onStoryPreviewPageSettled(page: Int) {
        interactionStore.settleStoryPreviewPage(page)
        syncInteractionState()
    }

    fun onStoryPreviewNavigate(page: Int, showWebsite: Boolean) {
        val target = interactionStore.storyPreviewTarget(page) ?: return
        listener.onStoryPreviewNavigate(
            target.story,
            showWebsite,
        )
        // The preview remains part of the Stories destination in both single- and multi-pane
        // layouts. Retaining it lets Back return to the exact preview page and action state.
    }

    fun onStoryPreviewAction(page: Int, action: StoryPreviewActionKind) {
        val kind = action
        val target = interactionStore.beginStoryPreviewAction(page, kind) ?: return
        if (action == StoryPreviewActionKind.Read) {
            val readState = storyPreviewReadStates.getOrPut(target.story.id) {
                mutableStateOf(target.story.clicked)
            }
            readState.value = !readState.value
        }
        syncInteractionState()
        listener.onStoryPreviewAction(target.story, action)
        if (action == StoryPreviewActionKind.Read || action == StoryPreviewActionKind.Bookmark) {
            contentVersion++
        }
    }

    fun finishStoryPreviewAction(storyId: Int, action: StoryPreviewActionKind) {
        interactionStore.finishStoryPreviewAction(storyId, action)
        syncInteractionState()
        contentVersion++
    }

    private fun requestStopStoryPreviewScroll() {
        listener.onStoryPreviewStopScroll()
    }

    fun getAdjacentStoryPagingDistance(precedingStoryId: Int): Int {
        return interactionStore.getAdjacentStoryPagingDistance(precedingStoryId)
    }

    fun updateStoryBounds(storyId: Int, bounds: Rect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        putBounded(storyBounds, storyId, bounds)
    }

    fun updateStoryPreviewSourceGeometry(
        storyId: Int,
        geometry: StoryPreviewSourceGeometry,
    ) {
        if (geometry.container.width <= 0f || geometry.container.height <= 0f) return
        putBounded(storyBounds, storyId, geometry.container)
        if (storyPreviewSourceGeometries[storyId] != geometry) {
            putBounded(storyPreviewSourceGeometries, storyId, geometry)
        }
    }

    fun updateStoryItemHeight(storyId: Int, heightPx: Int) {
        interactionStore.updateStoryItemHeight(storyId, heightPx)
    }

    fun isStorySuppressed(storyId: Int): Boolean =
        storyId in interactionState.suppressedStoryIds

    /** Keeps the live source row visible until the transition overlay has covered it. */
    fun shouldKeepStoryPreviewSourceVisible(storyId: Int): Boolean =
        storyPreviewOverlay != null &&
            visibleStoryPreviewId == storyId &&
            !sourceCoveredByStoryPreviewTransition

    fun setStoryPreviewSourceCovered(covered: Boolean) {
        sourceCoveredByStoryPreviewTransition = covered
    }

    fun isBookmarked(storyId: Int): Boolean = savedItemState.isBookmarked(storyId)

    fun isStoryPreviewRead(storyId: Int, initialValue: Boolean): Boolean =
        storyPreviewReadStates.getOrPut(storyId) { mutableStateOf(initialValue) }.value

    fun isFavorited(storyId: Int): Boolean = savedItemState.isFavorited(storyId)

    fun isUpvoted(storyId: Int): Boolean =
        savedItemState.isUpvoted(storyId, isComment = false)

    private fun <T> putBounded(target: MutableMap<Int, T>, storyId: Int, value: T) {
        target.remove(storyId)
        while (target.size >= MAX_TRACKED_STORY_GEOMETRIES) {
            target.remove(target.keys.first())
        }
        target[storyId] = value
    }

    interface Listener {
        fun onTypeSelected(index: Int)
        fun onOpenSearch()
        fun onCloseSearch()
        fun onSearch(query: String)
        fun onSearchOption(kind: StorySearchOption, index: Int)
        fun onToggleOnlyClicked()
        fun onRefresh(showMainLoadingIndicator: Boolean = false)
        fun onShowCached()
        fun onLoadMore()
        fun onSavedFilterSelected(filter: SavedItemFilter)
        fun onShiftFrontDate(days: Int)
        fun onPickFrontDate()
        fun onFrontDateSelected(day: Long)
        fun onMoreAction(action: StoriesMenuAction)
        fun onCacheStoriesConfirmed(storyCount: Int, downloadWebViewContents: Boolean)
        fun onLinkClick(story: StoryListItemSnapshot)
        fun onCommentClick(story: StoryListItemSnapshot)
        fun onCommentStoryClick(story: StoryListItemSnapshot)
        fun onCommentRepliesClick(story: StoryListItemSnapshot)
        fun onStoryLongClick(
            story: StoryListItemSnapshot,
            tintBaseColorArgb: Int,
        ): com.simon.harmonichackernews.presentation.StoryPreviewDeck?
        fun onStoryPreviewImageLoaded(storyId: Int, pageUrl: String, imageUrl: String)
        fun onStoryPreviewImageLoadFailed(storyId: Int, pageUrl: String, imageUrl: String)
        fun onStoryTintExtracted(
            story: StoryListItemSnapshot,
            sourceUrl: String,
            baseColorArgb: Int,
            paletteConfigKey: String,
            tintColorArgb: Int,
            favicon: Boolean,
        )
        fun onVisibleStoryRange(lastVisibleIndex: Int)
        fun onStoryPreviewStopScroll()
        fun onStoryPreviewVisibilityChanged(showing: Boolean)
        fun onStoryPreviewNavigate(
            story: StoryListItemSnapshot,
            showWebsite: Boolean,
        ): Boolean
        fun onStoryPreviewAction(
            story: StoryListItemSnapshot,
            action: StoryPreviewActionKind,
        )
    }

    companion object {
        private const val MAX_TRACKED_STORY_GEOMETRIES = 256

        fun create(
            defaultStoryHeightPx: Int,
            savedItemState: SavedItemStateReader,
            listener: Listener,
        ): StoriesComposeController = StoriesComposeController(
            defaultStoryHeightPx = defaultStoryHeightPx,
            savedItemState = savedItemState,
            listener = listener,
        )
    }
}
