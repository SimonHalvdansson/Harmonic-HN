package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime

data class CommentsPlatformPresentation(
    val displaySettings: CommentDisplaySettings,
    val adBlockActive: Boolean,
    val integratedWebView: Boolean,
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
        val state = feature.state
        val thread = feature.thread.state.value
        return CommentsScreenState(
            story = story,
            comments = feature.comments,
            displaySettings = platform.displaySettings,
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
            integratedWebView = platform.integratedWebView,
            readerModeAvailable = platform.readerModeAvailable,
            readerModeEnabled = platform.readerModeEnabled,
            currentSorting = thread.sorting,
            topInsetPx = platform.topInsetPx,
            contentInsetLeftPx = platform.contentInsetLeftPx,
            contentInsetRightPx = platform.contentInsetRightPx,
            storyVoteLoading = state.storyVoteLoading,
            storyFavoriteLoading = state.storyFavoriteLoading,
            searchQuery = thread.searchQuery,
            searchResults = thread.searchResults,
            visibleComments = thread.visibleComments,
        )
    }
}
