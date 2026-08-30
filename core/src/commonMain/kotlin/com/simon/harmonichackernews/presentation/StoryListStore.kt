package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.data.ItemTimeFormatter
import com.simon.harmonichackernews.data.loadedLinkPreviewType
import com.simon.harmonichackernews.utils.DomainNamePolicy
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

data class StoryListItemSnapshot(
    val story: StorySnapshot,
    val presentation: StoryPresentationSnapshot,
) {
    val id: Int get() = story.id
    val author: String? get() = story.author
    val title: String? get() = story.title
    val text: String? get() = story.text
    val url: String? get() = story.url
    val score: Int get() = story.score
    val descendantCount: Int get() = story.descendantCount
    val createdAtEpochSeconds: Int get() = story.createdAtEpochSeconds
    val isComment: Boolean get() = story.isComment
    val isJob: Boolean get() = story.isJob
    val loaded: Boolean get() = presentation.loaded
    val clicked: Boolean get() = presentation.clicked
    val loadingFailed: Boolean get() = presentation.loadingFailed
    val isLink: Boolean get() = presentation.isLink
    val isFrontpageLink: Boolean get() = presentation.isFrontpageLink
    val by: String? get() = author
    val descendants: Int get() = descendantCount
    val time: Int get() = createdAtEpochSeconds
    val parentId: Int get() = story.parentId
    val commentMasterId: Int get() = presentation.commentMaster?.id ?: 0
    val kids: List<Int> get() = story.childIds
    val pdfTitle: String? get() = presentation.pdfTitle
    val videoTitle: String? get() = presentation.videoTitle
    val summary: String? get() = presentation.summary
    val summaryGeneratedSuccessfully: Boolean
        get() = presentation.summaryGeneratedSuccessfully
    val previewImageUrl: String? get() = presentation.previewImage.url
    val previewImageTintColorLoaded: Boolean get() = presentation.previewTint?.loaded == true
    val previewImageTintColor: Int get() = presentation.previewTint?.colorArgb ?: 0
    val previewImageTintBaseColor: Int get() = presentation.previewTint?.baseColorArgb ?: 0
    val previewImageTintMode: String? get() = presentation.previewTint?.mode
    val faviconTintSourceUrl: String? get() = presentation.faviconTint?.sourceUrl
    val faviconTintColorLoaded: Boolean get() = presentation.faviconTint?.loaded == true
    val faviconTintColor: Int get() = presentation.faviconTint?.colorArgb ?: 0
    val faviconTintBaseColor: Int get() = presentation.faviconTint?.baseColorArgb ?: 0
    val faviconTintMode: String? get() = presentation.faviconTint?.mode
    val pollOptionArrayList get() = presentation.pollOptions.takeIf(List<*>::isNotEmpty)
    val repoInfo get() = presentation.repoInfo
    val gitLabInfo get() = presentation.gitLabInfo
    val huggingFaceInfo get() = presentation.huggingFaceInfo
    val openRouterInfo get() = presentation.openRouterInfo
    val stackExchangeInfo get() = presentation.stackExchangeInfo
    val arxivInfo get() = presentation.arxivInfo
    val wikiInfo get() = presentation.wikiInfo
    val nitterInfo get() = presentation.nitterInfo
    val linkPreviewInfo get() = presentation.linkPreviewInfo
    val linkPreviewLoading: Boolean get() = presentation.linkPreviewLoading
    val timeFormatted: String get() = ItemTimeFormatter.formatNow(time)

    fun getDisplayDomain(includeTopLevelDomain: Boolean): String? =
        DomainNamePolicy.fromUrl(url.orEmpty())?.let {
            DomainNamePolicy.formatForDisplay(it, includeTopLevelDomain)
        }

    fun loadedLinkPreviewType() = presentation.loadedLinkPreviewType()

    fun hasLoadedLinkPreview(): Boolean = loadedLinkPreviewType() != null

    fun hasExtraInfo(): Boolean = linkPreviewLoading || hasLoadedLinkPreview()
}

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
 * Repository loaders may still enrich [Story] instances in place. Canonical [state] snapshots
 * never expose those mutable objects to UI consumers.
 */
