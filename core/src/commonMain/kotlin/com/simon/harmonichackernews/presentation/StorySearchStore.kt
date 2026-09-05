package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class StorySearchOptions(
    val sortIndex: Int = 0,
    val dateRangeIndex: Int = 0,
    val minimumPointsIndex: Int = 0,
    val minimumCommentsIndex: Int = 0,
    val onlyClicked: Boolean = false,
)

enum class StorySearchMode {
    NONE,
    QUERY,
    TOP_STORIES,
}

data class StorySearchUiState(
    val mode: StorySearchMode = StorySearchMode.NONE,
    val query: String = "",
    val stories: List<Story> = emptyList(),
    val options: StorySearchOptions = StorySearchOptions(),
    val hitsPerPage: Int = StorySearchController.ALGOLIA_HITS_INCREMENT,
    val topStoriesStartTime: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val failure: StoryLoadFailure? = null,
    val revision: Long = 0,
)

/** Executes the complete portable Algolia/clicked-history search workflow. */
class StorySearchStore(
    private val scope: CoroutineScope,
    private val algoliaRepository: AlgoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository,
    private val clickedStoryIds: () -> List<Int>,
    private val isStoryClicked: (Int) -> Boolean,
    private val shouldFilterStory: (Story) -> Boolean,
    private val shouldHideClickedStories: () -> Boolean,
    private val controller: StorySearchController = StorySearchController(),
) {
    private val mutableState = MutableStateFlow(StorySearchUiState())
    val state: StateFlow<StorySearchUiState> = mutableState.asStateFlow()

    private var request: Request? = null
    private var loadJob: Job? = null
    private var generation = 0L
    private val clickedStoryRequests = Semaphore(MAX_CONCURRENT_HISTORY_REQUESTS)

    val sortLabel: String get() = controller.sortLabel
    val dateRangeLabel: String get() = controller.dateRangeLabel
    val minimumPointsLabel: String get() = controller.minimumPointsLabel
    val minimumCommentsLabel: String get() = controller.minimumCommentsLabel

    fun getTopStoriesStartTime(storyType: StoryType): Int =
        controller.getCurrentTopStoriesStartTime(storyType)

    fun resetOptions() {
        controller.resetOptions()
        publish { copy(options = currentOptions()) }
    }

    fun restoreOptions(options: StorySearchOptions) {
        controller.sortIndex = options.sortIndex.coerceIn(StorySearchController.sortLabels.indices)
        controller.dateRangeIndex = options.dateRangeIndex.coerceIn(StorySearchController.dateRangeLabels.indices)
        controller.minimumPointsIndex = options.minimumPointsIndex.coerceIn(StorySearchController.minimumPointsLabels.indices)
        controller.minimumCommentsIndex = options.minimumCommentsIndex.coerceIn(StorySearchController.minimumCommentsLabels.indices)
        if (controller.isOnlyClicked != options.onlyClicked) controller.toggleOnlyClicked()
        publish { copy(options = currentOptions()) }
    }

    fun selectSort(index: Int) = updateOption {
        controller.sortIndex = index.coerceIn(StorySearchController.sortLabels.indices)
    }

    fun selectDateRange(index: Int) = updateOption {
        controller.dateRangeIndex = index.coerceIn(StorySearchController.dateRangeLabels.indices)
    }

    fun selectMinimumPoints(index: Int) = updateOption {
        controller.minimumPointsIndex = index.coerceIn(StorySearchController.minimumPointsLabels.indices)
    }

    fun selectMinimumComments(index: Int) = updateOption {
        controller.minimumCommentsIndex = index.coerceIn(StorySearchController.minimumCommentsLabels.indices)
    }

    fun toggleOnlyClicked() = updateOption(controller::toggleOnlyClicked)

    fun search(query: String?, resetResultLimit: Boolean = true) {
        request = Request.Query(query.orEmpty())
        if (resetResultLimit) resetResultLimit()
        execute(loadMore = false)
    }

    fun loadTopStories(
        storyType: StoryType,
        startTime: Int = controller.getCurrentTopStoriesStartTime(storyType),
        resetResultLimit: Boolean = true,
    ) {
        request = Request.TopStories(startTime)
        if (resetResultLimit) resetResultLimit()
        execute(loadMore = false)
    }

    fun loadMore() {
        if (state.value.loading || !state.value.canLoadMore || request == null) return
        execute(loadMore = true)
    }

    fun retry() {
        if (request != null) execute(loadMore = false)
    }

    fun cancel(clearResults: Boolean = false) {
        cancelLoad()
        request = null
        publish {
            copy(
                mode = StorySearchMode.NONE,
                query = "",
                stories = if (clearResults) emptyList() else stories,
                loading = false,
                loadingMore = false,
                canLoadMore = false,
                failure = null,
            )
        }
    }

    fun restore(
        mode: StorySearchMode,
        query: String,
        stories: List<Story>,
        hitsPerPage: Int,
        topStoriesStartTime: Int,
        options: StorySearchOptions,
        canLoadMore: Boolean,
        failure: StoryLoadFailure?,
    ) {
        cancelLoad()
        restoreOptions(options)
        request = when (mode) {
            StorySearchMode.QUERY -> Request.Query(query)
            StorySearchMode.TOP_STORIES -> Request.TopStories(topStoriesStartTime)
            StorySearchMode.NONE -> null
        }
        publish {
            copy(
                mode = mode,
                query = query,
                stories = stories,
                hitsPerPage = hitsPerPage.coerceAtLeast(StorySearchController.ALGOLIA_HITS_INCREMENT),
                topStoriesStartTime = topStoriesStartTime,
                loading = false,
                loadingMore = false,
                canLoadMore = canLoadMore,
                failure = failure,
                options = currentOptions(),
            )
        }
    }

    private fun cancelLoad() {
        generation++
        loadJob?.cancel()
        loadJob = null
    }

    private fun execute(loadMore: Boolean) {
        val activeRequest = request ?: return
        cancelLoad()
        val requestGeneration = generation
        val hitsPerPage = state.value.hitsPerPage +
            if (loadMore) StorySearchController.ALGOLIA_HITS_INCREMENT else 0
        publish {
            copy(
                mode = activeRequest.mode,
                query = activeRequest.query,
                stories = if (loadMore) stories else emptyList(),
                hitsPerPage = hitsPerPage,
                topStoriesStartTime = activeRequest.topStoriesStartTime,
                loading = true,
                loadingMore = loadMore,
                failure = null,
            )
        }
        loadJob = scope.launch {
            val result = try {
                when {
                    activeRequest is Request.Query && controller.isOnlyClicked ->
                        loadOnlyClickedStories(activeRequest.query)
                    else -> loadAlgoliaStories(activeRequest, hitsPerPage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SearchResult(
                    stories = state.value.stories,
                    canLoadMore = false,
                    failure = StoryFeedRefreshPolicy.failureFor(error),
                )
            }
            if (requestGeneration != generation) return@launch
            loadJob = null
            publish {
                copy(
                    stories = result.stories,
                    loading = false,
                    loadingMore = false,
                    canLoadMore = result.canLoadMore,
                    failure = result.failure,
                )
            }
        }
    }

    private suspend fun loadAlgoliaStories(request: Request, hitsPerPage: Int): SearchResult {
        val url = when (request) {
            is Request.Query -> controller.buildSearchUrl(request.query, hitsPerPage)
            is Request.TopStories -> controller.buildTopStoriesUrl(request.startTime, hitsPerPage)
        }
        val parsedStories = algoliaRepository.search(url)
        val visibleStories = parsedStories.filter { story ->
            story.clicked = isStoryClicked(story.id)
            !shouldFilterStory(story) && !(shouldHideClickedStories() && story.clicked)
        }
        return SearchResult(
            stories = visibleStories,
            canLoadMore = controller.canLoadMoreResults(parsedStories.size, hitsPerPage),
        )
    }

    private suspend fun loadOnlyClickedStories(query: String): SearchResult = coroutineScope {
        val ids = clickedStoryIds()
        if (ids.isEmpty()) return@coroutineScope SearchResult(emptyList(), canLoadMore = false)

        val normalizedQuery = controller.normalizeQuery(query)
        val outcomes = ids.map { id ->
            async {
                clickedStoryRequests.withPermit {
                    try {
                        ClickedStoryLoad(
                            story = hackerNewsRepository.getStory(id)?.also { it.clicked = true },
                            failed = false,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        ClickedStoryLoad(story = null, failed = true)
                    }
                }
            }
        }.awaitAll()
        val filter = StorySearchController.StoryFilter(shouldFilterStory)
        val stories = outcomes.mapNotNull(ClickedStoryLoad::story)
            .filter { story ->
                controller.shouldIncludeOnlyClickedStory(story, normalizedQuery, filter)
            }
            .toMutableList()
        controller.sortOnlyClickedResults(stories, query)
        SearchResult(
            stories = stories,
            canLoadMore = false,
            failure = StoryLoadFailure.GENERAL.takeIf { outcomes.all(ClickedStoryLoad::failed) },
        )
    }

    private fun resetResultLimit() {
        publish {
            copy(
                hitsPerPage = StorySearchController.ALGOLIA_HITS_INCREMENT,
                loadingMore = false,
                canLoadMore = false,
            )
        }
    }

    private fun updateOption(block: () -> Unit) {
        block()
        publish { copy(options = currentOptions()) }
    }

    private fun currentOptions() = StorySearchOptions(
        sortIndex = controller.sortIndex,
        dateRangeIndex = controller.dateRangeIndex,
        minimumPointsIndex = controller.minimumPointsIndex,
        minimumCommentsIndex = controller.minimumCommentsIndex,
        onlyClicked = controller.isOnlyClicked,
    )

    private inline fun publish(update: StorySearchUiState.() -> StorySearchUiState) {
        val current = state.value
        val next = current.update()
        mutableState.value = next.copy(
            stories = next.stories.toList(),
            revision = current.revision + 1,
        )
    }

    private sealed class Request {
        abstract val mode: StorySearchMode
        abstract val query: String
        abstract val topStoriesStartTime: Int

        data class Query(override val query: String) : Request() {
            override val mode = StorySearchMode.QUERY
            override val topStoriesStartTime = 0
        }

        data class TopStories(val startTime: Int) : Request() {
            override val mode = StorySearchMode.TOP_STORIES
            override val query = ""
            override val topStoriesStartTime = startTime
        }
    }

    private data class SearchResult(
        val stories: List<Story>,
        val canLoadMore: Boolean,
        val failure: StoryLoadFailure? = null,
    )

    private data class ClickedStoryLoad(val story: Story?, val failed: Boolean)

    private companion object {
        const val MAX_CONCURRENT_HISTORY_REQUESTS = 8
    }
}
