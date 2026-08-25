package com.simon.harmonichackernews.presentation

data class StorySearchApplication(
    val consumed: Boolean,
    val loading: Boolean = false,
    val completed: Boolean = false,
    val contentApplied: Boolean = false,
)

/** Applies shared search results to the retained main/search list stores. */
class StorySearchRuntime {
    private var loadMoreInProgress = false
    private var requestedVisibleCount = -1

    fun beginLoadMore(store: StoryListStore) {
        store.beginLoadMore()
        loadMoreInProgress = true
        requestedVisibleCount = if (store.state.value.paginationEnabled) {
            store.state.value.visibleStoryCount + StoryPaginationPolicy.DEFAULT_PAGE_SIZE
        } else {
            -1
        }
    }

    fun apply(
        store: StoryListStore,
        state: StorySearchUiState,
        searching: Boolean,
        activeTypeIsAlgolia: Boolean,
    ): StorySearchApplication {
        if (state.mode == StorySearchMode.NONE) return StorySearchApplication(false)
        if (state.mode == StorySearchMode.QUERY && !searching) return StorySearchApplication(false)
        if (state.mode == StorySearchMode.TOP_STORIES && (searching || !activeTypeIsAlgolia)) {
            return StorySearchApplication(false)
        }

        if (state.loading) {
            if (state.loadingMore) {
                if (!loadMoreInProgress) beginLoadMore(store)
            } else if (state.mode == StorySearchMode.QUERY && store.stories.isNotEmpty()) {
                store.clear()
            }
            return StorySearchApplication(consumed = true, loading = true)
        }

        if (state.failure != null) {
            store.setFailure(state.failure)
            clearLoadMoreState(store)
            return StorySearchApplication(consumed = true, completed = true)
        }

        store.setShowingCached(false)
        val nextStories = state.stories.toList()
        store.replace(nextStories, canLoadMore = state.canLoadMore)
        if (loadMoreInProgress && store.state.value.paginationEnabled) {
            store.setVisibleStoryCount(
                requestedVisibleCount
                    .coerceAtLeast(StoryPaginationPolicy.DEFAULT_PAGE_SIZE)
                    .coerceAtMost(store.stories.size),
            )
        }
        store.markLoadedThrough(store.stories.lastIndex)
        store.finishLoadMore(state.canLoadMore)
        loadMoreInProgress = false
        requestedVisibleCount = -1
        return StorySearchApplication(
            consumed = true,
            completed = true,
            contentApplied = true,
        )
    }

    fun cancel(store: StoryListStore) {
        clearLoadMoreState(store)
    }

    private fun clearLoadMoreState(store: StoryListStore) {
        store.finishLoadMore(store.state.value.canLoadMore)
        loadMoreInProgress = false
        requestedVisibleCount = -1
    }
}
