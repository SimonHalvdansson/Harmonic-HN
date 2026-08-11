package com.simon.harmonichackernews.ui.stories

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StoryFrontDatePickerRequest
import com.simon.harmonichackernews.presentation.StoryPredictiveBackSettleRequest
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState
import com.simon.harmonichackernews.presentation.StoryScrollRequest
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.StoryCachePreferences
import org.jetbrains.compose.resources.DrawableResource

class StoriesComposeController private constructor(
    defaultStoryHeightPx: Int,
    private val savedItemState: SavedItemStateReader,
    val listener: Listener,
) {
    var mainStories by mutableStateOf<List<Story>>(emptyList())
        private set
    var searchStories by mutableStateOf<List<Story>>(emptyList())
        private set
    var displaySettings by mutableStateOf<StoryDisplaySettings?>(null)
        private set
    var typeLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedTypeIndex by mutableIntStateOf(0)
        private set
    var lastSearch by mutableStateOf("")
        private set
    var searchSortLabel by mutableStateOf("Relevance")
        private set
    var searchDateLabel by mutableStateOf("All time")
        private set
    var searchPointsLabel by mutableStateOf("Any points")
        private set
    var searchCommentsLabel by mutableStateOf("Any comments")
        private set
    var searchSortLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var searchDateLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var searchPointsLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var searchCommentsLabels by mutableStateOf<List<String>>(emptyList())
        private set
    var searchOnlyClicked by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var loadingFailed by mutableStateOf(false)
        private set
    var loadingFailedServerError by mutableStateOf(false)
        private set
    var loadingFailedMessage by mutableStateOf("Loading failed")
        private set
    var showingCached by mutableStateOf(false)
        private set
    var showCachedAction by mutableStateOf(false)
        private set
    var showEmptySavedList by mutableStateOf(false)
        private set
    var emptySavedListText by mutableStateOf("No saved stories")
        private set
    var emptySavedListIcon by mutableStateOf(Res.drawable.ic_bookmark)
        private set
    var showEmptySearch by mutableStateOf(false)
        private set
    var showUpdate by mutableStateOf(false)
        private set
    var lastUpdatedText by mutableStateOf<String?>(null)
        private set
    var showLoadMore by mutableStateOf(false)
        private set
    var loadMoreLoading by mutableStateOf(false)
        private set
    var mainVisibleCount by mutableIntStateOf(Int.MAX_VALUE)
        private set
    var searchVisibleCount by mutableIntStateOf(Int.MAX_VALUE)
        private set
    var showSavedFilter by mutableStateOf(false)
        private set
    var savedFilter by mutableIntStateOf(FILTER_BOTH)
        private set
    var showFrontDate by mutableStateOf(false)
        private set
    var frontDateLabel by mutableStateOf("")
        private set
    var frontPreviousEnabled by mutableStateOf(false)
        private set
    var frontNextEnabled by mutableStateOf(false)
        private set
    var loggedIn by mutableStateOf(false)
        private set
    var canCache by mutableStateOf(false)
        private set
    var canClearHistory by mutableStateOf(false)
        private set
    var cacheProgressVisible by mutableStateOf(false)
        private set
    var cacheProgress by mutableIntStateOf(0)
        private set
    var cacheProgressMax by mutableIntStateOf(1)
        private set
    var cacheProgressStatus by mutableStateOf("Caching stories")
        private set
    var contentInsetStartPx by mutableIntStateOf(0)
        private set
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
    val headerPinnedForPreview: Boolean get() = interactionState.headerPinnedForPreview
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
        emptySavedListIcon: DrawableResource,
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
        this.mainStories = mainStories.toList()
        this.searchStories = searchStories.toList()
        this.displaySettings = displaySettings
        this.typeLabels = typeLabels.toList()
        this.selectedTypeIndex = selectedTypeIndex
        this.lastSearch = lastSearch
        interactionStore.updateContent(mainStories, searchStories, searching, lastSearch)
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
        this.emptySavedListIcon = emptySavedListIcon
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

    fun updatePredictiveBack(progress: Float) {
        interactionStore.updatePredictiveBack(progress)
        syncInteractionState()
    }

    fun cancelPredictiveBack() {
        interactionStore.settlePredictiveBack(target = 0f)
        syncInteractionState()
    }

    fun commitPredictiveBack() {
        interactionStore.settlePredictiveBack(target = 1f)
        syncInteractionState()
    }

    fun endPredictiveBack(request: StoryPredictiveBackSettleRequest? = null) {
        interactionStore.endPredictiveBack(request)
        syncInteractionState()
    }

    fun requestScrollBy(dy: Int) {
        interactionStore.requestScrollBy(dy)
        syncInteractionState()
    }

    fun unpinPreviewHeader() {
        interactionStore.unpinPreviewHeader()
        syncInteractionState()
    }

    fun consumeScrollBy(request: StoryScrollRequest) {
        interactionStore.consumeScrollRequest(request)
        syncInteractionState()
    }

    fun clearStoryPagingAlphas() {
        interactionStore.clearStoryPagingAlphas()
        syncInteractionState()
    }

    fun showStoryPreview(
        stories: List<Story>,
        sourcePositions: IntArray,
        cardColors: IntArray,
        openedStoryId: Int,
    ) {
        if (!interactionStore.showStoryPreview(
                stories,
                sourcePositions.toList(),
                cardColors.toList(),
                openedStoryId,
            )
        ) return
        requestStopStoryPreviewScroll()
        syncInteractionState()
        listener.onStoryPreviewVisibilityChanged(true)
    }

    fun restoreStoryPreview(
        stories: List<Story>,
        sourcePositions: IntArray,
        cardColors: IntArray,
        openedStoryId: Int,
    ) = showStoryPreview(stories, sourcePositions, cardColors, openedStoryId)

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
        if (!listener.onStoryPreviewNavigate(target.story, target.sourcePosition, showWebsite)) {
            interactionStore.requestDismissStoryPreview()
            syncInteractionState()
        }
    }

    fun onStoryPreviewAction(page: Int, action: Int) {
        val kind = previewActionKind(action) ?: return
        val target = interactionStore.beginStoryPreviewAction(page, kind) ?: return
        syncInteractionState()
        listener.onStoryPreviewAction(target.story, target.sourcePosition, action)
        if (action == STORY_PREVIEW_ACTION_READ || action == STORY_PREVIEW_ACTION_BOOKMARK) {
            contentVersion++
        }
    }

    fun finishStoryPreviewAction(storyId: Int, action: Int) {
        previewActionKind(action)?.let { interactionStore.finishStoryPreviewAction(storyId, it) }
        syncInteractionState()
        contentVersion++
    }

    private fun requestStopStoryPreviewScroll() {
        listener.onStoryPreviewStopScroll()
    }

    fun getStoryPagingDistance(firstStoryId: Int, secondStoryId: Int): Int {
        return interactionStore.getStoryPagingDistance(firstStoryId, secondStoryId)
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

    private fun previewActionKind(action: Int): StoryPreviewActionKind? = when (action) {
        STORY_PREVIEW_ACTION_VOTE -> StoryPreviewActionKind.Vote
        STORY_PREVIEW_ACTION_READ -> StoryPreviewActionKind.Read
        STORY_PREVIEW_ACTION_BOOKMARK -> StoryPreviewActionKind.Bookmark
        STORY_PREVIEW_ACTION_FAVORITE -> StoryPreviewActionKind.Favorite
        else -> null
    }

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
