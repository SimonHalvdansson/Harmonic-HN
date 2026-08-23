package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.presentation.SavedListKind
import com.simon.harmonichackernews.presentation.SavedListPresentationPolicy
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.presentation.StoriesShellPresentationInput
import com.simon.harmonichackernews.presentation.StoriesShellPresentationPolicy
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.cache.StoryCacheStatus
import com.simon.harmonichackernews.platform.PresentationCopy
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_bookmark
import com.simon.harmonichackernews.resources.ic_history
import com.simon.harmonichackernews.resources.ic_star
import com.simon.harmonichackernews.resources.ic_thumb_up_filled

data class StoriesPlatformPresentation(
    val lastUpdatedText: String?,
    val contentInsetStartPx: Int,
)

/** Maps common stories state plus a narrow platform snapshot into shared UI state. */
object StoriesScreenStateFactory {
    fun create(
        state: StoriesState,
        platform: StoriesPlatformPresentation,
    ): StoriesScreenState {
        val listState = state.activeList
        val type = state.currentType
        val storyCache = state.cache
        val shell = StoriesShellPresentationPolicy.present(
            StoriesShellPresentationInput(
                searching = state.searching,
                submittedSearch = state.searchDraft.trim().isNotEmpty(),
                storyCount = state.activeItems.size,
                searchLoading = state.search.loading,
                loadingFailed = listState.failure != null,
                notFound = listState.failure == StoryLoadFailure.NOT_FOUND,
                rateLimited = state.loadingFailedRateLimited,
                online = state.online,
                bookmarks = type.isBookmarks,
                history = type.isHistory,
                userItems = type.isUserItemList,
                userItemsInitialLoadInProgress = state.userItemsInitialLoadInProgress,
                refreshIndicatorShowing = state.refreshIndicatorShowing,
                showingCached = listState.showingCached,
                cacheInProgress = storyCache.isCaching,
                visibleStoryCount = state.activeVisibleItemCount,
            ),
        )
        val savedKind = when {
            type.isHistory -> SavedListKind.HISTORY
            type.isFavorites -> SavedListKind.FAVORITES
            type.isUpvoted -> SavedListKind.UPVOTED
            else -> SavedListKind.BOOKMARKS
        }
        val emptyIcon = when (savedKind) {
            SavedListKind.HISTORY -> Res.drawable.ic_history
            SavedListKind.FAVORITES -> Res.drawable.ic_star
            SavedListKind.UPVOTED -> Res.drawable.ic_thumb_up_filled
            SavedListKind.BOOKMARKS -> Res.drawable.ic_bookmark
        }
        return StoriesScreenState(
            mainStories = state.mainList.items,
            searchStories = state.searchList.items,
            previewResources = state.previewResources,
            previewVoteLoadingIds = state.previewVoteLoadingIds,
            previewFavoriteLoadingIds = state.previewFavoriteLoadingIds,
            displaySettings = state.displaySettings,
            typeLabels = state.availableStoryTypes.map { it.label },
            selectedTypeIndex = state.selectedTypeIndex,
            searching = state.searching,
            lastSearch = state.searchDraft,
            searchSortLabel = state.search.sortLabel,
            searchDateLabel = state.search.dateLabel,
            searchPointsLabel = state.search.pointsLabel,
            searchCommentsLabel = state.search.commentsLabel,
            searchSortLabels = StorySearchController.sortLabels,
            searchDateLabels = StorySearchController.dateRangeLabels,
            searchPointsLabels = StorySearchController.minimumPointsLabels,
            searchCommentsLabels = StorySearchController.minimumCommentsLabels,
            searchOnlyClicked = state.search.options.onlyClicked,
            loading = shell.showLoading,
            refreshing = state.refreshIndicatorShowing,
            loadingFailed = listState.failure != null,
            loadingFailedServerError = listState.failure == StoryLoadFailure.NOT_FOUND,
            loadingFailedMessage = shell.loadingFailureMessage,
            showingCached = listState.showingCached,
            showCachedAction = listState.failure != null && !state.searching &&
                state.cachedStoriesAvailable,
            showEmptySavedList = shell.showEmptySavedList,
            emptySavedListText = SavedListPresentationPolicy.emptyMessage(
                savedKind,
                state.savedFilter,
                state.savedSourceHasItems,
            ),
            emptySavedListIcon = emptyIcon,
            showEmptySearch = shell.showEmptySearch,
            showUpdate = state.updateAvailable,
            lastUpdatedText = platform.lastUpdatedText,
            showLoadMore = state.activeHasLoadMore,
            loadMoreLoading = listState.loadMoreInProgress,
            mainVisibleCount = state.mainVisibleItemCount,
            searchVisibleCount = state.searchVisibleItemCount,
            showSavedFilter = !state.searching && type.usesSavedItemFilter() &&
                state.savedSourceHasItems,
            savedFilter = state.savedFilter,
            showFrontDate = !state.searching && type.isFront,
            frontDateLabel = state.frontDateLabel,
            frontPreviousEnabled = state.frontDateSelectedMillis > state.frontDateEarliestMillis,
            frontNextEnabled = state.frontDateSelectedMillis < state.frontDateLatestMillis,
            loggedIn = state.loggedIn,
            canCache = shell.canCacheStories,
            canClearHistory = state.canClearHistory,
            cacheProgressVisible = storyCache.progressVisible,
            cacheProgress = storyCache.completed,
            cacheProgressMax = storyCache.progressMax,
            cacheProgressStatus = when (storyCache.status) {
                StoryCacheStatus.IDLE -> PresentationCopy.CACHE_STORIES
                StoryCacheStatus.CACHING -> PresentationCopy.cachingStories(storyCache.total)
                StoryCacheStatus.FINISHED -> PresentationCopy.CACHE_FINISHED
                StoryCacheStatus.EMPTY -> PresentationCopy.CACHE_EMPTY
                StoryCacheStatus.FAILED -> PresentationCopy.CACHE_FAILED
            },
            contentInsetStartPx = platform.contentInsetStartPx,
        )
    }
}
