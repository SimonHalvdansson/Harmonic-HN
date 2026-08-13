package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.presentation.SavedListKind
import com.simon.harmonichackernews.presentation.SavedListPresentationPolicy
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesShellPresentationInput
import com.simon.harmonichackernews.presentation.StoriesShellPresentationPolicy
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.StorySearchController
import com.simon.harmonichackernews.cache.StoryCacheState
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
        feature: StoriesFeatureRuntime,
        platform: StoriesPlatformPresentation,
        storyCache: StoryCacheState,
    ): StoriesScreenState {
        val presenterState = feature.presenter.state.value
        val listState = feature.activeStore.state.value
        val searchState = feature.searchOptions.state.value
        val type = feature.currentType
        val shell = StoriesShellPresentationPolicy.present(
            StoriesShellPresentationInput(
                searching = feature.searching,
                submittedSearch = presenterState.searchDraft.trim().isNotEmpty(),
                storyCount = feature.activeStories.size,
                searchLoading = searchState.loading,
                loadingFailed = listState.failure != null,
                notFound = listState.failure == StoryLoadFailure.NOT_FOUND,
                rateLimited = feature.loadingFailedRateLimited,
                online = feature.online,
                bookmarks = type.isBookmarks,
                history = type.isHistory,
                userItems = type.isUserItemList,
                userItemsInitialLoadInProgress = feature.isUserItemsInitialLoadInProgress,
                refreshIndicatorShowing = feature.refreshIndicatorShowing,
                showingCached = listState.showingCached,
                cacheInProgress = storyCache.isCaching,
                visibleStoryCount = feature.activeStore.visibleStoryItemCount,
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
            mainStories = feature.mainStories,
            searchStories = feature.searchStories,
            previewResources = feature.previewResourceStates,
            displaySettings = feature.settingsState.value.displaySettings,
            typeLabels = feature.availableStoryTypes.map { it.label },
            selectedTypeIndex = feature.selectedStoryTypeIndex(),
            searching = feature.searching,
            lastSearch = presenterState.searchDraft,
            searchSortLabel = feature.searchOptions.sortLabel,
            searchDateLabel = feature.searchOptions.dateRangeLabel,
            searchPointsLabel = feature.searchOptions.minimumPointsLabel,
            searchCommentsLabel = feature.searchOptions.minimumCommentsLabel,
            searchSortLabels = StorySearchController.sortLabels.toList(),
            searchDateLabels = StorySearchController.dateRangeLabels.toList(),
            searchPointsLabels = StorySearchController.minimumPointsLabels.toList(),
            searchCommentsLabels = StorySearchController.minimumCommentsLabels.toList(),
            searchOnlyClicked = searchState.options.onlyClicked,
            loading = shell.showLoading,
            refreshing = feature.refreshIndicatorShowing,
            loadingFailed = listState.failure != null,
            loadingFailedServerError = listState.failure == StoryLoadFailure.NOT_FOUND,
            loadingFailedMessage = shell.loadingFailureMessage,
            showingCached = listState.showingCached,
            showCachedAction = listState.failure != null && !feature.searching &&
                feature.cachedStoriesAvailable,
            showEmptySavedList = shell.showEmptySavedList,
            emptySavedListText = SavedListPresentationPolicy.emptyMessage(
                savedKind,
                feature.savedFilter,
                feature.savedSourceHasItems,
            ),
            emptySavedListIcon = emptyIcon,
            showEmptySearch = shell.showEmptySearch,
            showUpdate = presenterState.updateAvailable,
            lastUpdatedText = platform.lastUpdatedText,
            showLoadMore = feature.activeStore.hasLoadMore,
            loadMoreLoading = listState.loadMoreInProgress,
            mainVisibleCount = feature.mainStore.visibleStoryItemCount,
            searchVisibleCount = feature.searchStore.visibleStoryItemCount,
            showSavedFilter = !feature.searching && type.usesSavedItemFilter() &&
                feature.savedSourceHasItems,
            savedFilter = feature.savedFilter,
            showFrontDate = !feature.searching && type.isFront,
            frontDateLabel = feature.frontPageDay.requestParameter,
            frontPreviousEnabled = feature.frontPageDay.selectedMillis >
                feature.frontPageDay.earliestMillis,
            frontNextEnabled = feature.frontPageDay.selectedMillis <
                feature.frontPageDay.latestMillis,
            loggedIn = feature.loggedIn,
            canCache = shell.canCacheStories,
            canClearHistory = feature.canClearHistory,
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
