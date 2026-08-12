package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.presentation.SavedListKind
import com.simon.harmonichackernews.presentation.SavedListPresentationPolicy
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesShellPresentationInput
import com.simon.harmonichackernews.presentation.StoriesShellPresentationPolicy
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_bookmark
import com.simon.harmonichackernews.resources.ic_history
import com.simon.harmonichackernews.resources.ic_star
import com.simon.harmonichackernews.resources.ic_thumb_up_filled

data class StoriesPlatformPresentation(
    val displaySettings: StoryDisplaySettings,
    val typeLabels: List<String>,
    val selectedTypeIndex: Int,
    val searchSortLabels: List<String>,
    val searchDateLabels: List<String>,
    val searchPointsLabels: List<String>,
    val searchCommentsLabels: List<String>,
    val online: Boolean,
    val lastUpdatedText: String?,
    val hasCachedStories: Boolean,
    val loggedIn: Boolean,
    val cacheInProgress: Boolean,
    val cacheProgressVisible: Boolean,
    val cacheProgress: Int,
    val cacheProgressMax: Int,
    val cacheProgressStatus: String,
    val canClearHistory: Boolean,
    val contentInsetStartPx: Int,
)

/** Maps common stories state plus a narrow platform snapshot into shared UI state. */
object StoriesScreenStateFactory {
    fun create(
        feature: StoriesFeatureRuntime,
        platform: StoriesPlatformPresentation,
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
                online = platform.online,
                bookmarks = type.isBookmarks,
                history = type.isHistory,
                userItems = type.isUserItemList,
                userItemsInitialLoadInProgress = feature.isUserItemsInitialLoadInProgress,
                refreshIndicatorShowing = feature.refreshIndicatorShowing,
                showingCached = listState.showingCached,
                cacheInProgress = platform.cacheInProgress,
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
            displaySettings = platform.displaySettings,
            typeLabels = platform.typeLabels,
            selectedTypeIndex = platform.selectedTypeIndex,
            searching = feature.searching,
            lastSearch = presenterState.searchDraft,
            searchSortLabel = feature.searchOptions.sortLabel,
            searchDateLabel = feature.searchOptions.dateRangeLabel,
            searchPointsLabel = feature.searchOptions.minimumPointsLabel,
            searchCommentsLabel = feature.searchOptions.minimumCommentsLabel,
            searchSortLabels = platform.searchSortLabels,
            searchDateLabels = platform.searchDateLabels,
            searchPointsLabels = platform.searchPointsLabels,
            searchCommentsLabels = platform.searchCommentsLabels,
            searchOnlyClicked = searchState.options.onlyClicked,
            loading = shell.showLoading,
            refreshing = feature.refreshIndicatorShowing,
            loadingFailed = listState.failure != null,
            loadingFailedServerError = listState.failure == StoryLoadFailure.NOT_FOUND,
            loadingFailedMessage = shell.loadingFailureMessage,
            showingCached = listState.showingCached,
            showCachedAction = listState.failure != null && !feature.searching &&
                platform.hasCachedStories,
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
            loggedIn = platform.loggedIn,
            canCache = shell.canCacheStories,
            canClearHistory = platform.canClearHistory,
            cacheProgressVisible = platform.cacheProgressVisible,
            cacheProgress = platform.cacheProgress,
            cacheProgressMax = platform.cacheProgressMax,
            cacheProgressStatus = platform.cacheProgressStatus,
            contentInsetStartPx = platform.contentInsetStartPx,
        )
    }
}
