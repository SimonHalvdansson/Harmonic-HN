package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemSnapshots
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsUserItemsLoader
import com.simon.harmonichackernews.network.HackerNewsUserItemsResult
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.StoryFeedLoader
import com.simon.harmonichackernews.network.StoryFeedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StoriesPresenterState(
    val searching: Boolean = false,
    val searchDraft: String = "",
    val updateAvailable: Boolean = false,
    val mainStoryType: StoryType = StoryType.TOP_STORIES,
    val searchStoryType: StoryType = StoryType.TOP_STORIES,
    val mainList: StoryListUiState = StoryListUiState(),
    val searchList: StoryListUiState = StoryListUiState(),
    val search: StorySearchUiState = StorySearchUiState(),
) {
    val activeList: StoryListUiState get() = if (searching) searchList else mainList
    val activeStoryType: StoryType get() = if (searching) searchStoryType else mainStoryType
}

enum class StoryListTarget { MAIN, SEARCH }

sealed interface StoriesAction {
    data class SetSearching(val searching: Boolean) : StoriesAction
    data class SetSearchDraft(val query: String) : StoriesAction
    data class SelectStoryType(
        val type: StoryType,
        val target: StoryListTarget,
    ) : StoriesAction
    data class EvaluateUpdateAvailability(
        val nowMillis: Long,
        val lastLoadedMillis: Long,
        val alwaysShow: Boolean,
        val storyType: StoryType,
    ) : StoriesAction
    data object DismissUpdateAvailability : StoriesAction
    data class Search(val query: String, val resetResultLimit: Boolean = true) : StoriesAction
    data class LoadTopStories(
        val storyType: StoryType,
        val startTime: Int,
        val resetResultLimit: Boolean = true,
    ) : StoriesAction
    data object LoadMoreSearchResults : StoriesAction
    data object RetrySearch : StoriesAction
    data object ResetSearchOptions : StoriesAction
    data class SelectSearchSort(val index: Int) : StoriesAction
    data class SelectSearchDateRange(val index: Int) : StoriesAction
    data class SelectSearchMinimumPoints(val index: Int) : StoriesAction
    data class SelectSearchMinimumComments(val index: Int) : StoriesAction
    data object ToggleOnlyClicked : StoriesAction
    data class SelectStoryLink(
        val story: Story,
        val position: Int,
        val alwaysOpenComments: Boolean,
        val useIntegratedWebView: Boolean,
    ) : StoriesAction
    data class SelectStoryComments(val story: Story, val position: Int) : StoriesAction
    data class LoadFeed(
        val storyType: StoryType,
        val frontDay: String?,
        val generation: Int,
    ) : StoriesAction
    data class LoadNextScrapedPage(
        val storyType: StoryType,
        val nextPageUrl: String,
        val generation: Int,
    ) : StoriesAction
    data object CancelFeedLoads : StoriesAction
    data class LoadStoryRow(
        val story: Story,
        val preserveTime: Boolean,
        val generation: Int,
    ) : StoriesAction
    data class SyncUserItems(
        val source: SavedItemSource,
        val generation: Int,
        val savedAtMillis: Long,
    ) : StoriesAction
}

sealed interface StoriesEffect {
    data class OpenComments(
        val story: Story,
        val position: Int,
        val showWebsite: Boolean,
    ) : StoriesEffect

    data class OpenExternalStory(
        val story: Story,
        val position: Int,
        val url: String,
    ) : StoriesEffect

    data class RetryStory(val story: Story, val position: Int) : StoriesEffect
    data class FeedLoaded(
        val storyType: StoryType,
        val generation: Int,
        val result: StoryFeedResult,
    ) : StoriesEffect
    data class FeedFailed(
        val storyType: StoryType,
        val generation: Int,
        val cause: Throwable,
    ) : StoriesEffect
    data class NextScrapedPageLoaded(
        val storyType: StoryType,
        val generation: Int,
        val page: HackerNewsListPage,
    ) : StoriesEffect
    data class NextScrapedPageFailed(
        val storyType: StoryType,
        val generation: Int,
        val cause: Throwable,
    ) : StoriesEffect
    data class StoryRowLoaded(
        val story: Story,
        val generation: Int,
    ) : StoriesEffect
    data class StoryRowRejected(
        val story: Story,
        val generation: Int,
    ) : StoriesEffect
    data class StoryRowLoadAttemptFailed(
        val story: Story,
        val generation: Int,
        val attempt: Int,
        val finalAttempt: Boolean,
        val cause: Throwable,
    ) : StoriesEffect
    data class UserItemsSynced(
        val source: SavedItemSource,
        val generation: Int,
        val snapshot: SavedItemSnapshot,
    ) : StoriesEffect
    data class UserItemsSyncFailed(
        val source: SavedItemSource,
        val generation: Int,
        val summary: String,
        val detail: String? = null,
        val cause: Throwable? = null,
    ) : StoriesEffect
}

