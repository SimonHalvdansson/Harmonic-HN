package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.cache.StoryCacheRequest
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintStore
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.CachedStoryHeader
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.network.StoryFeedResult
import com.simon.harmonichackernews.network.StoryPreviewResourceState
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.ObservableHistoryStore
import com.simon.harmonichackernews.settings.ContentFilters
import com.simon.harmonichackernews.settings.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Platform operations that remain after the shared stories feature has made a decision. */
sealed interface StoriesFeatureEffect {
    data class OpenStory(val destination: StoryDestination) : StoriesFeatureEffect

    data class OpenExternalLink(val url: String) : StoriesFeatureEffect
    data class Platform(val effect: StoriesPlatformEffect) : StoriesFeatureEffect
    data class StoryChanged(val storyId: Int? = null) : StoriesFeatureEffect
    data object LoginRequired : StoriesFeatureEffect
    data class UserMessage(val message: String) : StoriesFeatureEffect
    data class SavedActionFailed(
        val presentation: ActionFailurePresentation,
    ) : StoriesFeatureEffect
}

/**
 * Portable settings state for the stories feature.
 *
 * Version counters let a platform shell execute only facilities it owns, such as its font cache,
 * without rediscovering which preference changed. Preview reconciliation stays in shared code.
 */
data class StoriesSettingsState(
    val displaySettings: StoryDisplaySettings,
    val version: Long = 0L,
    val fontRefreshVersion: Long = 0L,
)

data class StoriesPreviewActionState(
    val voteLoadingIds: Set<Int> = emptySet(),
    val favoriteLoadingIds: Set<Int> = emptySet(),
)

data class StoryPreviewDeck(
    val stories: List<StoryListItemSnapshot>,
    val cardColors: List<Int>,
    val openedStoryId: Int,
)

private const val INITIAL_CACHE_ROWS = 12

/**
 * Lifecycle-independent stories-screen workflow.
 *
 * This owns feed/search application, active-list switching, pagination, saved-list reconciliation,
 * row loading and optimistic story actions. A platform coordinator supplies only storage/cache
 * ports and executes [StoriesRuntimeEffect]s such as navigation and image prefetching.
 */
