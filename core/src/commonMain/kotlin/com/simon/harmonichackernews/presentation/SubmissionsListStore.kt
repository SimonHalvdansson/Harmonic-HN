package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionCount
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    private var countGeneration = 0
    private val countJobs = mutableMapOf<SubmissionFilter, Job>()
    private var failedLoad: Pair<AlgoliaSubmissionsCursor, Boolean>? = null
    private val prefetchJobs = mutableMapOf<SubmissionFilter, Job>()
    private val prefetchAttempted = mutableSetOf<SubmissionFilter>()
    private var prefetchGeneration = 0

    /** Prefetch belongs to the feature lifetime, rather than the currently selected tab. */
    fun prefetchSparseFilters(scope: CoroutineScope) {
        val generation = prefetchGeneration
        scope.launch {
            if (generation != prefetchGeneration) return@launch
            val all = ranges[SubmissionFilter.BOTH] ?: return@launch
            if (all.nextCursor == null) return@launch
            for (filter in listOf(SubmissionFilter.STORIES, SubmissionFilter.COMMENTS)) {
                if (filter in ranges || filter in prefetchAttempted) continue
                if (mutableState.value.filter == filter && mutableState.value.loading) continue
                val cached = cachedItems(filter)
                if (cached.size >= minOf(PREFETCH_MIN_ITEMS, pageSize)) continue
                val count = counts[filter]
                if (count?.exact == true && count.value <= cached.size) continue
                prefetchAttempted += filter
                lateinit var job: Job
                job = scope.launch(start = CoroutineStart.LAZY) {
                    try {
                        val loaded = repository.getSubmissions(userName, pageSize, filter.apiType())
                        currentCoroutineContext().ensureActive()
                        if (generation != prefetchGeneration) return@launch
                        loaded.items.forEach { itemsById.getOrPut(it.id) { it } }
                        ranges[filter] = LoadedRange(
                            initialCategoryIds(filter, loaded.items, loaded.canLoadMore),
                            loaded.nextCursor,
                        )
                        loaded.totalCount?.let { counts[filter] = it }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        if (generation == prefetchGeneration && mutableState.value.filter == filter) {
                            failedLoad = AlgoliaSubmissionsCursor() to false
                            mutableState.value = mutableState.value.copy(loadingFailed = true)
                        }
                    } finally {
                        if (prefetchJobs[filter] === job) {
                            prefetchJobs.remove(filter)
                            if (mutableState.value.filter == filter) finishContentLoad()
                            publish()
                        }
                    }
                }
                prefetchJobs[filter] = job
                publish()
                job.start()
            }
        }
    }

    fun cancelPrefetch() {
        prefetchGeneration++
        val jobs = prefetchJobs.values.toList()
        prefetchJobs.clear()
        prefetchAttempted.clear()
        jobs.forEach(Job::cancel)
    }

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
        } else {
            // A filter switch cancels old counts even when this category's rows are retained.
            // Resume only missing metadata; there is no reason to reload its content.
            startCounts(refresh = false) { category, count ->
                counts[category] = count
                publish()
            }
        }
    }

    suspend fun refresh() {
        cancelPrefetch()
        cancelLoad()
        load(AlgoliaSubmissionsCursor(), refresh = true)
    }

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
        countGeneration++
        countJobs.values.toList().forEach(Job::cancel)
        countJobs.clear()
        finishContentLoad()
    }

    private fun finishContentLoad() {
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
            showInitialLoading = previousRange == null && cachedItems(filter).isEmpty(),
            refreshing = refresh && previousRange != null,
            loadingFailed = false,
        )
        val fetchedCounts = mutableMapOf<SubmissionFilter, AlgoliaSubmissionCount>()
        var contentPublished = false
        startCounts(refresh) { category, count ->
            fetchedCounts[category] = count
            if (contentPublished) {
                counts[category] = count
                publish()
            }
        }
        try {
            val loaded = repository.getSubmissions(userName, pageSize, filter.apiType(), cursor)
            currentCoroutineContext().ensureActive()
            if (serial != requestSerial) return
            // Commit only a successful refresh, preserving the previous snapshot on failure.
            if (refresh) {
                ranges.clear()
                itemsById.clear()
                counts.clear()
            }
            fetchedCounts.forEach { (category, count) ->
                counts[category] = count
            }
            loaded.totalCount?.let { counts[filter] = it }
            loaded.items.forEach { itemsById.getOrPut(it.id) { it } }
            val previousIds = if (refresh) emptyList() else previousRange?.ids.orEmpty()
            ranges[filter] = LoadedRange(
                ids = if (!refresh && previousRange == null) {
                    initialCategoryIds(filter, loaded.items, loaded.canLoadMore)
                } else {
                    (previousIds + loaded.items.map(Story::id)).distinct()
                },
                nextCursor = loaded.nextCursor,
            )
            contentPublished = true
            publish()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Retain the cursor and content so retry requests exactly the failed page.
            if (serial == requestSerial) {
                countGeneration++
                countJobs.values.toList().forEach(Job::cancel)
                countJobs.clear()
                failedLoad = cursor to refresh
                mutableState.value = mutableState.value.copy(loadingFailed = true)
            }
        } finally {
            if (serial == requestSerial) finishContentLoad()
        }
    }

    private suspend fun startCounts(
        refresh: Boolean,
        onCount: (SubmissionFilter, AlgoliaSubmissionCount) -> Unit,
    ) {
        val generation = countGeneration
        val filter = mutableState.value.filter
        // Children retain the feature's cancellation lifetime without delaying content/loading
        // state. Pagination can proceed while these jobs are still running.
        val requestScope = CoroutineScope(currentCoroutineContext())
        for (category in listOf(SubmissionFilter.STORIES, SubmissionFilter.COMMENTS)) {
            if (category == filter || (!refresh && category in counts) || countJobs[category]?.isActive == true) continue
            lateinit var job: Job
            job = requestScope.launch(start = CoroutineStart.LAZY) {
                try {
                    val count = loadCount(category)
                    if (generation == countGeneration && count != null) onCount(category, count)
                } finally {
                    if (countJobs[category] === job) countJobs.remove(category)
                }
            }
            countJobs[category] = job
            job.start()
        }
    }

    private suspend fun loadCount(filter: SubmissionFilter): AlgoliaSubmissionCount? = try {
        repository.getSubmissions(userName, 0, filter.apiType()).totalCount
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    // All's contiguous newest-first prefix is safe to display while a category's own
    // first page is fetched. Never use its cursor as a category pagination cursor.
    private fun cachedItems(filter: SubmissionFilter): List<Story> =
        ranges[SubmissionFilter.BOTH]?.ids.orEmpty().map(itemsById::getValue).filter {
            when (filter) {
                SubmissionFilter.BOTH -> true
                SubmissionFilter.STORIES -> !it.isComment
                SubmissionFilter.COMMENTS -> it.isComment
            }
        }

    private fun initialCategoryIds(
        filter: SubmissionFilter,
        items: List<Story>,
        canLoadMore: Boolean,
    ): List<Int> {
        // The response replaces its covered time range. Preserve only the older
        // cached tail so a shorter first page cannot make visible rows disappear.
        val oldest = items.minOfOrNull(Story::createdAtEpochSeconds)
        val tail = if (canLoadMore && oldest != null) {
            cachedItems(filter).filter { it.createdAtEpochSeconds <= oldest }
        } else emptyList()
        return (items + tail).distinctBy(Story::id)
            .sortedByDescending(Story::createdAtEpochSeconds).map(Story::id)
    }

    private fun publish() {
        val filter = mutableState.value.filter
        val range = ranges[filter]
        val items = range?.ids?.map(itemsById::getValue) ?: cachedItems(filter)
        val prefetching = filter in prefetchJobs
        val loading = mutableState.value.loading || prefetching
        mutableState.value = mutableState.value.copy(
            items = items,
            storyCount = counts[SubmissionFilter.STORIES],
            commentCount = counts[SubmissionFilter.COMMENTS],
            hasUnfilteredItems = itemsById.isNotEmpty(),
            canLoadMore = range?.nextCursor != null || (range == null && items.isNotEmpty()),
            loading = loading,
            showInitialLoading = loading && range == null && items.isEmpty(),
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
        const val PREFETCH_MIN_ITEMS = 10
    }
}
