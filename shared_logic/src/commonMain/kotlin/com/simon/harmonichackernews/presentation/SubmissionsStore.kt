package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import kotlinx.coroutines.CancellationException
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

/** Shared state owner and loading/filtering workflow for the submissions screen. */
class SubmissionsStore(
    private val userName: String,
    private val repository: AlgoliaRepository,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    private val allItems = mutableListOf<Story>()
    private val mutableState = MutableStateFlow(SubmissionsUiState())
    val state: StateFlow<SubmissionsUiState> = mutableState.asStateFlow()
    private var resultLimit = pageSize
    private var initialLoadFinished = false

    init {
        require(userName.isNotBlank()) { "A username is required" }
        require(pageSize > 0) { "A positive page size is required" }
    }

    fun selectFilter(filter: SubmissionFilter) {
        if (mutableState.value.filter == filter) return
        publish(filter = filter)
    }

    suspend fun refresh() {
        resultLimit = pageSize
        load(refreshing = initialLoadFinished)
    }

    suspend fun loadMore() {
        if (mutableState.value.loading || !mutableState.value.canLoadMore) return
        resultLimit += pageSize
        load(refreshing = false)
    }

    fun cancelLoad() {
        mutableState.value = mutableState.value.copy(
            loading = false,
            showInitialLoading = false,
            refreshing = false,
        )
    }

    fun contentChanged() {
        mutableState.value = mutableState.value.copy(revision = mutableState.value.revision + 1)
    }

    private suspend fun load(refreshing: Boolean) {
        val showInitialLoading = !initialLoadFinished
        mutableState.value = mutableState.value.copy(
            loading = true,
            showInitialLoading = showInitialLoading,
            refreshing = refreshing,
        )
        try {
            val loaded = repository.getSubmissions(userName, resultLimit)
            allItems.clear()
            allItems.addAll(loaded)
            initialLoadFinished = true
            publish(
                canLoadMore = loaded.size >= resultLimit,
                loadedSuccessfully = true,
                loading = false,
                showInitialLoading = false,
                refreshing = false,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            initialLoadFinished = true
            mutableState.value = mutableState.value.copy(
                loading = false,
                showInitialLoading = false,
                refreshing = false,
            )
        }
    }

    private fun publish(
        filter: SubmissionFilter = mutableState.value.filter,
        canLoadMore: Boolean = mutableState.value.canLoadMore,
        loadedSuccessfully: Boolean = mutableState.value.loadedSuccessfully,
        loading: Boolean = mutableState.value.loading,
        showInitialLoading: Boolean = mutableState.value.showInitialLoading,
        refreshing: Boolean = mutableState.value.refreshing,
    ) {
        val visibleItems = allItems.filter { item ->
            when (filter) {
                SubmissionFilter.STORIES -> !item.isComment
                SubmissionFilter.COMMENTS -> item.isComment
                SubmissionFilter.BOTH -> true
            }
        }
        mutableState.value = mutableState.value.copy(
            items = visibleItems,
            filter = filter,
            hasUnfilteredItems = allItems.isNotEmpty(),
            canLoadMore = canLoadMore,
            loadedSuccessfully = loadedSuccessfully,
            loading = loading,
            showInitialLoading = showInitialLoading,
            refreshing = refreshing,
            emptyText = when {
                allItems.isEmpty() -> "No submissions"
                filter == SubmissionFilter.STORIES -> "No stories"
                filter == SubmissionFilter.COMMENTS -> "No comments"
                else -> "No submissions"
            },
            revision = mutableState.value.revision + 1,
        )
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 200
    }
}
