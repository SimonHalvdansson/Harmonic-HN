package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
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

enum class StoryHistorySyncResult {
    UNCHANGED,
    CONTENT_CHANGED,
    ITEMS_REMOVED,
    REFRESH_REQUIRED,
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

data class StoryListItemSnapshot(
    val story: StorySnapshot,
    val presentation: StoryPresentationSnapshot,
)

/** Native-safe list state with no mutable model references. */
data class PortableStoryListState(
    val items: List<StoryListItemSnapshot> = emptyList(),
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
 * Android compatibility callers can still enrich legacy [Story] instances in place. Canonical
 * [state] snapshots never expose those mutable objects.
 */
class StoryListStore(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    val stories: MutableList<Story> = mutableListOf()
    private val paginationSession = StoryPaginationSession(pageSize)

    private val mutableState = MutableStateFlow(PortableStoryListState())
    val state: StateFlow<PortableStoryListState> = mutableState.asStateFlow()

    /** Temporary mutable-model view for Android and shared UI migration only. */
    private val mutableLegacyState = MutableStateFlow(StoryListUiState())
    val legacyState: StateFlow<StoryListUiState> = mutableLegacyState.asStateFlow()

    /** Source-compatible name for callers already migrated to immutable snapshots. */
    val portableState: StateFlow<PortableStoryListState> get() = state

    val visibleStoryItemCount: Int
        get() = state.value.visibleStoryCount
            .takeIf { state.value.paginationEnabled }
            ?.coerceAtMost(stories.size)
            ?: stories.size

    val hasLoadMore: Boolean
        get() = state.value.loadMoreInProgress || state.value.canLoadMore ||
            (state.value.paginationEnabled && state.value.visibleStoryCount < stories.size)

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
        paginationSession.clear()
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
        paginationSession.clear()
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
        paginationSession.clear()
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
        paginationSession.clear()
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

    fun removeAt(index: Int): Story? {
        if (index !in stories.indices) return null
        return stories.removeAt(index).also { publish() }
    }

    fun insertAt(index: Int, story: Story) {
        stories.add(index.coerceIn(0, stories.size), story)
        publish()
    }

    fun contentChanged() {
        publish()
    }

    fun syncHistory(
        clickedStoryIds: Set<Int>,
        searchingOnlyClicked: Boolean,
        showingHistory: Boolean,
        hideClicked: Boolean,
    ): StoryHistorySyncResult {
        if (searchingOnlyClicked) {
            val hadClicked = stories.any(Story::clicked)
            if (hadClicked) {
                stories.forEach { it.clicked = false }
                publish()
                return StoryHistorySyncResult.CONTENT_CHANGED
            }
            return StoryHistorySyncResult.UNCHANGED
        }
        if (showingHistory) return StoryHistorySyncResult.REFRESH_REQUIRED
        if (hideClicked) {
            val removed = stories.removeAll { it.id in clickedStoryIds }
            if (removed) {
                publish()
                return StoryHistorySyncResult.ITEMS_REMOVED
            }
            return StoryHistorySyncResult.REFRESH_REQUIRED
        }

        var changed = false
        stories.forEach { story ->
            val clicked = story.id in clickedStoryIds
            if (story.clicked != clicked) {
                story.clicked = clicked
                changed = true
            }
        }
        if (changed) publish()
        return if (changed) StoryHistorySyncResult.CONTENT_CHANGED else StoryHistorySyncResult.UNCHANGED
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

    fun beginNextPage(requestGeneration: Int): StoryPageLoadPlan? {
        val plan = paginationSession.beginNextPage(
            stories = stories,
            loadedThroughIndex = state.value.loadedThroughIndex,
            visibleStoryCount = state.value.visibleStoryCount,
            requestGeneration = requestGeneration,
        ) ?: return null
        publish(
            visibleStoryCount = plan.nextVisibleCount,
            loadMoreInProgress = true,
            failure = null,
        )
        return plan
    }

    fun finishNextPageStory(storyId: Int, requestGeneration: Int): Boolean {
        val completed = paginationSession.finishStory(storyId, requestGeneration)
        if (completed) {
            paginationSession.clear()
            publish(loadMoreInProgress = false)
        }
        return completed
    }

    fun hasPendingPageStories(): Boolean = paginationSession.hasPendingStories()

    fun clearPendingPage() {
        paginationSession.clear()
        publish(loadMoreInProgress = false)
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
        val revision = current.revision + 1
        val nextLegacyState = StoryListUiState(
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
            revision = revision,
        )
        val nextState = PortableStoryListState(
            items = stories.map { story ->
                StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
            },
            visibleStoryCount = visibleStoryCount,
            loadedThroughIndex = loadedThroughIndex,
            paginationEnabled = paginationEnabled,
            loading = loading,
            refreshing = refreshing,
            loadMoreInProgress = loadMoreInProgress,
            canLoadMore = canLoadMore,
            showingCached = showingCached,
            failure = failure,
            revision = revision,
        )
        // Publish detached snapshots before exposing mutable compatibility objects.
        mutableState.value = nextState
        mutableLegacyState.value = nextLegacyState
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
