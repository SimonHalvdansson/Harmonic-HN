package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
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
    val filter: SubmissionFilter = SubmissionFilter.BOTH,
    val hasUnfilteredItems: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadedSuccessfully: Boolean = false,
    val loading: Boolean = false,
    val showInitialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val emptyText: String = "No submissions",
    val revision: Int = 0,
)

/** Shared objects, with independent complete timeline prefixes for each filter. */
class SubmissionsStore(
    private val userName: String,
    private val repository: AlgoliaRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private data class LoadedRange(
        val ids: List<Int>,
        val limit: Int,
        val canLoadMore: Boolean,
        val queried: Boolean,
    )

    private val itemsById = mutableMapOf<Int, Story>()
    private val ranges = mutableMapOf<SubmissionFilter, LoadedRange>()
    private val mutableState = MutableStateFlow(SubmissionsUiState())
    val state: StateFlow<SubmissionsUiState> = mutableState.asStateFlow()
    private var requestSerial = 0

    init {
        require(userName.isNotBlank()) { "A username is required" }
        require(pageSize > 0) { "A positive page size is required" }
    }

    fun selectFilter(filter: SubmissionFilter) {
        if (mutableState.value.filter == filter) return
        cancelLoad()
        mutableState.value = mutableState.value.copy(filter = filter)
        publish()
    }

    suspend fun ensureLoaded() {
        val range = ranges[mutableState.value.filter]
        if (range == null || (!range.queried && range.canLoadMore)) {
            load(limit = maxOf(batchSize(), range?.ids?.size ?: 0), refresh = false)
        }
    }

    suspend fun refresh() = load(limit = batchSize(), refresh = true)

    private fun batchSize() = if (mutableState.value.filter == SubmissionFilter.BOTH) {
        pageSize
    } else {
        maxOf(1, pageSize / 2)
    }

    suspend fun loadMore() {
        if (mutableState.value.loading || !mutableState.value.canLoadMore) return
        val range = ranges.getValue(mutableState.value.filter)
        // Only commit the increased limit after success, so retries request the same range.
        load(limit = maxOf(range.limit, range.ids.size) + batchSize(), refresh = false)
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

    private suspend fun load(limit: Int, refresh: Boolean) {
        val filter = mutableState.value.filter
        val serial = ++requestSerial
        val previousRange = ranges[filter]
        mutableState.value = mutableState.value.copy(
            loading = true,
            showInitialLoading = previousRange == null,
            refreshing = refresh && previousRange != null,
        )
        try {
            if (filter == SubmissionFilter.BOTH && (refresh || ranges.isEmpty())) {
                val categoryLimit = maxOf(1, pageSize / 2)
                val (stories, comments) = coroutineScope {
                    val stories = async {
                        repository.getSubmissions(userName, categoryLimit, AlgoliaSubmissionType.STORIES)
                    }
                    val comments = async {
                        repository.getSubmissions(userName, categoryLimit, AlgoliaSubmissionType.COMMENTS)
                    }
                    stories.await() to comments.await()
                }
                currentCoroutineContext().ensureActive()
                if (serial != requestSerial) return
                ranges.clear()
                itemsById.clear()
                saveRange(SubmissionFilter.STORIES, stories, categoryLimit)
                saveRange(SubmissionFilter.COMMENTS, comments, categoryLimit)
                // Each category covers everything newer than its last timestamp. Exclude
                // the boundary second when truncated: Algolia can split timestamp ties.
                val cutoff = listOf(stories, comments)
                    .filter { it.canLoadMore }
                    .mapNotNull { it.items.lastOrNull()?.time }
                    .maxOrNull()
                val bothIds = itemsById.values
                    .filter { cutoff == null || it.time > cutoff }
                    .sortedWith(compareByDescending<Story> { it.time }.thenByDescending { it.id })
                    .map(Story::id)
                ranges[SubmissionFilter.BOTH] = LoadedRange(
                    ids = bothIds,
                    limit = pageSize,
                    canLoadMore = stories.canLoadMore || comments.canLoadMore,
                    queried = true,
                )
                publish()
                return
            }
            val loaded = repository.getSubmissions(userName, limit, when (filter) {
                SubmissionFilter.BOTH -> AlgoliaSubmissionType.BOTH
                SubmissionFilter.STORIES -> AlgoliaSubmissionType.STORIES
                SubmissionFilter.COMMENTS -> AlgoliaSubmissionType.COMMENTS
            })
            currentCoroutineContext().ensureActive()
            if (serial != requestSerial) return
            // Refresh starts a new snapshot; do not mix old coverage with a newer timeline.
            if (refresh) {
                ranges.clear()
                itemsById.clear()
            }
            saveRange(filter, loaded, limit)
            if (filter == SubmissionFilter.BOTH) shareBothRange()
            publish()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Preserve the successful range and its pagination state for retry.
        } finally {
            if (serial == requestSerial) cancelLoad()
        }
    }

    private fun saveRange(filter: SubmissionFilter, page: AlgoliaSubmissionsPage, limit: Int) {
        page.items.forEach { itemsById.getOrPut(it.id) { it } }
        ranges[filter] = LoadedRange(
            ids = page.items.map(Story::id).distinct(),
            limit = limit,
            canLoadMore = page.canLoadMore,
            queried = true,
        )
    }

    private fun shareBothRange() {
        val both = ranges.getValue(SubmissionFilter.BOTH)
        for (filter in listOf(SubmissionFilter.STORIES, SubmissionFilter.COMMENTS)) {
            val sharedIds = both.ids.filter { id ->
                itemsById.getValue(id).isComment == (filter == SubmissionFilter.COMMENTS)
            }
            val previous = ranges[filter]
            // Both is a complete prefix. It may extend a filtered view, but a filtered
            // response must never extend Both: the intervening other type may be missing.
            val ids = (sharedIds + previous?.ids.orEmpty()).distinct().sortedWith(
                compareByDescending<Int> { itemsById.getValue(it).time }.thenByDescending { it },
            )
            ranges[filter] = LoadedRange(
                ids = ids,
                limit = previous?.limit ?: pageSize,
                canLoadMore = both.canLoadMore && previous?.canLoadMore != false,
                queried = previous?.queried == true || !both.canLoadMore,
            )
        }
    }

    private fun publish() {
        val filter = mutableState.value.filter
        val range = ranges[filter]
        mutableState.value = mutableState.value.copy(
            items = range?.ids.orEmpty().map(itemsById::getValue),
            hasUnfilteredItems = itemsById.isNotEmpty(),
            // An inherited partial range needs a type-specific query before we know
            // whether this filter has more results (or any results at all).
            canLoadMore = range?.queried == true && range.canLoadMore,
            loadedSuccessfully = range != null,
            emptyText = when (filter) {
                SubmissionFilter.STORIES -> "No stories"
                SubmissionFilter.COMMENTS -> "No comments"
                SubmissionFilter.BOTH -> "No submissions"
            },
            revision = mutableState.value.revision + 1,
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}
