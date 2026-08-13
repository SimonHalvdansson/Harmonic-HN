package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.cache.StoryCacheState
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

data class StoriesSearchSnapshot(
    val sortLabel: String,
    val dateLabel: String,
    val pointsLabel: String,
    val commentsLabel: String,
    val options: StorySearchOptions,
    val loading: Boolean,
)

/** Complete immutable feature state consumed by every Stories host. */
data class StoriesState(
    val mainList: PortableStoryListState = PortableStoryListState(),
    val searchList: PortableStoryListState = PortableStoryListState(),
    val previewResources: Map<Int, StoryPreviewResourceState> = emptyMap(),
    val displaySettings: StoryDisplaySettings? = null,
    val availableStoryTypes: List<StoryType> = listOf(StoryType.TOP_STORIES),
    val selectedTypeIndex: Int = 0,
    val currentType: StoryType = StoryType.TOP_STORIES,
    val searching: Boolean = false,
    val searchDraft: String = "",
    val search: StoriesSearchSnapshot = StoriesSearchSnapshot(
        sortLabel = "Relevance",
        dateLabel = "All time",
        pointsLabel = "Any points",
        commentsLabel = "Any comments",
        options = StorySearchOptions(),
        loading = false,
    ),
    val refreshIndicatorShowing: Boolean = false,
    val updateAvailable: Boolean = false,
    val loadingFailedRateLimited: Boolean = false,
    val online: Boolean = true,
    val userItemsInitialLoadInProgress: Boolean = false,
    val savedSourceHasItems: Boolean = false,
    val cachedStoriesAvailable: Boolean = false,
    val savedFilter: SavedItemFilter = SavedItemFilter.BOTH,
    val frontDateLabel: String = "",
    val frontDateSelectedMillis: Long = 0L,
    val frontDateEarliestMillis: Long = 0L,
    val frontDateLatestMillis: Long = 0L,
    val loggedIn: Boolean = false,
    val canClearHistory: Boolean = false,
    val cache: StoryCacheState = StoryCacheState(),
    val lastUpdatedMillis: Long? = null,
) {
    val activeList: PortableStoryListState get() = if (searching) searchList else mainList
    val activeItems: List<StoryListItemSnapshot> get() = activeList.items
    val mainVisibleItemCount: Int get() = mainList.visibleItemCount()
    val searchVisibleItemCount: Int get() = searchList.visibleItemCount()
    val activeVisibleItemCount: Int get() = activeList.visibleItemCount()
    val activeHasLoadMore: Boolean get() = activeList.loadMoreInProgress ||
        activeList.canLoadMore ||
        (activeList.paginationEnabled && activeList.visibleStoryCount < activeList.items.size)

    private fun PortableStoryListState.visibleItemCount(): Int =
        visibleStoryCount.takeIf { paginationEnabled }?.coerceAtMost(items.size) ?: items.size
}

sealed interface StoriesIntent {
    data class SelectType(val index: Int) : StoriesIntent
    data object OpenSearch : StoriesIntent
    data object CloseSearch : StoriesIntent
    data class Search(val query: String) : StoriesIntent
    data class SelectSearchOption(val kind: StorySearchOption, val index: Int) : StoriesIntent
    data object ToggleOnlyClicked : StoriesIntent
    data object Refresh : StoriesIntent
    data object ShowCached : StoriesIntent
    data object LoadMore : StoriesIntent
    data class SelectSavedFilter(val filter: SavedItemFilter) : StoriesIntent
    data class ShiftFrontDate(val days: Int) : StoriesIntent
    data class SelectFrontDate(val day: Long) : StoriesIntent
    data class More(val action: StoriesMenuAction) : StoriesIntent
    data class CacheStories(val storyCount: Int) : StoriesIntent
    data class OpenLink(val storyId: Int) : StoriesIntent
    data class OpenComments(val storyId: Int) : StoriesIntent
    data class OpenCommentStory(val storyId: Int) : StoriesIntent
    data class CompletePreviewImage(
        val storyId: Int,
        val pageUrl: String,
        val imageUrl: String,
        val success: Boolean,
    ) : StoriesIntent
    data class RecordTint(
        val storyId: Int,
        val sourceUrl: String,
        val baseColorArgb: Int,
        val paletteConfigKey: String,
        val tintColorArgb: Int,
        val favicon: Boolean,
    ) : StoriesIntent
    data class VisibleRange(val lastVisibleIndex: Int) : StoriesIntent
    data class OpenPreviewStory(val storyId: Int, val showWebsite: Boolean) : StoriesIntent
    data class PreviewAction(val storyId: Int, val action: StoryPreviewActionKind) : StoriesIntent
}