class StoriesFeatureRuntime(
    private val scope: CoroutineScope,
    val sessionState: StoriesSessionState,
    private val requests: StoryRequests,
    private val savedItems: SavedItemsRepository,
    val savedItemActions: SavedItemActionUseCase,
    private val historyStore: ObservableHistoryStore,
    private val accounts: ObservableHackerNewsAccountRepository,
    private val connectivity: ConnectivityService,
    private val userSettings: UserSettings,
    private val loadContentFilters: () -> ContentFilters,
    private val rootStoryResolver: CommentMasterResolver,
    private val nowMillis: () -> Long,
    private val loadCachedStoryHeader: suspend (storyId: Int, rebuildIfMissing: Boolean) -> CachedStoryHeader?,
    private val loadCachedStories: () -> List<Story> = { emptyList() },
    private val hasCachedStories: () -> Boolean = { false },
    private val startStoryCache: (StoryCacheRequest) -> Unit = {},
    previewResourceService: StoryPreviewResourceService? = null,
    storyResourceTints: StoryResourceTintStore = StoryResourceTintStore.None,
    private val cacheDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableEffects = MutableSharedFlow<StoriesFeatureEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<StoriesFeatureEffect> = mutableEffects.asSharedFlow()

    val mainStore: StoryListStore = sessionState.mainStoryList
    val searchStore: StoryListStore = sessionState.searchStoryList
    val mainStories: List<Story> = mainStore.stories
    val searchStories: List<Story> = searchStore.stories
    val searchOptions: StorySearchStore = requests.searchStore
    val frontPageDay = FrontPageDayState(
        restoredMillis = sessionState.frontPageDayUtcMillis,
        nowMillis = nowMillis(),
    )

    private val feedRuntime = StoryFeedRuntime(
        sessionState = sessionState,
        readStoryIds = { historyStore.load().mapTo(mutableSetOf()) { it.id } },
        shouldHideReadStories = { hideRead },
        hydrateCachedStory = { false },
        shouldHideHydratedStory = { requests.shouldHideStory(it, currentType) },
    )
    private val searchRuntime = StorySearchRuntime()
    val storyResources = previewResourceService?.let { service ->
        StoryListResourceRuntime(
            scope = scope,
            service = service,
            settings = StoryDisplaySettings.from(userSettings.story),
            tintStore = storyResourceTints,
        )
    }

    val previewResourceStates: Map<Int, StoryPreviewResourceState>
        get() = storyResources?.states().orEmpty()

    private var paginationMode = false
    private var hideRead = false
    private var alwaysOpenComments = false
    private var useIntegratedWebView = false
    private var activeLoadedThrough = -1
    private var feedCache: FeedCachePreparation? = null
    private val visibleRanges = mutableMapOf<StoryListStore, IntRange>()

    // All bookkeeping and live rows are UI-owned. Only prepared headers cross dispatchers.
    private class FeedCachePreparation(
        val store: StoryListStore,
        val type: StoryType,
        val generation: Int,
        val ids: Set<Int>,
    ) {
        val prepared = mutableSetOf<Int>()
        val recover = mutableSetOf<Int>()
        val recovered = mutableSetOf<Int>()
        val protected = mutableSetOf<Int>()
        var firstVisible = 0
        var lastVisible: Int? = null
    }

    private var storiesBeforeSearch = false
    private var loadPendingBeforeSearch = false
    private var userItemsInitialLoadInProgress = false
    private var rateLimited = false
    private var lastSelectionMillis = 0L
    private var bookmarksChanged = false
    private var historyChangeVersion = -1L
    private var enabledAdditionalFrontpages: Set<String> = emptySet()
    private var preferredStoryTypeLabel: String = userSettings.story.preferredStoryType
    private val mutableSettingsState = MutableStateFlow(
        StoriesSettingsState(StoryDisplaySettings.from(userSettings.story)),
    )
    val settingsState: StateFlow<StoriesSettingsState> = mutableSettingsState.asStateFlow()
    val previewActionState: StateFlow<StoriesPreviewActionState> = sessionState.previewActionState

    var availableStoryTypes: List<StoryType> = listOf(StoryType.TOP_STORIES)
        private set

    var refreshIndicatorShowing: Boolean = false
        private set

    val activeStore: StoryListStore
        get() = if (searching) searchStore else mainStore

    val activeStories: List<Story>
        get() = activeStore.stories

    val currentType: StoryType
        get() = if (searching) sessionState.searchStoryType else sessionState.mainStoryType

    val searching: Boolean
        get() = sessionState.searching

    val failure: StoryLoadFailure?
        get() = activeStore.state.value.failure

    val loadingFailedRateLimited: Boolean
        get() = rateLimited || failure == StoryLoadFailure.RATE_LIMITED

    val savedFilter: SavedItemFilter
        get() = when (sessionState.userItemListFilter) {
            FILTER_STORIES -> SavedItemFilter.STORIES
            FILTER_COMMENTS -> SavedItemFilter.COMMENTS
            else -> SavedItemFilter.BOTH
        }

    val savedSourceHasItems: Boolean
        get() = when {
            currentType.isBookmarks -> sessionState.bookmarkStories.isNotEmpty()
            currentType.isUserItemList -> sessionState.userItemListStories.isNotEmpty()
            else -> false
        }

    val isUserItemsInitialLoadInProgress: Boolean
        get() = userItemsInitialLoadInProgress

    val online: Boolean
        get() = connectivity.isOnline()

    val loggedIn: Boolean
        get() = accounts.currentAccount != null

    val historyState = historyStore.historyState

    val canClearHistory: Boolean
        get() = currentType.isHistory && historyStore.size > 0

    var cachedStoriesAvailable: Boolean = false
        private set
    private var feedLoadJob: Job? = null
    private var nextScrapedPageJob: Job? = null
    private var feedPreparationJob: Job? = null
    private var cacheAvailabilityJob: Job? = null

    init {
        configure(userSettings, loadContentFilters())
        scope.launch { requests.effects.collect(::applyRequestEffect) }
        scope.launch { requests.searchStore.state.collect(::applySearchState) }
        scope.launch { userSettings.changes.collect { reconcileSettings() } }
        scope.launch {
            accounts.accountState.drop(1).collect { refreshAccountState() }
        }
        cacheAvailabilityJob = scope.launch {
            mainStore.state.map { it.failure }.distinctUntilChanged().collectLatest { failure ->
                cachedStoriesAvailable = failure != null && withContext(cacheDispatcher) { hasCachedStories() }
                emit(StoriesFeatureEffect.StoryChanged())
            }
        }
    }

    fun configure(
        pagination: Boolean,
        hideRead: Boolean,
        alwaysOpenComments: Boolean,
        useIntegratedWebView: Boolean,
    ) {
        val paginationChanged = paginationMode != pagination
        paginationMode = pagination
        this.hideRead = hideRead
        this.alwaysOpenComments = alwaysOpenComments
        this.useIntegratedWebView = useIntegratedWebView
        updatePaginationModes()
        if (paginationChanged) {
            mainStore.setVisibleStoryCount(initialVisibleCount(mainStore))
            searchStore.setVisibleStoryCount(initialVisibleCount(searchStore))
            activeLoadedThrough = min(
                activeLoadedThrough,
                activeStore.state.value.visibleStoryCount - 1,
            )
        }
    }

    /** Applies all portable story/feed preferences and reports whether filtering changed. */
    fun configure(settings: UserSettings, filters: ContentFilters): Boolean {
        val story = settings.story
        configure(
            pagination = story.pagination,
            hideRead = story.hideRead,
            alwaysOpenComments = story.alwaysOpenComments,
            useIntegratedWebView = settings.reading.integratedWebView,
        )
        return requests.configureVisibility(filters, story.hideJobs)
    }

    fun initializeHistory() {
        historyStore.initialize()
        historyChangeVersion = historyStore.changeVersion
    }

    fun initialize(
        preferredTypeLabel: CharSequence?,
        enabledAdditionalFrontpages: Set<String>,
        hasAccount: Boolean,
        restoring: Boolean,
        frontpageOrder: List<String> = userSettings.story.frontpageOrder,
        bookmarksEnabled: Boolean = userSettings.general.bookmarksEnabled,
    ) {
        availableStoryTypes = StoryTypeMenuPolicy.availableTypes(
            enabledAdditionalFrontpages,
            hasAccount,
            frontpageOrder,
            bookmarksEnabled,
        )
        this.enabledAdditionalFrontpages = enabledAdditionalFrontpages
        val preferredType = StoryTypeMenuPolicy.preferred(
            preferredTypeLabel,
            availableStoryTypes,
        )
        if (!sessionState.initialized) {
            selectType(StoryListTarget.MAIN, preferredType)
            selectType(StoryListTarget.SEARCH, preferredType)
        } else {
            if (sessionState.mainStoryType !in availableStoryTypes) {
                selectType(StoryListTarget.MAIN, preferredType)
                clearStore(mainStore, preferredType)
                if (searching) loadPendingBeforeSearch = true
            }
            if (sessionState.searchStoryType !in availableStoryTypes) {
                selectType(StoryListTarget.SEARCH, preferredType)
            }
        }
        updatePaginationModes()
        if (!restoring) {
            mainStore.setVisibleStoryCount(initialVisibleCount(mainStore))
            searchStore.setVisibleStoryCount(initialVisibleCount(searchStore))
        } else {
            feedRuntime.restoreScrapedPagination(currentType.takeIf(StoryType::isScrapedFrontpage))
            activeLoadedThrough = activeStore.state.value.loadedThroughIndex
        }
    }

    fun initialize(settings: UserSettings, hasAccount: Boolean, restoring: Boolean) = initialize(
        preferredTypeLabel = settings.story.preferredStoryType,
        enabledAdditionalFrontpages = settings.story.additionalFrontpages,
        hasAccount = hasAccount,
        restoring = restoring,
        frontpageOrder = settings.story.frontpageOrder,
        bookmarksEnabled = settings.general.bookmarksEnabled,
    )

    fun initialize(restoring: Boolean) = initialize(
        settings = userSettings,
        hasAccount = loggedIn,
        restoring = restoring,
    )

    /**
     * Reconciles persisted settings with all shared feed and presentation state.
     *
     * The platform observes [settingsState] only to perform facilities such as refreshing its font
     * cache or prefetching images. It does not decide what changed or which shared data to reload.
     */
    fun reconcileSettings(): StoryDisplaySettings.UpdateResult {
        val storyPreferences = userSettings.story
        val currentState = mutableSettingsState.value
        val nextDisplaySettings = StoryDisplaySettings.from(storyPreferences)
        val update = nextDisplaySettings.changesFrom(currentState.displaySettings)
        val hideReadChanged = hideRead != storyPreferences.hideRead
        val preferredStoryTypeChanged =
            preferredStoryTypeLabel != storyPreferences.preferredStoryType
        preferredStoryTypeLabel = storyPreferences.preferredStoryType
        storyResources?.updateSettings(nextDisplaySettings)
        val filtersChanged = configure(userSettings, loadContentFilters())

        var feedRefreshStarted = updateAvailableStoryTypes(
            enabledAdditionalFrontpages = storyPreferences.additionalFrontpages,
            hasAccount = loggedIn,
        )

        if (sessionState.initialized && preferredStoryTypeChanged) {
            val preferredType = StoryTypeMenuPolicy.preferred(
                storyPreferences.preferredStoryType,
                availableStoryTypes,
            )
            if (preferredType != currentType) {
                selectTypeAndRefresh(preferredType)
                feedRefreshStarted = true
            }
        }

        if (update.itemsChanged) {
            mutableSettingsState.value = currentState.copy(
                displaySettings = nextDisplaySettings,
                version = currentState.version + 1L,
                fontRefreshVersion = currentState.fontRefreshVersion +
                    if (update.fontChanged) 1L else 0L,
            )
            publishAllStoryContentChanged()
            changed()
        }

        if (sessionState.initialized && !feedRefreshStarted && hideReadChanged) {
            refresh(showSwipeRefreshIndicator = false)
            feedRefreshStarted = true
        }
        if (sessionState.initialized && !feedRefreshStarted && filtersChanged) {
            refresh(showSwipeRefreshIndicator = false)
        }
        if (update.previewImageModeChanged) prefetchVisibleStoryResources()
        evaluateUpdate(storyPreferences.alwaysShowTapToRefresh)
        return update
    }

    /** Applies portable work associated with a host becoming active again. */
    fun resume(hostStarted: Boolean) {
        reconcileSettings()
        syncVisibleUserItemsWithCache()
        refreshBookmarksIfNeeded(hostStarted)
        syncHistoryIfChanged()
        changed()
    }

    fun refreshAccountState() {
        requests.cancelUserItemsLoad()
        savedItems.refreshAccountScope()
        updateAvailableStoryTypes(
            enabledAdditionalFrontpages = userSettings.story.additionalFrontpages,
            hasAccount = loggedIn,
        )
        syncVisibleUserItemsWithCache()
        if (currentType.isUserItemList) refresh(showSwipeRefreshIndicator = false)
        changed()
    }

    fun requestStoryCache(storyCount: Int, downloadWebViewContents: Boolean) {
        userSettings.setStoriesToCache(storyCount)
        val cache = userSettings.cache
        startStoryCache(
            StoryCacheRequest(
                storyCount = cache.storiesToCache,
                cacheArticleSnapshots = downloadWebViewContents,
            ),
        )
    }

    fun evaluateUpdate(alwaysShow: Boolean) {
        val updateAvailable = StoryFeedRefreshPolicy.shouldShowRefreshPrompt(
            nowMillis = nowMillis(),
            lastLoadedMillis = sessionState.lastLoaded,
            alwaysShow = alwaysShow,
            searching = searching,
            storyType = currentType,
        )
        if (sessionState.showRefreshPrompt != updateAvailable) {
            sessionState.showRefreshPrompt = updateAvailable
            emit(StoriesFeatureEffect.StoryChanged())
        }
    }

    fun selectType(target: StoryListTarget, type: StoryType) {
        if (type != currentType) visibleRanges.remove(store(target))
        when (target) {
            StoryListTarget.MAIN -> sessionState.mainStoryType = type
            StoryListTarget.SEARCH -> sessionState.searchStoryType = type
        }
        store(target).setPaginationEnabled(shouldUsePagination(type))
    }

    fun selectTypeAndRefresh(type: StoryType) {
        if (type !in availableStoryTypes) return
        selectType(StoryListTarget.MAIN, type)
        refresh(showSwipeRefreshIndicator = false, showMainLoadingIndicator = true)
    }

    /** Updates the portable source menu and returns true if the selected feed was replaced. */
    fun updateAvailableStoryTypes(
        enabledAdditionalFrontpages: Set<String>,
        hasAccount: Boolean,
        frontpageOrder: List<String> = userSettings.story.frontpageOrder,
    ): Boolean {
        this.enabledAdditionalFrontpages = enabledAdditionalFrontpages
        val next = StoryTypeMenuPolicy.availableTypes(
            enabledAdditionalFrontpages, hasAccount, frontpageOrder, userSettings.general.bookmarksEnabled,
        )
        if (next == availableStoryTypes) return false
        availableStoryTypes = next
        if (sessionState.searchStoryType !in next) {
            selectType(StoryListTarget.SEARCH, StoryType.TOP_STORIES)
        }
        if (sessionState.mainStoryType !in next) {
            if (searching) {
                // The retained feed is no longer available. Keep search open, but load the
                // replacement feed when returning instead of exposing the old personal list.
                selectType(StoryListTarget.MAIN, StoryType.TOP_STORIES)
                clearStore(mainStore, StoryType.TOP_STORIES)
                loadPendingBeforeSearch = true
                changed()
                return false
            }
            selectTypeAndRefresh(StoryType.TOP_STORIES)
            return true
        }
        changed()
        return false
    }

    fun storyTypeAt(index: Int): StoryType =
        availableStoryTypes.getOrNull(index) ?: StoryType.UNKNOWN

    fun selectedStoryTypeIndex(): Int =
        availableStoryTypes.indexOf(sessionState.mainStoryType)

    fun shiftFrontPageDay(days: Int) {
        frontPageDay.shift(days)
        sessionState.frontPageDayUtcMillis = frontPageDay.selectedMillis
        if (!searching && currentType.isFront) refresh(false, true)
    }

    fun selectFrontPageDay(selection: Long) {
        frontPageDay.select(selection)
        sessionState.frontPageDayUtcMillis = frontPageDay.selectedMillis
        if (!searching && currentType.isFront) refresh(false, true)
    }

    fun openSearch() {
        if (searching) return
        storiesBeforeSearch = true
        loadPendingBeforeSearch = mainStories.isEmpty() && failure == null &&
            !currentType.isBookmarks && !currentType.isUserItemList
        beginGeneration()
        searchOptions.resetOptions()
        retainSearchOptions()
        sessionState.searching = true
        selectType(StoryListTarget.SEARCH, sessionState.mainStoryType)
        clearStore(searchStore, sessionState.searchStoryType)
        refreshIndicatorShowing = false
        rateLimited = false
        changed()
    }

    /** Returns true when retained main content was restored and no refresh was needed. */
    fun closeSearch(): Boolean {
        if (!searching) return false
        sessionState.lastSearch = ""
        searchOptions.resetOptions()
        retainSearchOptions()
        beginGeneration()
        sessionState.searching = false
        clearStore(searchStore, sessionState.searchStoryType)
        refreshIndicatorShowing = false
        rateLimited = false
        val retainedMain = storiesBeforeSearch
        storiesBeforeSearch = false
        val shouldRefresh = loadPendingBeforeSearch && mainStories.isEmpty()
        loadPendingBeforeSearch = false
        changed()
        if (shouldRefresh || !retainedMain) {
            refresh(false)
            return false
        }
        resumeInterruptedLoads()
        return true
    }

    fun submitSearch(query: String) {
        sessionState.lastSearch = query
        searchOptions.search(query)
    }

    fun selectSearchOption(option: StorySearchOption, index: Int) {
        when (option) {
            StorySearchOption.SORT -> searchOptions.selectSort(index)
            StorySearchOption.DATE -> searchOptions.selectDateRange(index)
            StorySearchOption.POINTS -> searchOptions.selectMinimumPoints(index)
            StorySearchOption.COMMENTS -> searchOptions.selectMinimumComments(index)
        }
        retainSearchOptions()
        retrySearch()
    }

    fun toggleOnlyRead() {
        searchOptions.toggleOnlyRead()
        retainSearchOptions()
        retrySearch()
    }

    fun refresh(
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean = false,
    ) {
        if (currentType.isBookmarks) bookmarksChanged = false
        sessionState.showRefreshPrompt = false
        val type = currentType
        val plan = StoryFeedRefreshPolicy.plan(
            searching = searching,
            storyType = type,
            showSwipeRefreshIndicator = showSwipeRefreshIndicator,
            showMainLoadingIndicator = showMainLoadingIndicator,
            listIsEmpty = activeStories.isEmpty(),
        )
        if (plan.source == StoryFeedSource.SEARCH) {
            submitSearch(sessionState.lastSearch)
            return
        }

        refreshIndicatorShowing = plan.showRefreshIndicator
        rateLimited = false
        val generation = beginGeneration()
        activeStore.beginLoad(
            refreshing = plan.showRefreshIndicator,
            clearItems = plan.clearItems,
        )
        if (plan.clearItems) {
            userItemsInitialLoadInProgress = plan.source == StoryFeedSource.USER_ITEMS
            replaceActive(emptyList())
        }
        if (plan.recordRefreshTime) sessionState.lastLoaded = nowMillis()

        when (plan.source) {
            StoryFeedSource.SEARCH -> Unit
            StoryFeedSource.ALGOLIA -> searchOptions.loadTopStories(
                storyType = type,
                startTime = searchOptions.getTopStoriesStartTime(type),
            )
            StoryFeedSource.BOOKMARKS -> loadBookmarks()
            StoryFeedSource.USER_ITEMS -> loadUserItems(plan, generation)
            StoryFeedSource.HISTORY -> loadHistory()
            StoryFeedSource.FRONTPAGE_LINKS,
            StoryFeedSource.SCRAPED_FRONTPAGE,
            StoryFeedSource.UNSLOP_RSS,
            StoryFeedSource.HACKER_NEWS_API,
            -> loadFeed(
                type,
                frontPageDay.requestParameter.takeIf { type.isFront },
                generation,
            )
        }
        changed()
    }

    fun loadMore() {
        val state = activeStore.state.value
        when {
            state.paginationEnabled && state.visibleStoryCount < activeStories.size -> {
                val generation = requests.storyLoadGeneration
                val plan = activeStore.beginNextPage(generation) ?: return
                if (!activeStore.hasPendingPageStories()) activeStore.clearPendingPage()
                loadThrough(plan.targetLoadedIndex, generation)
                retryUnsettledThrough(plan.targetLoadedIndex, generation)
            }
            state.canLoadMore && currentType.isScrapedFrontpage -> {
                val next = feedRuntime.beginNextScrapedPage(activeStore, currentType) ?: return
                loadNextScrapedPage(currentType, next, requests.storyLoadGeneration)
            }
            state.canLoadMore && !searchOptions.state.value.loading -> {
                searchRuntime.beginLoadMore(activeStore)
                searchOptions.loadMore()
            }
        }
        continueFeedCachePreparation()
        changed()
    }

    fun loadVisibleStories(lastVisibleIndex: Int? = null, firstVisibleIndex: Int = 0) {
        // Retained feed rows can outlive this runtime or a search generation. Their IDs are
        // already known; resume lazy cache preparation without enumerating the cache itself.
        if (feedCache == null && activeStories.isNotEmpty() && !searching && !currentType.isAlgolia &&
            !currentType.isBookmarks && !currentType.isUserItemList && !currentType.isHistory &&
            !activeStore.state.value.showingCached && !activeStore.state.value.loading &&
            !activeStore.state.value.refreshing
        ) {
            feedCache = FeedCachePreparation(
                activeStore, currentType, requests.storyLoadGeneration,
                activeStories.mapTo(mutableSetOf(), Story::id),
            )
        }
        if (lastVisibleIndex != null) {
            visibleRanges[activeStore] = firstVisibleIndex.coerceAtLeast(0)..lastVisibleIndex
        }
        feedCache?.let { cache ->
            if (lastVisibleIndex != null) {
                cache.firstVisible = firstVisibleIndex.coerceAtLeast(0)
                cache.lastVisible = lastVisibleIndex
            }
        }
        val target = (lastVisibleIndex ?: feedCache?.lastVisible)?.let { lastVisible ->
            StoryPaginationPolicy.scrolledLoadTargetIndex(
                storyCount = activeStories.size,
                lastVisibleIndex = lastVisible,
                initialLoadCount = initialLoadCount(),
            )
        } ?: StoryPaginationPolicy.visibleLoadTargetIndex(
            storyCount = activeStories.size,
            paginationEnabled = activeStore.state.value.paginationEnabled,
            visibleStoryCount = activeStore.state.value.visibleStoryCount,
        )
        val generation = requests.storyLoadGeneration
        loadThrough(target, generation)
        retryUnsettledThrough(target, generation)
        continueFeedCachePreparation()
    }

    fun selectStoryLink(story: Story) {
        if (story !in activeStories || !canSelect()) return
        when {
            !story.loaded && story.loadingFailed -> {
                activeStore.updateStory(story.id) { loadingFailed = false }
                loadStory(story, requests.storyLoadGeneration)
                changed(story)
            }
            !story.loaded -> Unit
            story.isFrontpageLink -> openExternalStory(story)
            alwaysOpenComments -> openStory(story, showWebsite = false)
            story.isLink && useIntegratedWebView -> openStory(story, showWebsite = true)
            story.isLink -> openExternalStory(story)
            else -> openStory(story, showWebsite = false)
        }
    }

    fun selectStoryComments(story: Story) {
        if (story !in activeStories || !canSelect() || !story.loaded) return
        if (story.isFrontpageLink) openExternalStory(story)
        else openStory(story, showWebsite = false)
    }

    private fun openExternalStory(story: Story) {
        val url = story.url ?: return
        if (story.isFrontpageLink) updateStoryReadState(story, true) else markRead(story)
        changed(story)
        emit(StoriesFeatureEffect.OpenExternalLink(url))
    }

    fun selectCommentStory(story: Story) {
        if (story !in activeStories) return
        val master = story.toRootStory()
        if (master == null) {
            selectStoryComments(story)
            return
        }
        if (master.loaded) {
            openStory(master, false)
            return
        }
        scope.launch {
            val resolved = try {
                rootStoryResolver.resolve(story)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                master
            }
            if (activeStories.contains(story)) {
                changed(story)
                openStory(resolved, false)
            }
        }
    }

    fun openStory(story: Story, showWebsite: Boolean) {
        markRead(story)
        changed(story)
        emit(StoriesFeatureEffect.OpenStory(story.toDestination(showWebsite = showWebsite)))
    }

    fun previewStories(openedStoryId: Int): List<Story> {
        val candidates = activeStories
            .take(activeStore.visibleStoryItemCount)
            .filter { story ->
                !story.isComment && story.loaded && (!story.isLink || !story.url.isNullOrEmpty())
            }
        return candidates.takeIf { stories -> stories.any { it.id == openedStoryId } }.orEmpty()
    }

    fun activeStory(storyId: Int): Story? = activeStories.firstOrNull { it.id == storyId }

    fun completePreviewImageLoad(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
        success: Boolean,
    ) = storyResources?.completePreviewImageLoad(storyId, pageUrl, imageUrl, success)

    fun recordStoryResourceTint(
        story: Story,
        kind: StoryResourceTintKind,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ): Boolean = storyResources?.recordTint(
        story,
        kind,
        sourceUrl,
        baseColorArgb,
        paletteConfigKey,
        tintColorArgb,
    ) ?: false

    fun prefetchVisibleStoryResources(lastVisibleIndex: Int = -1) {
        val store = activeStore
        storyResources?.prefetchNearViewport(
            stories = activeStories,
            initialLoadCount = previewPrefetchInitialLoadCount,
            lastVisibleItem = lastVisibleIndex,
            paginationVisibleCount = store.state.value.visibleStoryCount
                .takeIf { store.state.value.paginationEnabled },
        )
    }

    fun previewDeck(openedStoryId: Int, tintBaseColorArgb: Int): StoryPreviewDeck? {
        val stories = previewStories(openedStoryId)
        if (stories.isEmpty()) return null
        stories.forEach { storyResources?.requestForDialog(it) }
        return StoryPreviewDeck(
            stories = stories.map { story ->
                StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
            },
            cardColors = stories.map { story ->
                storyResources?.resolveCardBackgroundColor(story, tintBaseColorArgb)
                    ?: tintBaseColorArgb
            },
            openedStoryId = openedStoryId,
        )
    }

    val previewPrefetchInitialLoadCount: Int
        get() = if (activeStore.state.value.paginationEnabled) {
            StoryPaginationPolicy.DEFAULT_PAGE_SIZE
        } else {
            StoryPaginationPolicy.DEFAULT_INITIAL_LOAD_COUNT
        }

    fun lastUpdatedMillisForHeader(): Long? = sessionState.lastLoaded.takeIf {
        sessionState.showRefreshPrompt && !searching && it > 0L
    }

    fun notifySavedItemsChanged(source: SavedItemSource) {
        if (source == SavedItemSource.BOOKMARKS) bookmarksChanged = true
    }

    fun handleMenuAction(action: StoriesMenuAction) {
        if (action == StoriesMenuAction.CLEAR_HISTORY) {
            scope.launch {
                historyStore.clearHistory()
                clearActiveStories()
            }
            return
        }
        val account = accounts.currentAccount
        if (action == StoriesMenuAction.ACCOUNT && account != null) {
            scope.launch {
                accounts.clearAccount()
                updateAvailableStoryTypes(enabledAdditionalFrontpages, hasAccount = false)
                emit(StoriesFeatureEffect.UserMessage("Logged out"))
            }
            return
        }
        when (val effect = StoriesUiOrchestrator.menu(action, account?.username)) {
            null -> Unit
            else -> emit(StoriesFeatureEffect.Platform(effect))
        }
    }

    fun handlePreviewAction(story: Story, action: StoryPreviewActionKind) {
        if (story !in activeStories) return
        when (action) {
            StoryPreviewActionKind.Vote -> toggleVote(story)
            StoryPreviewActionKind.Read -> toggleRead(story)
            StoryPreviewActionKind.Bookmark -> toggleBookmark(story)
            StoryPreviewActionKind.Favorite -> toggleFavorite(story)
        }
    }

    fun refreshBookmarksIfNeeded(hostStarted: Boolean): Boolean {
        if (!bookmarksChanged || searching || !hostStarted || !currentType.isBookmarks) return false
        bookmarksChanged = false
        refresh(false)
        return true
    }

    private fun toggleRead(story: Story) {
        val read = !story.isRead
        updateStoryReadState(story, read)
        scope.launch {
            if (read) {
                historyStore.recordHistory(story.id, nowMillis())
            } else {
                historyStore.removeHistory(story.id)
            }
        }
        changed(story)
    }

    private fun toggleBookmark(story: Story) {
        val bookmarksList = currentType.isBookmarks
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val bookmarked = savedItemActions.toggleBookmarkAtomic(story.id)
            if (!bookmarked && bookmarksList) {
                sessionState.bookmarkStories.remove(story)
                removeStory(story, loadReplacement = true)
            } else {
                changed(story)
            }
        }
    }

    private fun toggleVote(story: Story) {
        val generation = requests.storyLoadGeneration
        val expectedStore = activeStore
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!sessionState.beginPreviewAction(story.id, StoryPreviewActionKind.Vote)) {
                return@launch
            }
            try {
                when (
                    val outcome = savedItemActions.toggleVoteAndExecuteAtomic(
                        itemId = story.id,
                        isComment = false,
                        onPending = { changed(story) },
                    )
                ) {
                    is SavedItemActionOutcome.Success -> Unit
                    is SavedItemActionOutcome.Failure -> {
                        if (isCurrentActionContext(generation, expectedStore)) changed(story)
                        emit(
                            StoriesFeatureEffect.SavedActionFailed(
                                ActionFailurePresentation(
                                    result = outcome.result,
                                    message = "Action unsuccessful, see dialog for response",
                                    showDetails = true,
                                ),
                            ),
                        )
                    }
                    is SavedItemActionOutcome.Indeterminate -> emit(
                        StoriesFeatureEffect.SavedActionFailed(
                            ActionFailurePresentation(
                                result = outcome.result,
                                message = "Action sent, but HN confirmation was interrupted",
                                showDetails = true,
                            ),
                        ),
                    )
                }
            } finally {
                sessionState.finishPreviewAction(story.id, StoryPreviewActionKind.Vote)
            }
        }
    }

    private fun toggleFavorite(story: Story) {
        val generation = requests.storyLoadGeneration
        val expectedStore = activeStore
        val favoritesList = currentType.isFavorites
        val optimisticIndex = activeStories.indexOf(story)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (!sessionState.beginPreviewAction(story.id, StoryPreviewActionKind.Favorite)) {
                return@launch
            }
            var wasFavorited = false
            try {
                when (
                    val outcome = savedItemActions.toggleFavoriteAndExecuteAtomic(
                        itemId = story.id,
                        onPending = { action ->
                            wasFavorited = action.previousPresent
                            if (wasFavorited && favoritesList && optimisticIndex >= 0) {
                                removeStory(story, loadReplacement = true)
                            } else {
                                changed(story)
                            }
                        },
                    )
                ) {
                    is SavedItemActionOutcome.Success -> Unit
                    is SavedItemActionOutcome.Failure -> {
                        if (isCurrentActionContext(generation, expectedStore)) {
                            if (wasFavorited && favoritesList && !activeStories.contains(story)) {
                                activeStore.insertAt(optimisticIndex.coerceAtLeast(0), story)
                                changed()
                            } else {
                                changed(story)
                            }
                        }
                        emit(
                            StoriesFeatureEffect.SavedActionFailed(
                                ActionFailurePresentation(
                                    result = outcome.result,
                                    message = "Action unsuccessful, see dialog for response",
                                    showDetails = true,
                                ),
                            ),
                        )
                    }
                    is SavedItemActionOutcome.Indeterminate -> emit(
                        StoriesFeatureEffect.SavedActionFailed(
                            ActionFailurePresentation(
                                result = outcome.result,
                                message = "Action sent, but HN confirmation was interrupted",
                                showDetails = true,
                            ),
                        ),
                    )
                }
            } finally {
                sessionState.finishPreviewAction(story.id, StoryPreviewActionKind.Favorite)
            }
        }
    }

    fun selectSavedFilter(filter: SavedItemFilter) {
        sessionState.userItemListFilter = when (filter) {
            SavedItemFilter.STORIES -> FILTER_STORIES
            SavedItemFilter.BOTH -> FILTER_BOTH
            SavedItemFilter.COMMENTS -> FILTER_COMMENTS
        }
        applySavedFilter()
    }

    fun syncVisibleUserItemsWithCache() {
        if (!currentType.isUserItemList) return
        val snapshot = savedItems.loadSnapshot(currentUserItemSource())
        syncUserItemStories(snapshot.itemIds, snapshot.commentIds)
    }

    fun showCachedStories(cachedStories: List<Story>) {
        beginGeneration()
        activeStore.setShowingCached(true)
        activeStore.setFailure(null)
        refreshIndicatorShowing = false
        rateLimited = false
        replaceActive(cachedStories)
        activeStore.markLoadedThrough(cachedStories.lastIndex)
        cachedStories.filter(Story::loaded).forEach(::prefetch)
        changed()
    }

    fun showCachedStories() {
        val generation = beginGeneration()
        val target = activeStore
        target.beginLoad(refreshing = false, clearItems = false)
        feedPreparationJob = scope.launch {
            try {
                val stories = withContext(cacheDispatcher) { loadCachedStories() }
                if (!isCurrentActionContext(generation, target)) return@launch
                feedPreparationJob = null
                showCachedStories(stories)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrentActionContext(generation, target)) {
                    target.fail(StoryLoadFailure.GENERAL)
                    changed()
                }
            }
        }
    }

    fun clearActiveStories() {
        beginGeneration()
        activeStore.cancelTransientLoads()
        activeStore.clear()
        activeStore.setPaginationEnabled(shouldUsePagination(currentType))
        activeStore.setVisibleStoryCount(initialVisibleCount(activeStore))
        activeStore.setCanLoadMore(false)
        activeStore.setFailure(null)
        refreshIndicatorShowing = false
        rateLimited = false
        changed()
    }

    fun shouldRefreshRestoredState(): Boolean = StoryFeedRefreshPolicy.shouldRefreshRestoredState(
        failure = activeStore.state.value.failure,
        listIsEmpty = activeStories.isEmpty(),
        searching = searching,
        searchQuery = sessionState.lastSearch,
        storyType = currentType,
    )

    fun resumeRetainedLoads() = resumeInterruptedLoads()

    fun syncHistoryIfChanged(): StoryHistorySyncResult {
        val currentVersion = historyStore.changeVersion
        if (currentVersion == historyChangeVersion) return StoryHistorySyncResult.UNCHANGED
        historyChangeVersion = currentVersion
        val result = activeStore.syncHistory(
            readStoryIds = historyStore.load().mapTo(mutableSetOf()) { it.id },
            searchingOnlyRead = searching && searchOptions.state.value.options.onlyRead,
            showingHistory = currentType.isHistory,
            hideRead = hideRead,
        )
        when (result) {
            StoryHistorySyncResult.ITEMS_REMOVED -> loadVisibleStories()
            StoryHistorySyncResult.REFRESH_REQUIRED -> refresh(false)
            StoryHistorySyncResult.CONTENT_CHANGED,
            StoryHistorySyncResult.UNCHANGED -> Unit
        }
        if (result != StoryHistorySyncResult.UNCHANGED) changed()
        return result
    }

    fun mergeExternalStoryUpdate(update: Story): Boolean {
        var matched = false
        for (store in listOf(mainStore, searchStore)) {
            if (store.mergeStoryContent(update)) matched = true
        }
        if (matched) {
            feedCache?.protected?.add(update.id)
            activeStories.firstOrNull { it.id == update.id }?.let { story ->
                if (activeStories.indexOf(story) <= activeLoadedThrough) {
                    loadStory(story, requests.storyLoadGeneration)
                }
            }
            emit(StoriesFeatureEffect.StoryChanged(update.id))
        }
        return matched
    }

    fun publishAllStoryContentChanged() {
        mainStore.contentChanged()
        searchStore.contentChanged()
    }

    fun dispose() {
        cancelFeedLoads()
        cacheAvailabilityJob?.cancel()
        requests.cancelUserItemsLoad()
        requests.clearStoryRowLoads()
        mainStore.cancelTransientLoads()
        searchStore.cancelTransientLoads()
        storyResources?.dispose()
    }

    private fun applyRequestEffect(effect: StoryRequestEvent) {
        when (effect) {
            is StoryRequestEvent.StoryRowLoaded -> applyRowLoaded(effect)
            is StoryRequestEvent.StoryRowRejected -> if (isCurrentRow(effect.story, effect.generation)) {
                removeStory(effect.story)
            }
            is StoryRequestEvent.StoryRowLoadAttemptFailed -> if (
                isCurrentRow(effect.story, effect.generation)
            ) {
                if (effect.finalAttempt) {
                    activeStore.finishNextPageStory(effect.story.id, effect.generation)
                }
                feedCache?.recover?.add(effect.story.id)
                continueFeedCachePreparation()
                changed(effect.story)
            }
            is StoryRequestEvent.UserItemsSynced -> applyUserItems(effect)
            is StoryRequestEvent.UserItemsSyncFailed -> applyUserItemsFailure(effect)
        }
    }

    private fun loadFeed(storyType: StoryType, frontDay: String?, generation: Int) {
        feedLoadJob?.cancel()
        nextScrapedPageJob?.cancel()
        feedLoadJob = scope.launch {
            try {
                val result = requests.loadFeed(storyType, frontDay)
                if (!isCurrentFeed(storyType, generation)) return@launch
                val ids = when (result) {
                    is StoryFeedResult.ItemIds -> result.ids
                    is StoryFeedResult.Scraped -> result.page.itemIds
                    is StoryFeedResult.LinkDirectory -> emptyList()
                }
                val commentIds = (result as? StoryFeedResult.Scraped)?.page?.commentIds.orEmpty().toSet()
                prepareFeedCache(ids, storyType, generation, commentIds) { cached ->
                    refreshIndicatorShowing = false
                    rateLimited = false
                    val application = feedRuntime.applyInitial(activeStore, storyType, result, cached)
                    if (application.loadVisibleStories) {
                        requests.invalidateLoadedStoryRows(application.loadedStories)
                        loadVisibleStories()
                    }
                    application.loadedStories.filter(Story::loaded).forEach(::prefetch)
                    changed()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentFeed(storyType, generation)) return@launch
                refreshIndicatorShowing = false
                val failure = StoryFeedRefreshPolicy.failureFor(error)
                rateLimited = failure == StoryLoadFailure.RATE_LIMITED
                activeStore.fail(failure)
                changed()
            }
        }
    }

    private fun loadNextScrapedPage(storyType: StoryType, nextPageUrl: String, generation: Int) {
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = scope.launch {
            try {
                val page = requests.loadNextScrapedPage(storyType, nextPageUrl)
                if (!isCurrentFeed(storyType, generation)) return@launch
                prepareFeedCache(
                    page.itemIds, storyType, generation, page.commentIds.toSet(), append = true,
                ) { cached ->
                    val application = feedRuntime.applyNextScrapedPage(activeStore, storyType, page, cached)
                    if (application.loadVisibleStories) {
                        requests.invalidateLoadedStoryRows(application.loadedStories)
                        loadVisibleStories()
                    }
                    application.loadedStories.filter(Story::loaded).forEach(::prefetch)
                    changed()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentFeed(storyType, generation)) return@launch
                feedRuntime.failNextScrapedPage(activeStore, storyType)
                changed()
            }
        }
    }

    private fun prepareFeedCache(
        ids: List<Int>,
        type: StoryType,
        generation: Int,
        commentIds: Set<Int>,
        append: Boolean = false,
        apply: (Map<Int, Story>) -> Unit,
    ) {
        feedPreparationJob?.cancel()
        val target = activeStore
        val previous = feedCache?.takeIf { append && isCurrentCache(it) }
        val existing = target.stories.mapTo(mutableSetOf(), Story::id)
        val existingLoaded = target.stories.filter(Story::loaded).mapTo(mutableSetOf(), Story::id)
        val cache = FeedCachePreparation(
            target, type, generation, if (append) existing + ids else ids.toSet(),
        )
        previous?.let {
            cache.prepared.addAll(it.prepared)
            cache.recover.addAll(it.recover)
            cache.recovered.addAll(it.recovered)
            cache.protected.addAll(it.protected)
        }
        feedCache = cache
        visibleRanges[target]?.let {
            cache.firstVisible = it.first
            cache.lastVisible = it.last
        }
        val readIds = if (hideRead) historyStore.load().mapTo(mutableSetOf()) { it.id } else emptySet()
        val candidates = ids.filterNot { (hideRead && it in readIds) || (append && it in existing) }
        if (!append) {
            cache.firstVisible = min(cache.firstVisible, (candidates.size - INITIAL_CACHE_ROWS).coerceAtLeast(0))
        }
        feedPreparationJob = scope.launch {
            val cached = mutableMapOf<Int, Story>()
            var cursor = if (append) 0 else cache.firstVisible
            var visible = 0
            // Fill a screen plus a small buffer, continuing past cached rows hidden by filters.
            while (cursor < candidates.size && visible < INITIAL_CACHE_ROWS) {
                val batch = candidates.drop(cursor).take(INITIAL_CACHE_ROWS - visible)
                cursor += batch.size
                val prepared = readFeedCache(batch.filterNot(existing::contains), rebuild = false) { header ->
                    Story("Loading...", header.storyId, false, false).takeIf(header::applyTo)
                }
                if (!isCurrentCache(cache)) return@launch
                // Retained placeholders still need their summary attempt after reconciliation.
                // Loaded live rows can refresh immediately without consulting an older cache.
                cache.prepared.addAll(batch.filter { it !in existing || it in existingLoaded })
                for (id in batch) {
                    val story = prepared[id]
                    if (story != null) {
                        story.isRead = id in readIds
                        story.isComment = id in commentIds
                        cached[id] = story
                    }
                    if (story == null || !requests.shouldHideStory(story, type)) visible++
                }
            }
            if (!isCurrentCache(cache)) return@launch
            apply(cached)
            prepareApproachingRows(cache)
        }
    }

    private suspend fun <T : Any> readFeedCache(
        ids: List<Int>,
        rebuild: Boolean,
        prepare: (CachedStoryHeader) -> T?,
    ): Map<Int, T> =
        withContext(cacheDispatcher) {
            buildMap {
                for (id in ids) {
                    coroutineContext.ensureActive()
                    loadCachedStoryHeader(id, rebuild)?.let(prepare)?.let { put(id, it) }
                }
            }
        }

    private fun isCurrentCache(cache: FeedCachePreparation): Boolean =
        feedCache === cache && activeStore === cache.store && isCurrentFeed(cache.type, cache.generation)

    private fun continueFeedCachePreparation() {
        val cache = feedCache ?: return
        if (!isCurrentCache(cache) || feedPreparationJob?.isActive == true) return
        feedPreparationJob = scope.launch { prepareApproachingRows(cache) }
    }

    private suspend fun prepareApproachingRows(cache: FeedCachePreparation) {
        while (isCurrentCache(cache)) {
            val viewportEnd = cache.lastVisible?.let {
                StoryPaginationPolicy.scrolledLoadTargetIndex(activeStories.size, it, initialLoadCount())
            } ?: StoryPaginationPolicy.visibleLoadTargetIndex(
                activeStories.size, activeStore.state.value.paginationEnabled,
                activeStore.state.value.visibleStoryCount,
            )
            val end = if (activeStore.state.value.paginationEnabled) {
                max(viewportEnd, activeStore.state.value.visibleStoryCount.coerceAtMost(activeStories.size) - 1)
            } else viewportEnd
            // Recompute between batches so a scroll takes priority over earlier off-screen work.
            val approaching = activeStories.drop(cache.firstVisible)
                .take((end - cache.firstVisible + 1).coerceAtLeast(0))
                .filter { it.id in cache.ids && !it.loaded && it.id !in cache.protected }
            val summaries = approaching.filter { it.id !in cache.prepared }.take(INITIAL_CACHE_ROWS)
            val rebuilding = summaries.isEmpty()
            // Legacy recovery follows failed HTTP, one discussion at a time; online loads never
            // need to parse a full discussion simply to draw a feed row.
            val batch = if (!rebuilding) summaries else approaching.filter {
                it.id in cache.recover && it.id !in cache.recovered
            }.take(1)
            if (batch.isEmpty()) return
            val headers = readFeedCache(batch.map(Story::id), rebuild = rebuilding) { it }
            if (!isCurrentCache(cache)) return
            if (rebuilding) cache.recovered.addAll(batch.map(Story::id))
            else cache.prepared.addAll(batch.map(Story::id))
            var contentChanged = false
            for (story in batch) {
                if (story !in activeStories) continue
                // Keep the feed's row classification, which can differ from older cached JSON.
                val isComment = story.isComment
                // A request or an external update may have won while the worker was preparing.
                if (!story.loaded && story.id !in cache.protected && headers[story.id]?.applyTo(story) == true) {
                    story.isComment = isComment
                    if (requests.shouldHideStory(story, cache.type)) {
                        removeStory(story, loadReplacement = true)
                        continue
                    }
                    if (!rebuilding) requests.invalidateLoadedStoryRows(listOf(story))
                    contentChanged = true
                    prefetch(story)
                }
                if (!rebuilding && activeStories.indexOf(story) <= activeLoadedThrough) {
                    loadStory(story, cache.generation)
                }
            }
            if (contentChanged) changed()
        }
    }

    private fun applyRowLoaded(effect: StoryRequestEvent.StoryRowLoaded) {
        val story = effect.story
        if (!isCurrentRow(story, effect.generation)) return
        activeStore.finishNextPageStory(story.id, effect.generation)
        if (story.isComment && currentType.usesCommentRows()) {
            resolveRootStory(story, effect.generation)
        }
        if (currentType.usesSavedItemFilter() && !matchesSavedFilter(story)) {
            removeStory(story, loadReplacement = true)
            return
        }
        if (requests.shouldHideStory(story, currentType)) {
            removeStory(story, loadReplacement = true)
            return
        }
        prefetch(story)
        changed(story)
    }

    private fun applySearchState(state: StorySearchUiState) {
        if (state.loading && !state.loadingMore) activeLoadedThrough = -1
        val targetStore = when (state.mode) {
            StorySearchMode.QUERY -> searchStore
            StorySearchMode.TOP_STORIES -> mainStore
            StorySearchMode.NONE -> return
        }
        val application = searchRuntime.apply(
            store = targetStore,
            state = state,
            searching = state.mode == StorySearchMode.QUERY,
            activeTypeIsAlgolia = when (state.mode) {
                StorySearchMode.QUERY -> sessionState.searchStoryType.isAlgolia
                StorySearchMode.TOP_STORIES -> sessionState.mainStoryType.isAlgolia
                StorySearchMode.NONE -> false
            },
        )
        if (!application.consumed) return
        if (application.completed) refreshIndicatorShowing = false
        if (application.contentApplied) {
            targetStore.stories.filter(Story::loaded).forEach(::prefetch)
        }
        targetStore.contentChanged()
        emit(StoriesFeatureEffect.StoryChanged())
    }

    private fun loadBookmarks() {
        val stories = savedItems.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true)
            .mapTo(mutableListOf()) { Story("Loading...", it.id, false, false) }
        sessionState.bookmarkStories.clear()
        sessionState.bookmarkStories.addAll(stories)
        replaceActive(filteredSavedStories())
        loadVisibleStories()
        refreshIndicatorShowing = false
        changed()
    }

    private fun loadHistory() {
        replaceActive(
            historyStore.load().map {
                Story("Loading...", it.id, false, false, it.created)
            },
        )
        loadVisibleStories()
        refreshIndicatorShowing = false
        changed()
    }

    private fun loadUserItems(plan: StoryFeedRefreshPlan, generation: Int) {
        val source = currentUserItemSource()
        val cached = savedItems.loadSnapshot(source)
        if (plan.loadCachedUserItems) syncUserItemStories(cached.itemIds, cached.commentIds)
        if (accounts.currentAccount == null) {
            refreshIndicatorShowing = false
            userItemsInitialLoadInProgress = false
            if (activeStories.isEmpty()) activeStore.fail(StoryLoadFailure.GENERAL)
            emit(StoriesFeatureEffect.LoginRequired)
            changed()
            return
        }
        userItemsInitialLoadInProgress = activeStories.isEmpty() && !refreshIndicatorShowing
        requests.syncUserItems(source, generation, nowMillis())
        changed()
    }

    private fun applyUserItems(effect: StoryRequestEvent.UserItemsSynced) {
        if (!isCurrentUserItems(effect.source, effect.generation)) return
        syncUserItemStories(effect.snapshot.itemIds, effect.snapshot.commentIds)
        userItemsInitialLoadInProgress = false
        refreshIndicatorShowing = false
        activeStore.setFailure(null)
        changed()
    }

    private fun applyUserItemsFailure(effect: StoryRequestEvent.UserItemsSyncFailed) {
        if (!isCurrentUserItems(effect.source, effect.generation)) return
        refreshIndicatorShowing = false
        userItemsInitialLoadInProgress = false
        rateLimited = effect.summary.contains("rate", ignoreCase = true) ||
            effect.detail?.contains("429") == true
        if (activeStories.isEmpty()) activeStore.fail(
            if (rateLimited) StoryLoadFailure.RATE_LIMITED else StoryLoadFailure.GENERAL,
        )
        emit(StoriesFeatureEffect.UserMessage(effect.summary))
        changed()
    }

    private fun syncUserItemStories(itemIds: List<Int>, commentIds: Set<Int>) {
        val source = sessionState.userItemListStories.ifEmpty { activeStories }
        val result = SavedItemStoryReconciler.reconcile(
            currentStories = source,
            currentCommentIds = sessionState.userItemListCommentIds,
            itemIds = itemIds,
            commentIds = commentIds,
        )
        if (!result.changed) return
        requests.clearStoryRowLoads()
        sessionState.userItemListStories.clear()
        sessionState.userItemListStories.addAll(result.stories)
        sessionState.userItemListCommentIds.clear()
        sessionState.userItemListCommentIds.addAll(commentIds)
        replaceActive(filteredSavedStories())
        loadVisibleStories()
        changed()
    }

    private fun applySavedFilter() {
        replaceActive(filteredSavedStories())
        loadVisibleStories()
        changed()
    }

    private fun filteredSavedStories(): List<Story> {
        val source = if (currentType.isBookmarks) {
            sessionState.bookmarkStories
        } else {
            sessionState.userItemListStories
        }
        return activeStore.filteredSavedItems(
            source = source,
            filter = savedFilter,
            keepUnloadedItems = currentType.isBookmarks,
        )
    }

    private fun matchesSavedFilter(story: Story): Boolean = activeStore.filteredSavedItems(
        source = listOf(story),
        filter = savedFilter,
        keepUnloadedItems = currentType.isBookmarks,
    ).isNotEmpty()

    private fun replaceActive(stories: List<Story>) {
        activeLoadedThrough = -1
        activeStore.clearPendingPage()
        activeStore.setPaginationEnabled(shouldUsePagination(currentType))
        activeStore.replace(stories, showingCached = activeStore.state.value.showingCached)
    }

    private fun clearStore(store: StoryListStore, type: StoryType) {
        visibleRanges.remove(store)
        store.clear()
        store.setPaginationEnabled(shouldUsePagination(type))
        store.setVisibleStoryCount(initialVisibleCount(store))
        store.setCanLoadMore(false)
        if (store === activeStore) activeLoadedThrough = -1
    }

    private fun loadThrough(targetIndex: Int, generation: Int) {
        if (!requests.isCurrentStoryLoadGeneration(generation) || targetIndex < 0) return
        var index = max(activeLoadedThrough + 1, feedCache?.firstVisible ?: 0)
        while (index <= targetIndex && index < activeStories.size) {
            activeLoadedThrough = index
            loadStory(activeStories[index], generation)
            index++
        }
    }

    private fun retryUnsettledThrough(targetIndex: Int, generation: Int) {
        if (!requests.isCurrentStoryLoadGeneration(generation) || targetIndex < 0) return
        val capped = min(targetIndex, activeStories.lastIndex)
        if (capped < 0) return
        for (index in (feedCache?.firstVisible ?: 0)..capped) {
            val story = activeStories[index]
            if (((!story.loaded && !story.loadingFailed) || requests.storyRowNeedsRefresh(story.id)) &&
                !requests.isStoryRowLoadInProgress(story.id)
            ) loadStory(story, generation)
        }
    }

    private fun loadStory(story: Story, generation: Int) {
        if (!requests.isCurrentStoryLoadGeneration(generation)) return
        if (story.loaded && !requests.storyRowNeedsRefresh(story.id)) {
            if (requests.shouldHideStory(story, currentType)) removeStory(story)
            else prefetch(story)
            return
        }
        if (requests.isStoryRowLoadInProgress(story.id)) return
        // Each new row gets one cheap summary attempt before its request. Rows outside the
        // viewport buffer remain placeholders until approached, including after a large jump.
        feedCache?.let { cache ->
            if (isCurrentCache(cache) && story.id in cache.ids &&
                story.id !in cache.prepared && story.id !in cache.protected
            ) return
        }
        requests.loadStoryRow(
                story = story,
                preserveTime = currentType.isHistory,
                generation = generation,
            )
    }

    private fun removeStory(story: Story, loadReplacement: Boolean = false) {
        val index = activeStories.indexOf(story)
        if (index < 0) return
        val removed = activeStore.removeAt(index) ?: return
        activeStore.finishNextPageStory(removed.id, requests.storyLoadGeneration)
        requests.cancelStoryRowLoad(removed.id)
        if (index <= activeLoadedThrough) activeLoadedThrough = max(-1, activeLoadedThrough - 1)
        if (loadReplacement) loadVisibleStories()
        changed()
    }

    private fun resolveRootStory(story: Story, generation: Int) {
        val parentId = story.parentId
        if (parentId <= 0 || (story.rootStoryId > 0 && !story.rootStoryTitle.isNullOrEmpty())) {
            return
        }
        scope.launch {
            try {
                rootStoryResolver.resolveParentChain(story, parentId) ?: return@launch
                if (isCurrentRow(story, generation)) changed(story)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Parent metadata is an enhancement; the comment row remains usable without it.
            }
        }
    }

    private fun retainSearchOptions() {
        sessionState.searchOptions = searchOptions.state.value.options
    }

    private fun retrySearch() {
        val hasSubmittedQuery = searchOptions.state.value.mode == StorySearchMode.QUERY
        if (searching && (sessionState.lastSearch.isNotBlank() || hasSubmittedQuery)) {
            submitSearch(sessionState.lastSearch)
        }
    }

    private fun resumeInterruptedLoads() {
        if (currentType.isAlgolia || activeStories.isEmpty() || activeLoadedThrough < 0) return
        val last = min(activeLoadedThrough, activeStories.lastIndex)
        for (index in 0..last) {
            val story = activeStories[index]
            if (!story.loaded && !story.loadingFailed) loadStory(story, requests.storyLoadGeneration)
        }
    }

    private fun updatePaginationModes() {
        mainStore.setPaginationEnabled(shouldUsePagination(sessionState.mainStoryType))
        searchStore.setPaginationEnabled(shouldUsePagination(sessionState.searchStoryType))
    }

    private fun shouldUsePagination(type: StoryType): Boolean =
        StoryPaginationPolicy.isEnabled(paginationMode, type)

    private fun initialVisibleCount(store: StoryListStore): Int =
        StoryPaginationPolicy.initialVisibleCount(store.state.value.paginationEnabled)

    private fun initialLoadCount(): Int = if (activeStore.state.value.paginationEnabled) {
        StoryPaginationPolicy.DEFAULT_PAGE_SIZE
    } else {
        StoryPaginationPolicy.DEFAULT_INITIAL_LOAD_COUNT
    }

    private fun cancelFeedLoads() {
        feedLoadJob?.cancel()
        nextScrapedPageJob?.cancel()
        feedPreparationJob?.cancel()
        feedCache = null
    }

    private fun beginGeneration(): Int {
        cancelFeedLoads()
        val generation = requests.beginStoryLoadGeneration()
        activeStore.clearPendingPage()
        feedRuntime.resetScrapedPagination(activeStore)
        searchOptions.cancel(clearResults = false)
        searchRuntime.cancel(activeStore)
        activeLoadedThrough = -1
        return generation
    }

    private fun isCurrentFeed(type: StoryType, generation: Int): Boolean =
        currentType == type && requests.isCurrentStoryLoadGeneration(generation)

    private fun isCurrentRow(story: Story, generation: Int): Boolean =
        requests.isCurrentStoryLoadGeneration(generation) && activeStories.contains(story)

    private fun isCurrentUserItems(source: SavedItemSource, generation: Int): Boolean =
        requests.isCurrentStoryLoadGeneration(generation) && currentUserItemSource() == source

    private fun isCurrentActionContext(generation: Int, store: StoryListStore): Boolean =
        requests.isCurrentStoryLoadGeneration(generation) && activeStore === store

    private fun currentUserItemSource(): SavedItemSource =
        if (currentType.isUpvoted) SavedItemSource.UPVOTED else SavedItemSource.FAVORITES

    private fun updateStoryReadState(story: Story, read: Boolean) {
        // Resolved parent stories may not be in either list yet.
        story.isRead = read
        mainStore.markRead(story.id, read)
        searchStore.markRead(story.id, read)
    }

    private fun markRead(story: Story) {
        if (!searchOptions.state.value.options.onlyRead) updateStoryReadState(story, true)
        scope.launch { historyStore.recordHistory(story.id, nowMillis()) }
    }

    private fun canSelect(): Boolean {
        val now = nowMillis()
        if (now - lastSelectionMillis <= SELECTION_INTERVAL_MILLIS) return false
        lastSelectionMillis = now
        return true
    }

    private fun prefetch(story: Story) = storyResources?.prefetchStory(story, activeStories)

    private fun changed(story: Story? = null) {
        when {
            story == null -> activeStore.contentChanged()
            mainStories.contains(story) -> mainStore.contentChanged(story)
            searchStories.contains(story) -> searchStore.contentChanged(story)
        }
        emit(StoriesFeatureEffect.StoryChanged(story?.id))
    }

    private fun emit(effect: StoriesFeatureEffect) {
        mutableEffects.tryEmit(effect)
    }

    private fun store(target: StoryListTarget): StoryListStore =
        if (target == StoryListTarget.MAIN) mainStore else searchStore

    private companion object {
        const val FILTER_STORIES = 0
        const val FILTER_BOTH = 1
        const val FILTER_COMMENTS = 2
        const val SELECTION_INTERVAL_MILLIS = 500L
    }
}