class StoryListStore(
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    val stories: MutableList<Story> = mutableListOf()
    private val paginationSession = StoryPaginationSession(pageSize)

    private val mutableState = MutableStateFlow(PortableStoryListState())
    val state: StateFlow<PortableStoryListState> = mutableState.asStateFlow()

    /** Source-compatible name retained for native callers that adopted the earlier name. */
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
            refreshItems = clearItems,
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
            refreshItems = false,
        )
    }

    fun setFailure(failure: StoryLoadFailure?) {
        if (failure == null) {
            publish(failure = null, refreshItems = false)
        } else {
            publish(
                loading = false,
                refreshing = false,
                loadMoreInProgress = false,
                failure = failure,
                refreshItems = false,
            )
        }
    }

    fun cancelTransientLoads() {
        paginationSession.clear()
        publish(
            loading = false,
            refreshing = false,
            loadMoreInProgress = false,
            refreshItems = false,
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

    fun contentChanged(story: Story? = null) {
        publish(changedStory = story)
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
            refreshItems = false,
        )
    }

    fun beginLoadMore() {
        publish(loadMoreInProgress = true, failure = null, refreshItems = false)
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
            refreshItems = false,
        )
        return plan
    }

    fun finishNextPageStory(storyId: Int, requestGeneration: Int): Boolean {
        val completed = paginationSession.finishStory(storyId, requestGeneration)
        if (completed) {
            paginationSession.clear()
            publish(loadMoreInProgress = false, refreshItems = false)
        }
        return completed
    }

    fun hasPendingPageStories(): Boolean = paginationSession.hasPendingStories()

    fun clearPendingPage() {
        paginationSession.clear()
        publish(loadMoreInProgress = false, refreshItems = false)
    }

    fun finishLoadMore(canLoadMore: Boolean) {
        publish(
            loadMoreInProgress = false,
            canLoadMore = canLoadMore,
            refreshItems = false,
        )
    }

    fun setCanLoadMore(canLoadMore: Boolean) {
        publish(canLoadMore = canLoadMore, refreshItems = false)
    }

    fun revealNextPage(): Int {
        val current = state.value
        val nextVisibleCount = if (current.paginationEnabled) {
            (current.visibleStoryCount + pageSize).coerceAtMost(stories.size)
        } else {
            Int.MAX_VALUE
        }
        publish(visibleStoryCount = nextVisibleCount, refreshItems = false)
        return nextVisibleCount
    }

    fun setVisibleStoryCount(count: Int) {
        publish(
            visibleStoryCount = if (state.value.paginationEnabled) count.coerceAtLeast(0)
            else Int.MAX_VALUE,
            refreshItems = false,
        )
    }

    fun markLoadedThrough(index: Int) {
        publish(loadedThroughIndex = index.coerceAtLeast(-1), refreshItems = false)
    }

    fun setShowingCached(showingCached: Boolean) {
        publish(showingCached = showingCached, refreshItems = false)
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
        refreshItems: Boolean = true,
        changedStory: Story? = null,
    ) {
        val current = state.value
        val revision = current.revision + 1
        val nextState = PortableStoryListState(
            items = when {
                !refreshItems -> current.items
                changedStory != null -> snapshotsWithChangedStory(current.items, changedStory)
                else -> stories.map { story -> story.toListItemSnapshot() }
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
        mutableState.value = nextState
    }

    private fun snapshotsWithChangedStory(
        currentItems: List<StoryListItemSnapshot>,
        changedStory: Story,
    ): List<StoryListItemSnapshot> {
        val changedIndex = stories.indexOf(changedStory)
        if (currentItems.size != stories.size || changedIndex !in currentItems.indices ||
            currentItems[changedIndex].id != changedStory.id
        ) {
            return stories.map { story -> story.toListItemSnapshot() }
        }
        return currentItems.toMutableList().apply {
            this[changedIndex] = changedStory.toListItemSnapshot()
        }
    }

    private fun Story.toListItemSnapshot(): StoryListItemSnapshot =
        StoryListItemSnapshot(toSnapshot(), presentationSnapshot())

    companion object {
        const val DEFAULT_PAGE_SIZE = 30
    }
}
