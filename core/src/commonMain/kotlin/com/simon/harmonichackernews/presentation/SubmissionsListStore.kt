package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionCount
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SubmissionsUiState(
    val items: List<Story> = emptyList(),
    val storyCount: AlgoliaSubmissionCount? = null,
    val commentCount: AlgoliaSubmissionCount? = null,
    val filter: SubmissionFilter = SubmissionFilter.BOTH,
    val hasUnfilteredItems: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadedSuccessfully: Boolean = false,
    val loading: Boolean = false,
    val showInitialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingFailed: Boolean = false,
    val emptyText: String = "No submissions",
    val revision: Int = 0,
)

/** Shared objects, with independently paginated timelines for each filter. */
class SubmissionsListStore(
    private val userName: String,
    private val repository: AlgoliaRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private data class LoadedRange(
        val ids: List<Int>,
        val nextCursor: AlgoliaSubmissionsCursor?,
    )

    private val itemsById = mutableMapOf<Int, Story>()
    private val ranges = mutableMapOf<SubmissionFilter, LoadedRange>()
    private val counts = mutableMapOf<SubmissionFilter, AlgoliaSubmissionCount>()
    private val mutableState = MutableStateFlow(SubmissionsUiState())
    val state: StateFlow<SubmissionsUiState> = mutableState.asStateFlow()
    private var requestSerial = 0
    private var failedLoad: Pair<AlgoliaSubmissionsCursor, Boolean>? = null

    init {
        require(userName.isNotBlank()) { "A username is required" }
        require(pageSize in 1..1000) { "Page size must be between 1 and 1000" }
    }

    fun selectFilter(filter: SubmissionFilter) {
        if (mutableState.value.filter == filter) return
        cancelLoad()
        failedLoad = null
        mutableState.value = mutableState.value.copy(filter = filter, loadingFailed = false)
        publish()
    }

    suspend fun ensureLoaded() {
        if (mutableState.value.loading) return
        if (ranges[mutableState.value.filter] == null) {
            load(AlgoliaSubmissionsCursor(), refresh = false)
        }
    }

    suspend fun refresh() = load(AlgoliaSubmissionsCursor(), refresh = true)

    suspend fun retry() {
        if (mutableState.value.loading) return
        val (cursor, refresh) = failedLoad ?: return
        load(cursor, refresh)
    }

    suspend fun loadMore() {
        if (mutableState.value.loading) return
        val cursor = ranges[mutableState.value.filter]?.nextCursor ?: return
        load(cursor, refresh = false)
    }

    fun cancelLoad() {
        requestSerial++
        mutableState.value = mutableState.value.copy(
            loading = false,
            showInitialLoading = false,
            refreshing = false,
        )
    }

    fun contentChanged() {
        mutableState.value = mutableState.value.copy(revision = mutableState.value.revision + 1)
    }

    private suspend fun load(cursor: AlgoliaSubmissionsCursor, refresh: Boolean) {
        val filter = mutableState.value.filter
        val serial = ++requestSerial
        val previousRange = ranges[filter]
        failedLoad = null
        mutableState.value = mutableState.value.copy(
            loading = true,
            showInitialLoading = previousRange == null,
            refreshing = refresh && previousRange != null,
            loadingFailed = false,
        )
        try {
            val (loaded, fetchedCounts) = coroutineScope {
                // Reuse the active category's normal response; fetch only metadata for
                // other categories. Count failures must not prevent reading submissions.
                val countRequests = listOf(SubmissionFilter.STORIES, SubmissionFilter.COMMENTS)
                    .filter { it != filter && (refresh || it !in counts) }
                    .associateWith { category ->
                        async { loadCount(category) }
                    }
                val page = repository.getSubmissions(userName, pageSize, filter.apiType(), cursor)
                page to countRequests.mapValues { (_, request) -> request.await() }
            }
            currentCoroutineContext().ensureActive()
            if (serial != requestSerial) return
            // Commit only a successful refresh, preserving the previous snapshot on failure.
            if (refresh) {
                ranges.clear()
                itemsById.clear()
                counts.clear()
            }
            fetchedCounts.forEach { (category, count) ->
                if (count != null) counts[category] = count
            }
            loaded.totalCount?.let { counts[filter] = it }
            loaded.items.forEach { itemsById.getOrPut(it.id) { it } }
            val previousIds = if (refresh) emptyList() else previousRange?.ids.orEmpty()
            ranges[filter] = LoadedRange(
                ids = (previousIds + loaded.items.map(Story::id)).distinct(),
                nextCursor = loaded.nextCursor,
            )
            publish()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Retain the cursor and content so retry requests exactly the failed page.
            if (serial == requestSerial) {
                failedLoad = cursor to refresh
                mutableState.value = mutableState.value.copy(loadingFailed = true)
            }
        } finally {
            if (serial == requestSerial) cancelLoad()
        }
    }

    private suspend fun loadCount(filter: SubmissionFilter): AlgoliaSubmissionCount? = try {
        repository.getSubmissions(userName, 0, filter.apiType()).totalCount
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private fun publish() {
        val filter = mutableState.value.filter
        val range = ranges[filter]
        mutableState.value = mutableState.value.copy(
            items = range?.ids.orEmpty().map(itemsById::getValue),
            storyCount = counts[SubmissionFilter.STORIES],
            commentCount = counts[SubmissionFilter.COMMENTS],
            hasUnfilteredItems = itemsById.isNotEmpty(),
            canLoadMore = range?.nextCursor != null,
            loadedSuccessfully = range != null,
            emptyText = when (filter) {
                SubmissionFilter.STORIES -> "No stories"
                SubmissionFilter.COMMENTS -> "No comments"
                SubmissionFilter.BOTH -> "No submissions"
            },
            revision = mutableState.value.revision + 1,
        )
    }

    private fun SubmissionFilter.apiType() = when (this) {
        SubmissionFilter.BOTH -> AlgoliaSubmissionType.BOTH
        SubmissionFilter.STORIES -> AlgoliaSubmissionType.STORIES
        SubmissionFilter.COMMENTS -> AlgoliaSubmissionType.COMMENTS
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 100
    }
}
