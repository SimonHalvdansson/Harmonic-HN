package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.CoroutineScope
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
    val mainList: StoryListUiState = StoryListUiState(),
    val searchList: StoryListUiState = StoryListUiState(),
    val search: StorySearchUiState = StorySearchUiState(),
) {
    val activeList: StoryListUiState get() = if (searching) searchList else mainList
}

sealed interface StoriesAction {
    data class SetSearching(val searching: Boolean) : StoriesAction
    data class SetSearchDraft(val query: String) : StoriesAction
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
    clickedStoryIds: () -> List<Int>,
    isStoryClicked: (Int) -> Boolean,
    shouldFilterStory: (Story) -> Boolean,
    shouldHideClickedStories: () -> Boolean,
) {
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
            mainList = mainStoryList.state.value,
            searchList = searchStoryList.state.value,
        ),
    )
    val state: StateFlow<StoriesPresenterState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<StoriesEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<StoriesEffect> = mutableEffects.asSharedFlow()

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
    }

    fun dispatch(action: StoriesAction) {
        when (action) {
            is StoriesAction.SetSearching -> publish(searching = action.searching)
            is StoriesAction.SetSearchDraft -> publish(searchDraft = action.query)
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
        mainList: StoryListUiState = state.value.mainList,
        searchList: StoryListUiState = state.value.searchList,
        search: StorySearchUiState = state.value.search,
    ) {
        sessionState.searching = searching
        sessionState.lastSearch = searchDraft
        mutableState.value = StoriesPresenterState(
            searching = searching,
            searchDraft = searchDraft,
            mainList = mainList,
            searchList = searchList,
            search = search,
        )
    }
}