/**
 * Portable presentation owner for the stories screen.
 *
 * It combines list and search stores, accepts user intent as [StoriesAction], and emits only the
 * effects that a platform shell must perform. Android lifecycle, navigation, intents, and images
 * deliberately remain outside this class.
 */
class StoriesPresenter(
    private val scope: CoroutineScope,
    private val sessionState: StoriesSessionState,
    algoliaRepository: AlgoliaRepository,
    hackerNewsRepository: HackerNewsRepository,
    hackerNewsApi: HackerNewsApi,
    private val userItemsLoader: HackerNewsUserItemsLoader,
    private val savedItemsRepository: SavedItemsRepository,
    private val storyFeedLoader: StoryFeedLoader,
    clickedStoryIds: () -> List<Int>,
    isStoryClicked: (Int) -> Boolean,
    shouldFilterStory: (Story) -> Boolean,
    shouldHideClickedStories: () -> Boolean,
) : Feature<StoriesAction, StoriesPresenterState, StoriesEffect> {
    val mainStoryList = sessionState.mainStoryList
    val searchStoryList = sessionState.searchStoryList
    val searchStore = StorySearchStore(
        scope = scope,
        algoliaRepository = algoliaRepository,
        hackerNewsRepository = hackerNewsRepository,
        clickedStoryIds = clickedStoryIds,
        isStoryClicked = isStoryClicked,
        shouldFilterStory = shouldFilterStory,
        shouldHideClickedStories = shouldHideClickedStories,
    )

    private val mutableState = MutableStateFlow(
        StoriesPresenterState(
            searching = sessionState.searching,
            searchDraft = sessionState.lastSearch,
            updateAvailable = sessionState.updateButtonShowing,
            mainStoryType = sessionState.mainStoryType,
            searchStoryType = sessionState.searchStoryType,
            mainList = mainStoryList.state.value,
            searchList = searchStoryList.state.value,
        ),
    )
    override val state: StateFlow<StoriesPresenterState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<StoriesEffect>(extraBufferCapacity = 16)
    override val effects: SharedFlow<StoriesEffect> = mutableEffects.asSharedFlow()
    private var feedLoadJob: Job? = null
    private var nextScrapedPageJob: Job? = null
    private var userItemsLoadJob: Job? = null
    private val storyRowLoader = StoryRowLoadOrchestrator(
        scope = scope,
        hackerNewsApi = hackerNewsApi,
        staleLoadMillis = STORY_ROW_STALE_MILLIS,
        nowMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    )

    init {
        searchStore.restoreOptions(
            StorySearchOptions(
                sortIndex = sessionState.searchSortIndex,
                dateRangeIndex = sessionState.searchDateRangeIndex,
                minimumPointsIndex = sessionState.searchMinimumPointsIndex,
                minimumCommentsIndex = sessionState.searchMinimumCommentsIndex,
                onlyClicked = sessionState.searchOnlyClicked,
            ),
        )
        scope.launch { mainStoryList.state.collect { publish(mainList = it) } }
        scope.launch { searchStoryList.state.collect { publish(searchList = it) } }
        scope.launch { searchStore.state.collect(::applySearchState) }
        scope.launch { storyRowLoader.effects.collect(::applyStoryRowLoadEffect) }
    }

    override fun dispatch(intent: StoriesAction) {
        val action = intent
        when (action) {
            is StoriesAction.SetSearching -> publish(searching = action.searching)
            is StoriesAction.SetSearchDraft -> publish(searchDraft = action.query)
            is StoriesAction.SelectStoryType -> when (action.target) {
                StoryListTarget.MAIN -> publish(mainStoryType = action.type)
                StoryListTarget.SEARCH -> publish(searchStoryType = action.type)
            }
            is StoriesAction.EvaluateUpdateAvailability -> publish(
                updateAvailable = StoryFeedRefreshPolicy.shouldShowUpdateAffordance(
                    nowMillis = action.nowMillis,
                    lastLoadedMillis = action.lastLoadedMillis,
                    alwaysShow = action.alwaysShow,
                    searching = state.value.searching,
                    storyType = action.storyType,
                ),
            )
            StoriesAction.DismissUpdateAvailability -> publish(updateAvailable = false)
            is StoriesAction.Search -> {
                publish(searchDraft = action.query)
                searchStore.search(action.query, action.resetResultLimit)
            }
            is StoriesAction.LoadTopStories -> searchStore.loadTopStories(
                storyType = action.storyType,
                startTime = action.startTime,
                resetResultLimit = action.resetResultLimit,
            )
            StoriesAction.LoadMoreSearchResults -> searchStore.loadMore()
            StoriesAction.RetrySearch -> searchStore.retry()
            StoriesAction.ResetSearchOptions -> searchStore.resetOptions()
            is StoriesAction.SelectSearchSort -> searchStore.selectSort(action.index)
            is StoriesAction.SelectSearchDateRange -> searchStore.selectDateRange(action.index)
            is StoriesAction.SelectSearchMinimumPoints ->
                searchStore.selectMinimumPoints(action.index)
            is StoriesAction.SelectSearchMinimumComments ->
                searchStore.selectMinimumComments(action.index)
            StoriesAction.ToggleOnlyClicked -> searchStore.toggleOnlyClicked()
            is StoriesAction.SelectStoryLink -> selectStoryLink(action)
            is StoriesAction.SelectStoryComments -> selectStoryComments(action)
            is StoriesAction.LoadFeed -> loadFeed(action)
            is StoriesAction.LoadNextScrapedPage -> loadNextScrapedPage(action)
            StoriesAction.CancelFeedLoads -> cancelFeedLoads()
            is StoriesAction.LoadStoryRow -> storyRowLoader.load(
                story = action.story,
                preserveTime = action.preserveTime,
                requestGeneration = action.generation,
            )
            is StoriesAction.SyncUserItems -> syncUserItems(action)
        }
        applySearchState(searchStore.state.value)
    }

    private fun selectStoryLink(action: StoriesAction.SelectStoryLink) {
        val story = action.story
        val effect = when {
            !story.loaded && story.loadingFailed -> StoriesEffect.RetryStory(story, action.position)
            !story.loaded -> null
            story.isFrontpageLink -> story.url?.let {
                StoriesEffect.OpenExternalStory(story, action.position, it)
            }
            action.alwaysOpenComments ->
                StoriesEffect.OpenComments(story, action.position, showWebsite = false)
            story.isLink && action.useIntegratedWebView ->
                StoriesEffect.OpenComments(story, action.position, showWebsite = true)
            story.isLink -> story.url?.let {
                StoriesEffect.OpenExternalStory(story, action.position, it)
            }
            else -> StoriesEffect.OpenComments(story, action.position, showWebsite = false)
        }
        effect?.let(mutableEffects::tryEmit)
    }

    private fun selectStoryComments(action: StoriesAction.SelectStoryComments) {
        if (!action.story.loaded) return
        val effect = if (action.story.isFrontpageLink) {
            action.story.url?.let {
                StoriesEffect.OpenExternalStory(action.story, action.position, it)
            }
        } else {
            StoriesEffect.OpenComments(action.story, action.position, showWebsite = false)
        }
        effect?.let(mutableEffects::tryEmit)
    }

    private fun loadFeed(action: StoriesAction.LoadFeed) {
        feedLoadJob?.cancel()
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = null
        feedLoadJob = scope.launch {
            try {
                mutableEffects.emit(
                    StoriesEffect.FeedLoaded(
                        action.storyType,
                        action.generation,
                        storyFeedLoader.load(action.storyType, action.frontDay),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.FeedFailed(action.storyType, action.generation, error),
                )
            }
        }
    }

    private fun loadNextScrapedPage(action: StoriesAction.LoadNextScrapedPage) {
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = scope.launch {
            try {
                mutableEffects.emit(
                    StoriesEffect.NextScrapedPageLoaded(
                        action.storyType,
                        action.generation,
                        storyFeedLoader.loadNextScrapedPage(
                            action.storyType,
                            action.nextPageUrl,
                        ),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.NextScrapedPageFailed(
                        action.storyType,
                        action.generation,
                        error,
                    ),
                )
            }
        }
    }

    private fun cancelFeedLoads() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = null
        userItemsLoadJob?.cancel()
        userItemsLoadJob = null
    }

    private fun syncUserItems(action: StoriesAction.SyncUserItems) {
        userItemsLoadJob?.cancel()
        userItemsLoadJob = scope.launch {
            val upvoted = action.source == SavedItemSource.UPVOTED
            val path = if (upvoted) "upvoted" else "favorites"
            try {
                when (val result = userItemsLoader.getUserItems(path, loginRequired = upvoted)) {
                    is HackerNewsUserItemsResult.Success -> {
                        val snapshot = SavedItemSnapshots.normalize(
                            result.items.itemIds,
                            result.items.commentIds,
                        )
                        if (savedItemsRepository.loadSnapshot(action.source) != snapshot) {
                            savedItemsRepository.saveSnapshot(
                                action.source,
                                snapshot,
                                action.savedAtMillis,
                            )
                        }
                        mutableEffects.emit(
                            StoriesEffect.UserItemsSynced(
                                action.source,
                                action.generation,
                                snapshot,
                            ),
                        )
                    }
                    is HackerNewsUserItemsResult.Failure -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = action.source,
                            generation = action.generation,
                            summary = result.summary,
                            detail = result.detail,
                        ),
                    )
                    is HackerNewsUserItemsResult.Captcha -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = action.source,
                            generation = action.generation,
                            summary = "Captcha required",
                            detail = "HN asked for a captcha before syncing $path.",
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.UserItemsSyncFailed(
                        source = action.source,
                        generation = action.generation,
                        summary = "Couldn't sync $path",
                        detail = error.message,
                        cause = error,
                    ),
                )
            }
        }
    }

    fun beginStoryLoadGeneration(): Int {
        cancelFeedLoads()
        return storyRowLoader.beginGeneration()
    }

    val storyLoadGeneration: Int get() = storyRowLoader.generation

    fun isCurrentStoryLoadGeneration(generation: Int): Boolean =
        storyRowLoader.isCurrent(generation)

    fun isStoryRowLoadInProgress(storyId: Int): Boolean =
        storyRowLoader.isInProgress(storyId)

    fun cancelStoryRowLoad(storyId: Int) = storyRowLoader.cancel(storyId)

    fun clearStoryRowLoads() = storyRowLoader.clear()

    private suspend fun applyStoryRowLoadEffect(effect: StoryRowLoadEffect) {
        mutableEffects.emitAsStoriesEffect(effect)
    }

    private suspend fun MutableSharedFlow<StoriesEffect>.emitAsStoriesEffect(
        effect: StoryRowLoadEffect,
    ) {
        emit(
            when (effect) {
                is StoryRowLoadEffect.Loaded ->
                    StoriesEffect.StoryRowLoaded(effect.story, effect.generation)
                is StoryRowLoadEffect.Rejected ->
                    StoriesEffect.StoryRowRejected(effect.story, effect.generation)
                is StoryRowLoadEffect.AttemptFailed ->
                    StoriesEffect.StoryRowLoadAttemptFailed(
                        story = effect.story,
                        generation = effect.generation,
                        attempt = effect.attempt,
                        finalAttempt = effect.finalAttempt,
                        cause = effect.cause,
                    )
            },
        )
    }

    private fun applySearchState(state: StorySearchUiState) {
        sessionState.searchSortIndex = state.options.sortIndex
        sessionState.searchDateRangeIndex = state.options.dateRangeIndex
        sessionState.searchMinimumPointsIndex = state.options.minimumPointsIndex
        sessionState.searchMinimumCommentsIndex = state.options.minimumCommentsIndex
        sessionState.searchOnlyClicked = state.options.onlyClicked
        when (state.mode) {
            StorySearchMode.QUERY -> {
                sessionState.searchAlgoliaHitsPerPage = state.hitsPerPage
                sessionState.searchLastAlgoliaTopStoriesStartTime = state.topStoriesStartTime
            }
            StorySearchMode.TOP_STORIES -> {
                sessionState.mainAlgoliaHitsPerPage = state.hitsPerPage
                sessionState.mainLastAlgoliaTopStoriesStartTime = state.topStoriesStartTime
            }
            StorySearchMode.NONE -> Unit
        }
        publish(search = state)
    }

    private fun publish(
        searching: Boolean = state.value.searching,
        searchDraft: String = state.value.searchDraft,
        updateAvailable: Boolean = state.value.updateAvailable,
        mainStoryType: StoryType = state.value.mainStoryType,
        searchStoryType: StoryType = state.value.searchStoryType,
        mainList: StoryListUiState = state.value.mainList,
        searchList: StoryListUiState = state.value.searchList,
        search: StorySearchUiState = state.value.search,
    ) {
        sessionState.searching = searching
        sessionState.lastSearch = searchDraft
        sessionState.updateButtonShowing = updateAvailable
        sessionState.mainStoryType = mainStoryType
        sessionState.searchStoryType = searchStoryType
        mutableState.value = StoriesPresenterState(
            searching = searching,
            searchDraft = searchDraft,
            updateAvailable = updateAvailable,
            mainStoryType = mainStoryType,
            searchStoryType = searchStoryType,
            mainList = mainList,
            searchList = searchList,
            search = search,
        )
    }

    private companion object {
        const val STORY_ROW_STALE_MILLIS = 30_000L
    }
}