/**
 * Canonical Stories owner. Presenters and mutable working models are private implementation
 * details; hosts observe only [state], send [StoriesIntent]s, and handle immutable effects.
 */
class StoriesStore internal constructor(
    private val scope: CoroutineScope,
    private val sessionState: StoriesSessionState,
    private val presenter: StoriesPresenter,
    private val runtime: StoriesFeatureRuntime,
    private val storyCache: StoryCacheRuntime,
    private val observeSavedItems: suspend (suspend (SavedItemSource) -> Unit) -> Unit,
    private val observeStoryUpdates: suspend (suspend (Story) -> Unit) -> Unit,
) : FeatureStore<StoriesIntent, StoriesState, StoriesRuntimeEffect> {
    private val mutableState = MutableStateFlow(snapshot())
    override val state: StateFlow<StoriesState> = mutableState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<StoriesRuntimeEffect>(extraBufferCapacity = 64)
    override val effects: SharedFlow<StoriesRuntimeEffect> = mutableEffects.asSharedFlow()
    private val jobs = mutableListOf<Job>()
    private var started = false
    private var hostStarted = false
    private var closed = false

    val savedItemState: SavedItemStateReader get() = runtime.savedItemActions

    /** Starts retained state and all repository observation exactly once. */
    fun start(): Boolean {
        if (closed) return sessionState.initialized
        if (started) return sessionState.initialized
        started = true
        runtime.initializeHistory()
        val restoring = sessionState.initialized
        runtime.storyResources?.setResourceChangedListener(::publish)
        runtime.initialize(restoring)
        jobs += scope.launch {
            runtime.effects.collect {
                mutableEffects.emit(it)
                publish()
            }
        }
        jobs += scope.launch { runtime.settingsState.collect { publish() } }
        jobs += scope.launch { presenter.state.collect { publish() } }
        jobs += scope.launch { runtime.mainStore.state.collect { publish() } }
        jobs += scope.launch { runtime.searchStore.state.collect { publish() } }
        jobs += scope.launch {
            observeSavedItems { source ->
                runtime.notifySavedItemsChanged(source)
                runtime.refreshBookmarksIfNeeded(hostStarted)
                publish()
            }
        }
        jobs += scope.launch { storyCache.state.collect { publish() } }
        jobs += scope.launch {
            observeStoryUpdates { story ->
                runtime.mergeExternalStoryUpdate(story)
                publish()
            }
        }
        sessionState.initialized = true
        if (restoring) {
            when {
                runtime.shouldRefreshRestoredState() -> runtime.refresh(false)
                !presenter.state.value.searching -> runtime.resumeRetainedLoads()
            }
        } else {
            runtime.refresh(false)
        }
        publish()
        return restoring
    }

    fun onStart() {
        hostStarted = true
        runtime.refreshBookmarksIfNeeded(hostStarted = true)
        publish()
    }

    fun onStop() {
        hostStarted = false
    }

    fun onResume() {
        runtime.resume(hostStarted)
        publish()
    }

    override fun accept(intent: StoriesIntent) {
        if (closed) return
        when (intent) {
            is StoriesIntent.SelectType -> {
                val type = runtime.storyTypeAt(intent.index)
                if (type != StoryType.UNKNOWN && type != runtime.currentType) {
                    runtime.selectTypeAndRefresh(type)
                }
            }
            StoriesIntent.OpenSearch -> runtime.openSearch()
            StoriesIntent.CloseSearch -> runtime.closeSearch()
            is StoriesIntent.Search -> runtime.submitSearch(intent.query)
            is StoriesIntent.SelectSearchOption ->
                runtime.selectSearchOption(intent.kind, intent.index)
            StoriesIntent.ToggleOnlyClicked -> runtime.toggleOnlyClicked()
            StoriesIntent.Refresh -> runtime.refresh(false)
            StoriesIntent.ShowCached -> runtime.showCachedStories()
            StoriesIntent.LoadMore -> runtime.loadMore()
            is StoriesIntent.SelectSavedFilter -> runtime.selectSavedFilter(intent.filter)
            is StoriesIntent.ShiftFrontDate -> runtime.shiftFrontPageDay(intent.days)
            is StoriesIntent.SelectFrontDate -> runtime.selectFrontPageDay(intent.day)
            is StoriesIntent.More -> runtime.menu(intent.action)
            is StoriesIntent.CacheStories -> runtime.requestStoryCache(intent.storyCount)
            is StoriesIntent.OpenLink -> withActiveStory(intent.storyId, runtime::selectStoryLink)
            is StoriesIntent.OpenComments ->
                withActiveStory(intent.storyId, runtime::selectStoryComments)
            is StoriesIntent.OpenCommentStory ->
                withActiveStory(intent.storyId, runtime::selectCommentStory)
            is StoriesIntent.CompletePreviewImage -> runtime.completePreviewImageLoad(
                intent.storyId,
                intent.pageUrl,
                intent.imageUrl,
                intent.success,
            )
            is StoriesIntent.RecordTint -> withActiveStory(intent.storyId) { story ->
                runtime.recordStoryResourceTint(
                    story,
                    if (intent.favicon) StoryResourceTintKind.FAVICON
                    else StoryResourceTintKind.PREVIEW_IMAGE,
                    intent.sourceUrl,
                    intent.baseColorArgb,
                    intent.paletteConfigKey,
                    intent.tintColorArgb,
                )
            }
            is StoriesIntent.VisibleRange -> {
                runtime.loadVisibleStories(intent.lastVisibleIndex)
                runtime.prefetchVisibleStoryResources(intent.lastVisibleIndex)
            }
            is StoriesIntent.OpenPreviewStory -> withActiveStory(intent.storyId) { story ->
                runtime.openStory(story, intent.showWebsite)
            }
            is StoriesIntent.PreviewAction -> withActiveStory(intent.storyId) { story ->
                runtime.previewAction(story, intent.action)
            }
        }
        if (intent is StoriesIntent.OpenSearch) {
            runtime.refreshBookmarksIfNeeded(hostStarted)
        } else if (intent is StoriesIntent.CloseSearch) {
            runtime.refreshBookmarksIfNeeded(hostStarted)
        }
        publish()
    }

    fun previewDeck(openedStoryId: Int, tintBaseColorArgb: Int): StoryPreviewDeck? =
        runtime.previewDeck(openedStoryId, tintBaseColorArgb)

    override fun close() {
        if (closed) return
        closed = true
        runtime.storyResources?.setResourceChangedListener(null)
        jobs.forEach(Job::cancel)
        jobs.clear()
        storyCache.dispose()
        runtime.dispose()
        scope.cancel()
    }

    private inline fun withActiveStory(storyId: Int, action: (Story) -> Unit) {
        runtime.activeStory(storyId)?.let(action)
    }

    private fun publish() {
        mutableState.value = snapshot()
    }

    private fun snapshot(): StoriesState {
        val presenterState = presenter.state.value
        val searchState = runtime.searchOptions.state.value
        val frontDate = runtime.frontPageDay
        return StoriesState(
            mainList = runtime.mainStore.state.value,
            searchList = runtime.searchStore.state.value,
            previewResources = runtime.previewResourceStates.toMap(),
            displaySettings = runtime.settingsState.value.displaySettings,
            availableStoryTypes = runtime.availableStoryTypes.toList(),
            selectedTypeIndex = runtime.selectedStoryTypeIndex(),
            currentType = runtime.currentType,
            searching = runtime.searching,
            searchDraft = presenterState.searchDraft,
            search = StoriesSearchSnapshot(
                sortLabel = runtime.searchOptions.sortLabel,
                dateLabel = runtime.searchOptions.dateRangeLabel,
                pointsLabel = runtime.searchOptions.minimumPointsLabel,
                commentsLabel = runtime.searchOptions.minimumCommentsLabel,
                options = searchState.options,
                loading = searchState.loading,
            ),
            refreshIndicatorShowing = runtime.refreshIndicatorShowing,
            updateAvailable = presenterState.updateAvailable,
            loadingFailedRateLimited = runtime.loadingFailedRateLimited,
            online = runtime.online,
            userItemsInitialLoadInProgress = runtime.isUserItemsInitialLoadInProgress,
            savedSourceHasItems = runtime.savedSourceHasItems,
            cachedStoriesAvailable = runtime.cachedStoriesAvailable,
            savedFilter = runtime.savedFilter,
            frontDateLabel = frontDate.requestParameter,
            frontDateSelectedMillis = frontDate.selectedMillis,
            frontDateEarliestMillis = frontDate.earliestMillis,
            frontDateLatestMillis = frontDate.latestMillis,
            loggedIn = runtime.loggedIn,
            canClearHistory = runtime.canClearHistory,
            cache = storyCache.state.value,
            lastUpdatedMillis = runtime.lastUpdatedMillisForHeader(),
        )
    }
}
