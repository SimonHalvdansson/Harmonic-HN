package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.applySnapshot
import com.simon.harmonichackernews.data.toSnapshot
import kotlin.time.Clock
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import io.ktor.http.URLBuilder
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
    val onlyRead: Boolean = false,
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
    val nextPage: Int = 0,
    val topStoriesStartTime: Int = 0,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val failure: StoryLoadFailure? = null,
    val revision: Long = 0,
)

/** Executes the complete portable Algolia/read-history search workflow. */
class StorySearchStore(
    private val scope: CoroutineScope,
    private val algoliaRepository: AlgoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository,
    private val readStoryIds: () -> List<Int>,
    private val isStoryRead: (Int) -> Boolean,
    private val shouldFilterStory: (Story) -> Boolean,
    private val shouldHideReadStories: () -> Boolean,
    private val controller: StorySearchController = StorySearchController(),
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(StorySearchUiState())
    val state: StateFlow<StorySearchUiState> = mutableState.asStateFlow()

    private var request: Request? = null
    private var loadJob: Job? = null
    private var generation = 0L
    // Keep filtered records too: read/hidden state can change before the next page arrives.
    private var fetchedStories: List<Story> = emptyList()
    private val readStoryRequests = Semaphore(MAX_CONCURRENT_HISTORY_REQUESTS)
    // Query/options changes reuse immutable metadata; never retain mutable result rows.
    private val readMetadata = mutableMapOf<Int, CachedReadStory>()
    private var readMetadataBytes = 0L

    val sortLabel: String get() = controller.sortLabel
    val dateRangeLabel: String get() = controller.dateRangeLabel
    val minimumPointsLabel: String get() = controller.minimumPointsLabel
    val minimumCommentsLabel: String get() = controller.minimumCommentsLabel

    fun getTopStoriesStartTime(storyType: StoryType): Int =
        controller.getCurrentTopStoriesStartTime(storyType)

    fun resetOptions() = updateOption(controller::resetOptions)

    fun restoreOptions(options: StorySearchOptions) = updateOption {
        controller.sortIndex = options.sortIndex.coerceIn(StorySearchController.sortLabels.indices)
        controller.dateRangeIndex = options.dateRangeIndex.coerceIn(StorySearchController.dateRangeLabels.indices)
        controller.minimumPointsIndex = options.minimumPointsIndex.coerceIn(StorySearchController.minimumPointsLabels.indices)
        controller.minimumCommentsIndex = options.minimumCommentsIndex.coerceIn(StorySearchController.minimumCommentsLabels.indices)
        if (controller.isOnlyRead != options.onlyRead) controller.toggleOnlyRead()
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

    fun toggleOnlyRead() = updateOption(controller::toggleOnlyRead)

    fun search(query: String?, forceRefresh: Boolean = false) {
        if (forceRefresh) clearReadMetadata()
        request = Request.Query(
            query.orEmpty(),
            controller.buildSearchUrl(query, StorySearchController.ALGOLIA_PAGE_SIZE),
        )
        resetPagination()
        execute(loadMore = false)
    }

    fun loadTopStories(
        storyType: StoryType,
        startTime: Int = controller.getCurrentTopStoriesStartTime(storyType),
    ) {
        request = Request.TopStories(
            startTime,
            controller.buildTopStoriesUrl(startTime, StorySearchController.ALGOLIA_PAGE_SIZE),
        )
        resetPagination()
        execute(loadMore = false)
    }

    fun loadMore() {
        if (state.value.loading || !state.value.canLoadMore || request == null) return
        execute(loadMore = true)
    }

    fun retry() {
        if (request == null || state.value.loading) return
        val retryPage = state.value.failure != null && state.value.nextPage > 0
        if (!retryPage) resetPagination()
        execute(loadMore = retryPage)
    }

    fun cancel(clearResults: Boolean = false) {
        cancelLoad()
        clearReadMetadata()
        request = null
        fetchedStories = emptyList()
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
        nextPage: Int,
        topStoriesStartTime: Int,
        options: StorySearchOptions,
        canLoadMore: Boolean,
        failure: StoryLoadFailure?,
    ) {
        cancelLoad()
        restoreOptions(options)
        request = when (mode) {
            StorySearchMode.QUERY -> Request.Query(
                query,
                controller.buildSearchUrl(query, StorySearchController.ALGOLIA_PAGE_SIZE),
            )
            StorySearchMode.TOP_STORIES -> Request.TopStories(
                topStoriesStartTime,
                controller.buildTopStoriesUrl(topStoriesStartTime, StorySearchController.ALGOLIA_PAGE_SIZE),
            )
            StorySearchMode.NONE -> null
        }
        fetchedStories = stories.toList()
        publish {
            copy(
                mode = mode,
                query = query,
                stories = stories,
                nextPage = nextPage.coerceAtLeast(0),
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
        val page = if (loadMore) state.value.nextPage else 0
        val retainedStories = if (loadMore) state.value.stories else emptyList()
        val retainedFetchedStories = if (loadMore) fetchedStories else emptyList()
        publish {
            copy(
                mode = activeRequest.mode,
                query = activeRequest.query,
                stories = retainedStories,
                topStoriesStartTime = activeRequest.topStoriesStartTime,
                loading = true,
                loadingMore = loadMore,
                failure = null,
            )
        }
        loadJob = scope.launch {
            val result = try {
                when {
                    activeRequest is Request.Query && controller.isOnlyRead ->
                        loadOnlyReadStories(activeRequest.query)
                    else -> loadAlgoliaStories(activeRequest, page, retainedFetchedStories)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                SearchResult(
                    stories = state.value.stories,
                    canLoadMore = loadMore,
                    nextPage = page,
                    failure = StoryFeedRefreshPolicy.failureFor(error),
                )
            }
            if (requestGeneration != generation) return@launch
            loadJob = null
            result.fetchedStories?.let { fetchedStories = it }
            publish {
                copy(
                    stories = result.stories,
                    loading = false,
                    loadingMore = false,
                    canLoadMore = result.canLoadMore,
                    nextPage = result.nextPage,
                    failure = result.failure,
                )
            }
        }
    }

    private suspend fun loadAlgoliaStories(
        request: Request,
        page: Int,
        retainedStories: List<Story>,
    ): SearchResult {
        // Freeze all search parameters (including relative date filters) for this pagination run.
        val url = URLBuilder(request.url).apply { parameters["page"] = page.toString() }.buildString()
        val response = algoliaRepository.search(url)
        check(response.page == page) { "Algolia returned an unexpected page" }
        // Keep the first occurrence and its existing presentation data. Live rankings can move
        // IDs across page boundaries; refreshing starts a new ordering instead of moving old rows.
        val ids = retainedStories.mapTo(mutableSetOf(), Story::id)
        val additions = response.stories.filter { ids.add(it.id) }
        val allStories = retainedStories + additions
        val visibleStories = allStories.filter { story ->
            story.isRead = isStoryRead(story.id)
            !shouldFilterStory(story) && !(shouldHideReadStories() && story.isRead)
        }
        return SearchResult(
            stories = visibleStories,
            canLoadMore = response.nextPage != null,
            nextPage = response.nextPage ?: (page + 1),
            fetchedStories = allStories,
        )
    }

    private suspend fun loadOnlyReadStories(query: String): SearchResult = coroutineScope {
        val ids = readStoryIds()
        val retainedIds = ids.toSet()
        val now = nowMillis()
        readMetadata.entries.removeAll { (id, cached) ->
            val expired = id !in retainedIds || now < cached.loadedAt || now - cached.loadedAt >= READ_METADATA_TTL_MILLIS
            if (expired) readMetadataBytes -= cached.estimatedBytes
            expired
        }
        if (ids.isEmpty()) return@coroutineScope SearchResult(emptyList(), canLoadMore = false)

        val normalizedQuery = controller.normalizeQuery(query)
        val requestGeneration = generation
        // Capture this query's hits: admitting new records may evict some of them from the cache.
        val cachedForQuery = readMetadata.toMap()
        val missing = ids.filter { it !in cachedForQuery }
        val loaded = missing.map { id ->
            async {
                readStoryRequests.withPermit {
                    try {
                        val story = hackerNewsRepository.getStory(id)?.also { it.isRead = true }
                        if (story != null && requestGeneration == generation) {
                            rememberReadStory(id, CachedReadStory(
                                story.toSnapshot(), story.isLink, story.pdfTitle, story.videoTitle, nowMillis(),
                            ))
                        }
                        ReadStoryLoad(
                            story = story,
                            failed = false,
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        ReadStoryLoad(story = null, failed = true)
                    }
                }
            }
        }.awaitAll()
        val byId = missing.zip(loaded).toMap()
        val outcomes = ids.map { id ->
            byId[id] ?: cachedForQuery[id]?.let {
                ReadStoryLoad(Story().applySnapshot(it.story).apply {
                    this.loaded = true
                    isRead = true
                    isLink = it.isLink
                    pdfTitle = it.pdfTitle
                    videoTitle = it.videoTitle
                }, failed = false)
            } ?: ReadStoryLoad(null, failed = false)
        }
        val filter = StorySearchController.StoryFilter(shouldFilterStory)
        val stories = outcomes.mapNotNull(ReadStoryLoad::story)
            .filter { story ->
                controller.shouldIncludeOnlyReadStory(story, normalizedQuery, filter)
            }
            .toMutableList()
        controller.sortOnlyReadResults(stories, query)
        SearchResult(
            stories = stories,
            canLoadMore = false,
            failure = StoryLoadFailure.GENERAL.takeIf { outcomes.all(ReadStoryLoad::failed) },
        )
    }

    private fun resetPagination() {
        fetchedStories = emptyList()
        publish {
            copy(
                nextPage = 0,
                loadingMore = false,
                canLoadMore = false,
            )
        }
    }

    private fun updateOption(block: () -> Unit) {
        cancelLoad()
        block()
        request = null
        publish {
            copy(
                options = currentOptions(),
                loading = false,
                loadingMore = false,
                canLoadMore = false,
            )
        }
    }

    private fun currentOptions() = StorySearchOptions(
        sortIndex = controller.sortIndex,
        dateRangeIndex = controller.dateRangeIndex,
        minimumPointsIndex = controller.minimumPointsIndex,
        minimumCommentsIndex = controller.minimumCommentsIndex,
        onlyRead = controller.isOnlyRead,
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
        abstract val url: String
        abstract val mode: StorySearchMode
        abstract val query: String
        abstract val topStoriesStartTime: Int

        data class Query(override val query: String, override val url: String) : Request() {
            override val mode = StorySearchMode.QUERY
            override val topStoriesStartTime = 0
        }

        data class TopStories(val startTime: Int, override val url: String) : Request() {
            override val mode = StorySearchMode.TOP_STORIES
            override val query = ""
            override val topStoriesStartTime = startTime
        }
    }

    private data class SearchResult(
        val stories: List<Story>,
        val canLoadMore: Boolean,
        val failure: StoryLoadFailure? = null,
        val nextPage: Int = 0,
        val fetchedStories: List<Story>? = null,
    )

    private data class ReadStoryLoad(val story: Story?, val failed: Boolean)

    private fun clearReadMetadata() {
        readMetadata.clear()
        readMetadataBytes = 0L
    }

    private fun rememberReadStory(id: Int, cached: CachedReadStory) {
        if (cached.estimatedBytes > MAX_READ_METADATA_BYTES) return
        readMetadata.remove(id)?.let { readMetadataBytes -= it.estimatedBytes }
        while (readMetadataBytes + cached.estimatedBytes > MAX_READ_METADATA_BYTES && readMetadata.isNotEmpty()) {
            val oldest = readMetadata.keys.first()
            readMetadataBytes -= readMetadata.remove(oldest)!!.estimatedBytes
        }
        readMetadata[id] = cached
        readMetadataBytes += cached.estimatedBytes
    }

    private data class CachedReadStory(
        val story: StorySnapshot,
        val isLink: Boolean,
        val pdfTitle: String?,
        val videoTitle: String?,
        val loadedAt: Long,
    ) {
        val estimatedBytes = 512L + 2L * ((story.title?.length ?: 0) + (story.text?.length ?: 0) +
            (story.url?.length ?: 0) + (story.author?.length ?: 0) +
            (pdfTitle?.length ?: 0) + (videoTitle?.length ?: 0)) +
            24L * (story.childIds.size + story.pollOptionIds.size)
    }

    private companion object {
        const val MAX_CONCURRENT_HISTORY_REQUESTS = 8
        const val READ_METADATA_TTL_MILLIS = 60_000L
        const val MAX_READ_METADATA_BYTES = 8L * 1024 * 1024
    }
}
