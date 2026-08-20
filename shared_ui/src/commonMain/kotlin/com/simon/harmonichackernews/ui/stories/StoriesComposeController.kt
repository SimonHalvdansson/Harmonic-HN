package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
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

class StoriesComposeController private constructor(
    defaultStoryHeightPx: Int,
    private val savedItemState: SavedItemStateReader,
    val listener: Listener,
) {
    var screenState by mutableStateOf(StoriesScreenState())
        private set

    val mainStories: List<StoryListItemSnapshot> get() = screenState.mainStories
    val searchStories: List<StoryListItemSnapshot> get() = screenState.searchStories
    val previewResources: Map<Int, StoryPreviewResourceState> get() = screenState.previewResources
    val displaySettings: StoryDisplaySettings? get() = screenState.displaySettings
    val typeLabels: List<String> get() = screenState.typeLabels
    val selectedTypeIndex: Int get() = screenState.selectedTypeIndex
    val lastSearch: String get() = screenState.lastSearch
    val searchSortLabel: String get() = screenState.searchSortLabel
    val searchDateLabel: String get() = screenState.searchDateLabel
    val searchPointsLabel: String get() = screenState.searchPointsLabel
    val searchCommentsLabel: String get() = screenState.searchCommentsLabel
    val searchSortLabels: List<String> get() = screenState.searchSortLabels
    val searchDateLabels: List<String> get() = screenState.searchDateLabels
    val searchPointsLabels: List<String> get() = screenState.searchPointsLabels
    val searchCommentsLabels: List<String> get() = screenState.searchCommentsLabels
    val searchOnlyClicked: Boolean get() = screenState.searchOnlyClicked
    val loading: Boolean get() = screenState.loading
    val refreshing: Boolean get() = screenState.refreshing
    val loadingFailed: Boolean get() = screenState.loadingFailed
    val loadingFailedServerError: Boolean get() = screenState.loadingFailedServerError
    val loadingFailedMessage: String get() = screenState.loadingFailedMessage
    val showingCached: Boolean get() = screenState.showingCached
    val showCachedAction: Boolean get() = screenState.showCachedAction
    val showEmptySavedList: Boolean get() = screenState.showEmptySavedList
    val emptySavedListText: String get() = screenState.emptySavedListText
    val emptySavedListIcon: DrawableResource get() = screenState.emptySavedListIcon
    val showEmptySearch: Boolean get() = screenState.showEmptySearch
    val showUpdate: Boolean get() = screenState.showUpdate
    val lastUpdatedText: String? get() = screenState.lastUpdatedText
    val showLoadMore: Boolean get() = screenState.showLoadMore
    val loadMoreLoading: Boolean get() = screenState.loadMoreLoading
    val mainVisibleCount: Int get() = screenState.mainVisibleCount
    val searchVisibleCount: Int get() = screenState.searchVisibleCount
    val showSavedFilter: Boolean get() = screenState.showSavedFilter
    val savedFilter: SavedItemFilter get() = screenState.savedFilter
    val showFrontDate: Boolean get() = screenState.showFrontDate
    val frontDateLabel: String get() = screenState.frontDateLabel
    val frontPreviousEnabled: Boolean get() = screenState.frontPreviousEnabled
    val frontNextEnabled: Boolean get() = screenState.frontNextEnabled
    val loggedIn: Boolean get() = screenState.loggedIn
    val canCache: Boolean get() = screenState.canCache
    val canClearHistory: Boolean get() = screenState.canClearHistory
    val cacheProgressVisible: Boolean get() = screenState.cacheProgressVisible
    val cacheProgress: Int get() = screenState.cacheProgress
    val cacheProgressMax: Int get() = screenState.cacheProgressMax
    val cacheProgressStatus: String get() = screenState.cacheProgressStatus
    val contentInsetStartPx: Int get() = screenState.contentInsetStartPx
    var contentVersion by mutableIntStateOf(0)
        private set

    private val interactionStore = StoriesInteractionStore(defaultStoryHeightPx)
    private var interactionState by mutableStateOf(interactionStore.state)
    private val storyBounds = mutableMapOf<Int, Rect>()
    private val storyRevisions = mutableMapOf<Int, MutableIntState>()

    val searching: Boolean get() = interactionState.searching
    val searchDraft: String get() = interactionState.searchDraft
    val frontDatePickerRequest: StoryFrontDatePickerRequest?
        get() = interactionState.frontDatePickerRequest
    val predictiveBackActive: Boolean get() = interactionState.predictiveBackActive
    val predictiveBackProgress: Float get() = interactionState.predictiveBackProgress
    val suppressSearchAutoFocus: Boolean get() = interactionState.suppressSearchAutoFocus
    val predictiveBackSettleRequest: StoryPredictiveBackSettleRequest?
        get() = interactionState.predictiveBackSettleRequest
    val scrollByRequest: StoryScrollRequest? get() = interactionState.scrollRequest
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
    val storyPreviewVoteLoadingId: Int get() = interactionState.storyPreviewVoteLoadingId
    val storyPreviewFavoriteLoadingId: Int
        get() = interactionState.storyPreviewFavoriteLoadingId
    val visibleStoryPreviewId: Int get() = interactionState.visibleStoryPreviewId

    private fun syncInteractionState() {
        interactionState = interactionStore.state
    }

    fun updateContent(state: StoriesScreenState) {
        screenState = state.copy(
            mainStories = state.mainStories.toList(),
            searchStories = state.searchStories.toList(),
            previewResources = state.previewResources.toMap(),
            typeLabels = state.typeLabels.toList(),
            searchSortLabels = state.searchSortLabels.toList(),
            searchDateLabels = state.searchDateLabels.toList(),
            searchPointsLabels = state.searchPointsLabels.toList(),
            searchCommentsLabels = state.searchCommentsLabels.toList(),
            cacheProgressMax = state.cacheProgressMax.coerceAtLeast(1),
        )
        interactionStore.updateContent(
            state.mainStories,
            state.searchStories,
            state.searching,
            state.lastSearch,
        )
        val currentStoryIds = buildSet(state.mainStories.size + state.searchStories.size) {
            state.mainStories.forEach { add(it.id) }
            state.searchStories.forEach { add(it.id) }
        }
        storyRevisions.keys.retainAll(currentStoryIds)
        syncInteractionState()
        contentVersion++
    }

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

    fun cacheStories(storyCount: Int) {
        listener.onCacheStoriesConfirmed(
            StoryCachePreferences.sanitizeCount(storyCount),
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
        syncInteractionState()
    }

    fun consumeScrollBy(request: StoryScrollRequest, consumedDy: Int) {
        interactionStore.consumeScrollRequest(request, consumedDy)
        syncInteractionState()
    }

    fun clearStoryPagingAlphas() {
        interactionStore.clearStoryPagingAlphas()
        syncInteractionState()
    }

    fun showStoryPreview(
        stories: List<StoryListItemSnapshot>,
        cardColors: IntArray,
        openedStoryId: Int,
    ) {
        if (!interactionStore.showStoryPreview(
                stories,
                cardColors.toList(),
                openedStoryId,
            )
        ) return
        requestStopStoryPreviewScroll()
        syncInteractionState()
        listener.onStoryPreviewVisibilityChanged(true)
    }

    fun showStoryPreview(deck: com.simon.harmonichackernews.presentation.StoryPreviewDeck) =
        showStoryPreview(deck.stories, deck.cardColors.toIntArray(), deck.openedStoryId)

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
        storyBounds[storyId]

    fun onStoryPreviewPagePosition(
        lowerPage: Int,
        upperPage: Int,
        offset: Float,
    ) {
        interactionStore.updateStoryPreviewPagePosition(lowerPage, upperPage, offset)
        syncInteractionState()
    }

    fun onStoryPreviewPageSettled(page: Int) {
        interactionStore.settleStoryPreviewPage(page)
        syncInteractionState()
    }

    fun onStoryPreviewNavigate(page: Int, showWebsite: Boolean) {
        val target = interactionStore.storyPreviewTarget(page) ?: return
        if (!listener.onStoryPreviewNavigate(target.story, showWebsite)) {
            interactionStore.requestDismissStoryPreview()
            syncInteractionState()
        }
    }

    fun onStoryPreviewAction(page: Int, action: StoryPreviewActionKind) {
        val kind = action
        val target = interactionStore.beginStoryPreviewAction(page, kind) ?: return
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
        if (storyBounds[storyId] != bounds) storyBounds[storyId] = bounds
    }

    fun updateStoryItemHeight(storyId: Int, heightPx: Int) {
        interactionStore.updateStoryItemHeight(storyId, heightPx)
    }

    fun isStorySuppressed(storyId: Int): Boolean =
        storyId in interactionState.suppressedStoryIds

    fun isBookmarked(storyId: Int): Boolean = savedItemState.isBookmarked(storyId)

    fun isFavorited(storyId: Int): Boolean = savedItemState.isFavorited(storyId)

    fun isUpvoted(storyId: Int): Boolean =
        savedItemState.isUpvoted(storyId, isComment = false)

    interface Listener {
        fun onTypeSelected(index: Int)
        fun onOpenSearch()
        fun onCloseSearch()
        fun onSearch(query: String)
        fun onSearchOption(kind: StorySearchOption, index: Int)
        fun onToggleOnlyClicked()
        fun onRefresh()
        fun onShowCached()
        fun onLoadMore()
        fun onSavedFilterSelected(filter: SavedItemFilter)
        fun onShiftFrontDate(days: Int)
        fun onPickFrontDate()
        fun onFrontDateSelected(day: Long)
        fun onMoreAction(action: StoriesMenuAction)
        fun onCacheStoriesConfirmed(storyCount: Int)
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
