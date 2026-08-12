package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.platform.HistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Platform operations that remain after the shared stories feature has made a decision. */
sealed interface StoriesRuntimeEffect {
    data class OpenComments(
        val story: Story,
        val position: Int,
        val showWebsite: Boolean,
    ) : StoriesRuntimeEffect

    data class OpenExternalLink(val url: String) : StoriesRuntimeEffect
    data class StoryChanged(val story: Story? = null) : StoriesRuntimeEffect
    data class PrefetchStoryResources(val story: Story) : StoriesRuntimeEffect
    data object LoginRequired : StoriesRuntimeEffect
    data class UserMessage(val message: String) : StoriesRuntimeEffect
    data class SavedActionFailed(
        val outcome: SavedItemActionOutcome.Failure,
    ) : StoriesRuntimeEffect
}

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
    val presenter: StoriesPresenter,
    private val savedItems: SavedItemsRepository,
    val savedItemActions: SavedItemActionUseCase,
    private val historyStore: HistoryStore,
    private val commentMasterResolver: CommentMasterResolver,
    private val nowMillis: () -> Long,
    private val hydrateCachedStory: (Story) -> Boolean,
    private val shouldFilterStory: (Story, StoryType) -> Boolean,
    private val hasAccountDetails: () -> Boolean,
) {
    private val mutableEffects = MutableSharedFlow<StoriesRuntimeEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<StoriesRuntimeEffect> = mutableEffects.asSharedFlow()

    val mainStore: StoryListStore = presenter.mainStoryList
    val searchStore: StoryListStore = presenter.searchStoryList
    val mainStories: MutableList<Story> = mainStore.stories
    val searchStories: MutableList<Story> = searchStore.stories
    val searchOptions: StorySearchStore = presenter.searchStore
    val frontPageDay = FrontPageDayState(
        restoredMillis = sessionState.frontPageDayUtcMillis,
        nowMillis = nowMillis(),
    )

    private val feedRuntime = StoryFeedRuntime(
        sessionState = sessionState,
        clickedStoryIds = { historyStore.load().mapTo(mutableSetOf()) { it.id } },
        shouldHideClickedStories = { hideClicked },
        hydrateCachedStory = hydrateCachedStory,
        shouldHideHydratedStory = { shouldFilterStory(it, currentType) },
    )
    private val searchRuntime = StorySearchRuntime()

    private var paginationMode = false
    private var hideClicked = false
    private var alwaysOpenComments = false
    private var useIntegratedWebView = false
    private var activeLoadedThrough = -1
    private var storiesBeforeSearch = false
    private var loadPendingBeforeSearch = false
    private var userItemsInitialLoadInProgress = false
    private var rateLimited = false
    private var lastSelectionMillis = 0L

    var refreshIndicatorShowing: Boolean = false
        private set

    val activeStore: StoryListStore
        get() = if (searching) searchStore else mainStore

    val activeStories: MutableList<Story>
        get() = activeStore.stories

    val currentType: StoryType
        get() = presenter.state.value.activeStoryType

    val searching: Boolean
        get() = presenter.state.value.searching

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

    init {
        scope.launch { presenter.effects.collect(::applyPresenterEffect) }
        scope.launch { presenter.searchStore.state.collect(::applySearchState) }
    }

    fun configure(
        pagination: Boolean,
        hideClicked: Boolean,
        alwaysOpenComments: Boolean,
        useIntegratedWebView: Boolean,
    ) {
        val paginationChanged = paginationMode != pagination
        paginationMode = pagination
        this.hideClicked = hideClicked
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

    fun initialize(preferredType: StoryType, restoring: Boolean) {
        if (!sessionState.initialized) {
            selectType(StoryListTarget.MAIN, preferredType)
            selectType(StoryListTarget.SEARCH, preferredType)
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

    fun selectType(target: StoryListTarget, type: StoryType) {
        presenter.dispatch(StoriesAction.SelectStoryType(type, target))
        store(target).setPaginationEnabled(shouldUsePagination(type))
    }

    fun selectTypeAndRefresh(type: StoryType) {
        selectType(StoryListTarget.MAIN, type)
        refresh(showSwipeRefreshIndicator = false, showMainLoadingIndicator = true)
    }

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
        presenter.dispatch(StoriesAction.ResetSearchOptions)
        presenter.dispatch(StoriesAction.SetSearching(true))
        selectType(StoryListTarget.SEARCH, presenter.state.value.mainStoryType)
        clearStore(searchStore, presenter.state.value.searchStoryType)
        refreshIndicatorShowing = false
        rateLimited = false
        changed()
    }

    /** Returns true when retained main content was restored and no refresh was needed. */
    fun closeSearch(): Boolean {
        if (!searching) return false
        presenter.dispatch(StoriesAction.SetSearchDraft(""))
        presenter.dispatch(StoriesAction.ResetSearchOptions)
        beginGeneration()
        presenter.dispatch(StoriesAction.SetSearching(false))
        clearStore(searchStore, presenter.state.value.searchStoryType)
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

    fun setSearchDraft(query: String) =
        presenter.dispatch(StoriesAction.SetSearchDraft(query))

    fun submitSearch(query: String, resetResultLimit: Boolean = true) {
        presenter.dispatch(StoriesAction.Search(query, resetResultLimit))
    }

    fun resetSearchOptions() = presenter.dispatch(StoriesAction.ResetSearchOptions)

    fun selectSearchOption(option: StorySearchOption, index: Int) {
        presenter.dispatch(
            when (option) {
                StorySearchOption.SORT -> StoriesAction.SelectSearchSort(index)
                StorySearchOption.DATE -> StoriesAction.SelectSearchDateRange(index)
                StorySearchOption.POINTS -> StoriesAction.SelectSearchMinimumPoints(index)
                StorySearchOption.COMMENTS -> StoriesAction.SelectSearchMinimumComments(index)
            },
        )
        retrySearch()
    }

    fun toggleOnlyClicked() {
        presenter.dispatch(StoriesAction.ToggleOnlyClicked)
        retrySearch()
    }

    fun refresh(
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean = false,
    ) {
        presenter.dispatch(StoriesAction.DismissUpdateAvailability)
        val type = currentType
        val plan = StoryFeedRefreshPolicy.plan(
            searching = searching,
            storyType = type,
            showSwipeRefreshIndicator = showSwipeRefreshIndicator,
            showMainLoadingIndicator = showMainLoadingIndicator,
            listIsEmpty = activeStories.isEmpty(),
        )
        if (plan.source == StoryFeedSource.SEARCH) {
            submitSearch(presenter.state.value.searchDraft)
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
            StoryFeedSource.ALGOLIA -> presenter.dispatch(
                StoriesAction.LoadTopStories(
                    storyType = type,
                    startTime = searchOptions.getTopStoriesStartTime(type),
                ),
            )
            StoryFeedSource.BOOKMARKS -> loadBookmarks()
            StoryFeedSource.USER_ITEMS -> loadUserItems(plan, generation)
            StoryFeedSource.HISTORY -> loadHistory()
            StoryFeedSource.FRONTPAGE_LINKS,
            StoryFeedSource.SCRAPED_FRONTPAGE,
            StoryFeedSource.HACKER_NEWS_API,
            -> presenter.dispatch(
                StoriesAction.LoadFeed(
                    type,
                    frontPageDay.requestParameter.takeIf { type.isFront },
                    generation,
                ),
            )
        }
        changed()
    }

    fun loadMore() {
        val state = activeStore.state.value
        when {
            state.paginationEnabled && state.visibleStoryCount < activeStories.size -> {
                val generation = presenter.storyLoadGeneration
                val plan = activeStore.beginNextPage(generation) ?: return
                if (!activeStore.hasPendingPageStories()) activeStore.clearPendingPage()
                loadThrough(plan.targetLoadedIndex, generation)
                retryUnsettledThrough(plan.targetLoadedIndex, generation)
            }
            state.canLoadMore && currentType.isScrapedFrontpage -> {
                val next = feedRuntime.beginNextScrapedPage(activeStore, currentType) ?: return
                presenter.dispatch(
                    StoriesAction.LoadNextScrapedPage(
                        currentType,
                        next,
                        presenter.storyLoadGeneration,
                    ),
                )
            }
            state.canLoadMore && !searchOptions.state.value.loading -> {
                searchRuntime.beginLoadMore(activeStore)
                presenter.dispatch(StoriesAction.LoadMoreSearchResults)
            }
        }
        changed()
    }

    fun loadVisibleStories(lastVisibleIndex: Int? = null) {
        val target = lastVisibleIndex?.let {
            min(
                max(it + VISIBLE_LOAD_AHEAD, initialLoadCount()),
                activeStories.lastIndex,
            )
        } ?: StoryPaginationPolicy.visibleLoadTargetIndex(
            storyCount = activeStories.size,
            paginationEnabled = activeStore.state.value.paginationEnabled,
            visibleStoryCount = activeStore.state.value.visibleStoryCount,
        )
        val generation = presenter.storyLoadGeneration
        loadThrough(target, generation)
        retryUnsettledThrough(target, generation)
    }

    fun selectStoryLink(story: Story) {
        val position = activeStories.indexOf(story)
        if (position < 0 || !canSelect()) return
        presenter.dispatch(
            StoriesAction.SelectStoryLink(
                story,
                position,
                alwaysOpenComments,
                useIntegratedWebView,
            ),
        )
    }

    fun selectStoryComments(story: Story) {
        val position = activeStories.indexOf(story)
        if (position >= 0 && canSelect()) {
            presenter.dispatch(StoriesAction.SelectStoryComments(story, position))
        }
    }

    fun selectCommentStory(story: Story) {
        val position = activeStories.indexOf(story)
        if (position < 0) return
        val master = story.toCommentMasterStory()
        if (master == null) {
            selectStoryComments(story)
            return
        }
        if (master.loaded) {
            emit(StoriesRuntimeEffect.OpenComments(master, position, false))
            return
        }
        scope.launch {
            val resolved = try {
                commentMasterResolver.resolve(story)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                master
            }
            if (activeStories.contains(story)) {
                changed(story)
                emit(StoriesRuntimeEffect.OpenComments(resolved, position, false))
            }
        }
    }

    fun openComments(story: Story, position: Int, showWebsite: Boolean) {
        markClicked(story)
        changed(story)
        emit(StoriesRuntimeEffect.OpenComments(story, position, showWebsite))
    }

    fun toggleRead(story: Story) {
        story.clicked = !story.clicked
        if (story.clicked) historyStore.record(story.id, nowMillis()) else historyStore.remove(story.id)
        changed(story)
    }

    fun toggleBookmark(story: Story) {
        val bookmarked = savedItemActions.toggleBookmark(story.id)
        if (!bookmarked && currentType.isBookmarks) {
            sessionState.bookmarkStories.remove(story)
            removeStory(story, loadReplacement = true)
        } else {
            changed(story)
        }
    }

    fun toggleVote(story: Story, completed: () -> Unit) {
        val generation = presenter.storyLoadGeneration
        val expectedStore = activeStore
        val action = savedItemActions.beginVote(
            itemId = story.id,
            isComment = false,
            direction = if (savedItemActions.isUpvoted(story.id, false)) "un" else "up",
        )
        changed(story)
        scope.launch {
            when (val outcome = savedItemActions.execute(action)) {
                is SavedItemActionOutcome.Success -> Unit
                is SavedItemActionOutcome.Failure -> {
                    if (isCurrentActionContext(generation, expectedStore)) changed(story)
                    emit(StoriesRuntimeEffect.SavedActionFailed(outcome))
                }
            }
            completed()
        }
    }

    fun toggleFavorite(story: Story, completed: () -> Unit) {
        val generation = presenter.storyLoadGeneration
        val expectedStore = activeStore
        val favoritesList = currentType.isFavorites
        val action = savedItemActions.beginFavorite(story.id)
        val wasFavorited = action.previousPresent
        val optimisticIndex = activeStories.indexOf(story)
        if (wasFavorited && favoritesList && optimisticIndex >= 0) {
            removeStory(story, loadReplacement = true)
        } else {
            changed(story)
        }
        scope.launch {
            when (val outcome = savedItemActions.execute(action)) {
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
                    emit(StoriesRuntimeEffect.SavedActionFailed(outcome))
                }
            }
            completed()
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

    fun refreshBookmarks() {
        if (currentType.isBookmarks && !searching) refresh(false)
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

    fun clearActiveStories() {
        beginGeneration()
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
        searchQuery = presenter.state.value.searchDraft,
        storyType = currentType,
    )

    fun resumeRetainedLoads() = resumeInterruptedLoads()

    fun syncHistoryIfChanged(previousVersion: Long): StoryHistorySyncResult {
        if (historyStore.changeVersion == previousVersion) return StoryHistorySyncResult.UNCHANGED
        val result = activeStore.syncHistory(
            clickedStoryIds = historyStore.load().mapTo(mutableSetOf()) { it.id },
            searchingOnlyClicked = searching && searchOptions.state.value.options.onlyClicked,
            showingHistory = currentType.isHistory,
            hideClicked = hideClicked,
        )
        if (result == StoryHistorySyncResult.ITEMS_REMOVED) loadVisibleStories()
        if (result != StoryHistorySyncResult.UNCHANGED) changed()
        return result
    }

    fun publishStoryChanged(story: Story? = null) = changed(story)

    fun dispose() {
        presenter.dispatch(StoriesAction.CancelFeedLoads)
        presenter.clearStoryRowLoads()
    }

    private fun applyPresenterEffect(effect: StoriesEffect) {
        when (effect) {
            is StoriesEffect.OpenComments -> {
                markClicked(effect.story)
                changed(effect.story)
                emit(StoriesRuntimeEffect.OpenComments(effect.story, effect.position, effect.showWebsite))
            }
            is StoriesEffect.OpenExternalStory -> {
                if (effect.story.isFrontpageLink) effect.story.clicked = true else markClicked(effect.story)
                changed(effect.story)
                emit(StoriesRuntimeEffect.OpenExternalLink(effect.url))
            }
            is StoriesEffect.RetryStory -> {
                effect.story.loadingFailed = false
                loadStory(effect.story, presenter.storyLoadGeneration)
                changed(effect.story)
            }
            is StoriesEffect.FeedLoaded -> applyFeedLoaded(effect)
            is StoriesEffect.FeedFailed -> applyFeedFailed(effect)
            is StoriesEffect.NextScrapedPageLoaded -> applyNextScrapedPage(effect)
            is StoriesEffect.NextScrapedPageFailed -> {
                if (isCurrentFeed(effect.storyType, effect.generation)) {
                    feedRuntime.failNextScrapedPage(activeStore, effect.storyType)
                    changed()
                }
            }
            is StoriesEffect.StoryRowLoaded -> applyRowLoaded(effect)
            is StoriesEffect.StoryRowRejected -> if (isCurrentRow(effect.story, effect.generation)) {
                removeStory(effect.story)
            }
            is StoriesEffect.StoryRowLoadAttemptFailed -> if (
                isCurrentRow(effect.story, effect.generation)
            ) {
                if (effect.finalAttempt) activeStore.finishNextPageStory(
                    effect.story.id,
                    effect.generation,
                )
                changed(effect.story)
            }
            is StoriesEffect.UserItemsSynced -> applyUserItems(effect)
            is StoriesEffect.UserItemsSyncFailed -> applyUserItemsFailure(effect)
        }
    }

    private fun applyFeedLoaded(effect: StoriesEffect.FeedLoaded) {
        if (!isCurrentFeed(effect.storyType, effect.generation)) return
        refreshIndicatorShowing = false
        rateLimited = false
        val result = feedRuntime.applyInitial(activeStore, effect.storyType, effect.result)
        if (result.loadVisibleStories) loadVisibleStories()
        result.loadedStories.filter(Story::loaded).forEach(::prefetch)
        changed()
    }

    private fun applyFeedFailed(effect: StoriesEffect.FeedFailed) {
        if (!isCurrentFeed(effect.storyType, effect.generation)) return
        refreshIndicatorShowing = false
        val mapped = StoryFeedRefreshPolicy.failureFor(effect.cause)
        rateLimited = mapped == StoryLoadFailure.RATE_LIMITED
        activeStore.fail(mapped)
        changed()
    }

    private fun applyNextScrapedPage(effect: StoriesEffect.NextScrapedPageLoaded) {
        if (!isCurrentFeed(effect.storyType, effect.generation)) return
        val application = feedRuntime.applyNextScrapedPage(activeStore, effect.storyType, effect.page)
        if (application.loadVisibleStories) loadVisibleStories()
        application.loadedStories.filter(Story::loaded).forEach(::prefetch)
        changed()
    }

    private fun applyRowLoaded(effect: StoriesEffect.StoryRowLoaded) {
        val story = effect.story
        if (!isCurrentRow(story, effect.generation)) return
        activeStore.finishNextPageStory(story.id, effect.generation)
        if (story.isComment && currentType.usesCommentRows()) {
            resolveCommentMaster(story, effect.generation)
        }
        if (currentType.usesSavedItemFilter() && !matchesSavedFilter(story)) {
            removeStory(story, loadReplacement = true)
            return
        }
        if (shouldFilterStory(story, currentType)) {
            removeStory(story)
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
                StorySearchMode.QUERY -> presenter.state.value.searchStoryType.isAlgolia
                StorySearchMode.TOP_STORIES -> presenter.state.value.mainStoryType.isAlgolia
                StorySearchMode.NONE -> false
            },
        )
        if (!application.consumed) return
        if (application.completed) refreshIndicatorShowing = false
        if (application.contentApplied) {
            targetStore.stories.filter(Story::loaded).forEach(::prefetch)
        }
        targetStore.contentChanged()
        emit(StoriesRuntimeEffect.StoryChanged())
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
        if (!hasAccountDetails()) {
            refreshIndicatorShowing = false
            userItemsInitialLoadInProgress = false
            if (activeStories.isEmpty()) activeStore.fail(StoryLoadFailure.GENERAL)
            emit(StoriesRuntimeEffect.LoginRequired)
            changed()
            return
        }
        userItemsInitialLoadInProgress = activeStories.isEmpty() && !refreshIndicatorShowing
        presenter.dispatch(StoriesAction.SyncUserItems(source, generation, nowMillis()))
        changed()
    }

    private fun applyUserItems(effect: StoriesEffect.UserItemsSynced) {
        if (!isCurrentUserItems(effect.source, effect.generation)) return
        syncUserItemStories(effect.snapshot.itemIds, effect.snapshot.commentIds)
        userItemsInitialLoadInProgress = false
        refreshIndicatorShowing = false
        activeStore.setFailure(null)
        changed()
    }

    private fun applyUserItemsFailure(effect: StoriesEffect.UserItemsSyncFailed) {
        if (!isCurrentUserItems(effect.source, effect.generation)) return
        refreshIndicatorShowing = false
        userItemsInitialLoadInProgress = false
        rateLimited = effect.summary.contains("rate", ignoreCase = true) ||
            effect.detail?.contains("429") == true
        if (activeStories.isEmpty()) activeStore.fail(
            if (rateLimited) StoryLoadFailure.RATE_LIMITED else StoryLoadFailure.GENERAL,
        )
        emit(StoriesRuntimeEffect.UserMessage(effect.summary))
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
        presenter.clearStoryRowLoads()
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
        store.clear()
        store.setPaginationEnabled(shouldUsePagination(type))
        store.setVisibleStoryCount(initialVisibleCount(store))
        store.setCanLoadMore(false)
        if (store === activeStore) activeLoadedThrough = -1
    }

    private fun loadThrough(targetIndex: Int, generation: Int) {
        if (!presenter.isCurrentStoryLoadGeneration(generation) || targetIndex < 0) return
        var index = activeLoadedThrough + 1
        while (index <= targetIndex && index < activeStories.size) {
            activeLoadedThrough = index
            loadStory(activeStories[index], generation)
            index++
        }
    }

    private fun retryUnsettledThrough(targetIndex: Int, generation: Int) {
        if (!presenter.isCurrentStoryLoadGeneration(generation) || targetIndex < 0) return
        val capped = min(targetIndex, activeStories.lastIndex)
        if (capped < 0) return
        for (index in 0..capped) {
            val story = activeStories[index]
            if (!story.loaded && !story.loadingFailed &&
                !presenter.isStoryRowLoadInProgress(story.id)
            ) loadStory(story, generation)
        }
    }

    private fun loadStory(story: Story, generation: Int) {
        if (!presenter.isCurrentStoryLoadGeneration(generation)) return
        if (story.loaded) {
            if (shouldFilterStory(story, currentType)) removeStory(story)
            else prefetch(story)
            return
        }
        if (presenter.isStoryRowLoadInProgress(story.id)) return
        presenter.dispatch(
            StoriesAction.LoadStoryRow(
                story = story,
                preserveTime = currentType.isHistory,
                generation = generation,
            ),
        )
    }

    private fun removeStory(story: Story, loadReplacement: Boolean = false) {
        val index = activeStories.indexOf(story)
        if (index < 0) return
        val removed = activeStore.removeAt(index) ?: return
        activeStore.finishNextPageStory(removed.id, presenter.storyLoadGeneration)
        presenter.cancelStoryRowLoad(removed.id)
        if (index <= activeLoadedThrough) activeLoadedThrough = max(-1, activeLoadedThrough - 1)
        if (loadReplacement) loadVisibleStories()
        changed()
    }

    private fun resolveCommentMaster(story: Story, generation: Int) {
        val parentId = story.parentId
        if (parentId <= 0 || (story.commentMasterId > 0 && !story.commentMasterTitle.isNullOrEmpty())) {
            return
        }
        scope.launch {
            try {
                commentMasterResolver.resolveParentChain(story, parentId) ?: return@launch
                if (isCurrentRow(story, generation)) changed(story)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Parent metadata is an enhancement; the comment row remains usable without it.
            }
        }
    }

    private fun retrySearch() {
        if (searching && presenter.state.value.searchDraft.isNotBlank()) {
            submitSearch(presenter.state.value.searchDraft)
        }
    }

    private fun resumeInterruptedLoads() {
        if (currentType.isAlgolia || activeStories.isEmpty() || activeLoadedThrough < 0) return
        val last = min(activeLoadedThrough, activeStories.lastIndex)
        for (index in 0..last) {
            val story = activeStories[index]
            if (!story.loaded && !story.loadingFailed) loadStory(story, presenter.storyLoadGeneration)
        }
    }

    private fun updatePaginationModes() {
        mainStore.setPaginationEnabled(shouldUsePagination(presenter.state.value.mainStoryType))
        searchStore.setPaginationEnabled(shouldUsePagination(presenter.state.value.searchStoryType))
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

    private fun beginGeneration(): Int {
        presenter.dispatch(StoriesAction.CancelFeedLoads)
        val generation = presenter.beginStoryLoadGeneration()
        activeStore.clearPendingPage()
        feedRuntime.resetScrapedPagination(activeStore)
        searchOptions.cancel(clearResults = false)
        searchRuntime.cancel(activeStore)
        activeLoadedThrough = -1
        return generation
    }

    private fun isCurrentFeed(type: StoryType, generation: Int): Boolean =
        currentType == type && presenter.isCurrentStoryLoadGeneration(generation)

    private fun isCurrentRow(story: Story, generation: Int): Boolean =
        presenter.isCurrentStoryLoadGeneration(generation) && activeStories.contains(story)

    private fun isCurrentUserItems(source: SavedItemSource, generation: Int): Boolean =
        presenter.isCurrentStoryLoadGeneration(generation) && currentUserItemSource() == source

    private fun isCurrentActionContext(generation: Int, store: StoryListStore): Boolean =
        presenter.isCurrentStoryLoadGeneration(generation) && activeStore === store

    private fun currentUserItemSource(): SavedItemSource =
        if (currentType.isUpvoted) SavedItemSource.UPVOTED else SavedItemSource.FAVORITES

    private fun markClicked(story: Story) {
        if (!searchOptions.state.value.options.onlyClicked) story.clicked = true
        historyStore.record(story.id, nowMillis())
    }

    private fun canSelect(): Boolean {
        val now = nowMillis()
        if (now - lastSelectionMillis <= SELECTION_INTERVAL_MILLIS) return false
        lastSelectionMillis = now
        return true
    }

    private fun prefetch(story: Story) =
        emit(StoriesRuntimeEffect.PrefetchStoryResources(story))

    private fun changed(story: Story? = null) {
        when {
            story == null -> activeStore.contentChanged()
            mainStories.contains(story) -> mainStore.contentChanged()
            searchStories.contains(story) -> searchStore.contentChanged()
        }
        emit(StoriesRuntimeEffect.StoryChanged(story))
    }

    private fun emit(effect: StoriesRuntimeEffect) {
        mutableEffects.tryEmit(effect)
    }

    private fun store(target: StoryListTarget): StoryListStore =
        if (target == StoryListTarget.MAIN) mainStore else searchStore

    private companion object {
        const val FILTER_STORIES = 0
        const val FILTER_BOTH = 1
        const val FILTER_COMMENTS = 2
        const val VISIBLE_LOAD_AHEAD = 10
        const val SELECTION_INTERVAL_MILLIS = 500L
    }
}
