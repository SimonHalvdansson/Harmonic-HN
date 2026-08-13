package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime

data class CommentsPlatformPresentation(
    val adBlockActive: Boolean,
    val readerModeAvailable: Boolean,
    val readerModeEnabled: Boolean,
    val topInsetPx: Int,
    val contentInsetLeftPx: Int,
    val contentInsetRightPx: Int,
)

/** Builds the portable comments rendering snapshot from the common feature state. */
object CommentsScreenStateFactory {
    fun create(
        feature: CommentsFeatureRuntime,
        platform: CommentsPlatformPresentation,
    ): CommentsScreenState? {
        val story = feature.story ?: return null
        val settings = feature.settingsState.value ?: return null
        val state = feature.state
        val thread = feature.state.thread
        return CommentsScreenState(
            story = story,
            accountUser = feature.accountUser,
            comments = thread.displayedComments,
            displaySettings = settings.displaySettings,
            commentsLoaded = state.loaded,
            commentsRefreshInProgress = state.refreshing,
            loadingFailed = state.failure != null,
            loadingFailedServerError = state.failure ==
                com.simon.harmonichackernews.presentation.StoryLoadFailure.NOT_FOUND,
            showUpdate = state.showUpdate,
            lastRefreshed = state.lastLoadedMillis,
            commentsByOpFilterActive = thread.commentsByOp,
            hasCommentsByOp = thread.hasCommentsByOp,
            adBlockActive = platform.adBlockActive,
            integratedWebView = settings.integratedWebView,
            readerModeAvailable = platform.readerModeAvailable,
            readerModeEnabled = platform.readerModeEnabled,
            currentSorting = thread.sorting,
            topInsetPx = platform.topInsetPx,
            contentInsetLeftPx = platform.contentInsetLeftPx,
            contentInsetRightPx = platform.contentInsetRightPx,
            storyVoteLoading = state.storyVoteLoading,
            storyFavoriteLoading = state.storyFavoriteLoading,
            pollVoteInFlightOptionId = state.pollVoteInFlightOptionId,
            storySummaryLoading = feature.summaryLoading,
            headerPreviewResource = feature.headerPreviewResource,
            commentFavoriteLoadingId = state.commentFavoriteLoadingId,
            commentVoteLoadingId = state.commentVoteLoadingId,
            commentVoteLoadingAction = state.commentVoteLoadingAction,
            downvotedCommentIds = state.downvotedCommentIds,
            searchQuery = thread.searchQuery,
            searchResults = thread.searchResults,
            visibleComments = thread.visibleComments,
        )
    }
}
