package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class StoryLoadFailure {
    GENERAL,
    NOT_FOUND,
    RATE_LIMITED,
}

enum class SavedItemFilter {
    STORIES,
    BOTH,
    COMMENTS,
}

data class StoryListUiState(
    val stories: List<Story> = emptyList(),
    val visibleStoryCount: Int = Int.MAX_VALUE,
    val loadedThroughIndex: Int = -1,
    val paginationEnabled: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadMoreInProgress: Boolean = false,
    val canLoadMore: Boolean = false,
    val showingCached: Boolean = false,
    val failure: StoryLoadFailure? = null,
    val revision: Long = 0,
)

/**
 * Platform-neutral owner for a story list's loading and paging state.
 *
 * The mutable item collection is deliberately exposed to migration-era callers so Android can
 * continue enriching canonical [Story] instances in place. All structural changes should go
 * through this store, which publishes immutable snapshots for Compose and future KMP clients.
 */
class StoryListStore(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    val stories: MutableList<Story> = mutableListOf()

    private val mutableState = MutableStateFlow(StoryListUiState())
    val state: StateFlow<StoryListUiState> = mutableState.asStateFlow()

    fun restore(
        stories: List<Story>,
        visibleStoryCount: Int,
        loadedThroughIndex: Int,
        paginationEnabled: Boolean,
        canLoadMore: Boolean,
        showingCached: Boolean,
        failure: StoryLoadFailure?,
    ) {
        this.stories.clear()
        this.stories.addAll(stories)
        publish(
            visibleStoryCount = visibleStoryCount,
            loadedThroughIndex = loadedThroughIndex,
            paginationEnabled = paginationEnabled,
            canLoadMore = canLoadMore,
            showingCached = showingCached,
            failure = failure,
            loading = false,
            refreshing = false,
            loadMoreInProgress = false,
        )
    }

    fun beginLoad(refreshing: Boolean, clearItems: Boolean = false) {
        if (clearItems) stories.clear()
        publish(
            loading = !refreshing,
            refreshing = refreshing,
            failure = null,
            showingCached = false,
            loadedThroughIndex = if (clearItems) -1 else state.value.loadedThroughIndex,
        )
    }

    fun replace(
        stories: List<Story>,
        canLoadMore: Boolean = false,
        showingCached: Boolean = false,
    ) {
        this.stories.clear()
        this.stories.addAll(stories)
        val current = state.value
        publish(
            visibleStoryCount = if (current.paginationEnabled) {
                pageSize.coerceAtMost(this.stories.size)
            } else {
                Int.MAX_VALUE
            },
            loadedThroughIndex = -1,
            loading = false,
            refreshing = false,
            loadMoreInProgress = false,
            canLoadMore = canLoadMore,
            showingCached = showingCached,
            failure = null,
        )
    }

    fun clear() {
        stories.clear()
        publish(
            visibleStoryCount = if (state.value.paginationEnabled) pageSize else Int.MAX_VALUE,
            loadedThroughIndex = -1,
            loadMoreInProgress = false,
            canLoadMore = false,
        )
    }

    fun fail(failure: StoryLoadFailure) {
        publish(
            loading = false,
            refreshing = false,
            loadMoreInProgress = false,
            failure = failure,
        )
    }

    fun setFailure(failure: StoryLoadFailure?) {
        if (failure == null) {
            publish(failure = null)
        } else {
            publish(
                loading = false,
                refreshing = false,
                loadMoreInProgress = false,
                failure = failure,
            )
        }
    }

    fun cancelTransientLoads() {
        publish(
            loading = false,
            refreshing = false,
            loadMoreInProgress = false,
        )
    }

    fun mutateStories(block: MutableList<Story>.() -> Unit) {
        stories.block()
        publish()
    }

    fun setPaginationEnabled(enabled: Boolean) {
        publish(
            paginationEnabled = enabled,
            visibleStoryCount = if (enabled) {
                state.value.visibleStoryCount
                    .takeIf { it != Int.MAX_VALUE && it > 0 }
                    ?.coerceAtLeast(pageSize)
                    ?: pageSize
            } else {
                Int.MAX_VALUE
            },
        )
    }

    fun beginLoadMore() {
        publish(loadMoreInProgress = true, failure = null)
    }

    fun finishLoadMore(canLoadMore: Boolean) {
        publish(loadMoreInProgress = false, canLoadMore = canLoadMore)
    }

    fun setCanLoadMore(canLoadMore: Boolean) {
        publish(canLoadMore = canLoadMore)
    }

    fun revealNextPage(): Int {
        val current = state.value
        val nextVisibleCount = if (current.paginationEnabled) {
            (current.visibleStoryCount + pageSize).coerceAtMost(stories.size)
        } else {
            Int.MAX_VALUE
        }
        publish(visibleStoryCount = nextVisibleCount)
        return nextVisibleCount
    }

    fun setVisibleStoryCount(count: Int) {
        publish(visibleStoryCount = if (state.value.paginationEnabled) count.coerceAtLeast(0) else Int.MAX_VALUE)
    }

    fun markLoadedThrough(index: Int) {
        publish(loadedThroughIndex = index.coerceAtLeast(-1))
    }

    fun setShowingCached(showingCached: Boolean) {
        publish(showingCached = showingCached)
    }

    fun notifyStoryChanged() {
        publish()
    }

    fun filteredSavedItems(
        source: List<Story>,
        filter: SavedItemFilter,
        keepUnloadedItems: Boolean,
    ): List<Story> = source.filter { story ->
        when {
            keepUnloadedItems && !story.loaded -> true
            filter == SavedItemFilter.STORIES -> !story.isComment
            filter == SavedItemFilter.COMMENTS -> story.isComment
            else -> true
        }
    }

    private fun publish(
        visibleStoryCount: Int = state.value.visibleStoryCount,
        loadedThroughIndex: Int = state.value.loadedThroughIndex,
        paginationEnabled: Boolean = state.value.paginationEnabled,
        loading: Boolean = state.value.loading,
        refreshing: Boolean = state.value.refreshing,
        loadMoreInProgress: Boolean = state.value.loadMoreInProgress,
        canLoadMore: Boolean = state.value.canLoadMore,
        showingCached: Boolean = state.value.showingCached,
        failure: StoryLoadFailure? = state.value.failure,
    ) {
        val current = state.value
        mutableState.value = StoryListUiState(
            stories = stories.toList(),
            visibleStoryCount = visibleStoryCount,
            loadedThroughIndex = loadedThroughIndex,
            paginationEnabled = paginationEnabled,
            loading = loading,
            refreshing = refreshing,
            loadMoreInProgress = loadMoreInProgress,
            canLoadMore = canLoadMore,
            showingCached = showingCached,
            failure = failure,
            revision = current.revision + 1,
        )
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
