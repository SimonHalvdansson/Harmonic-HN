package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.presentation.CommentsState
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.debug.DebugCachedPostFixture

data class CommentsPlatformPresentation(
    val adBlockActive: Boolean,
    val readerModeAvailable: Boolean,
    val readerModeEnabled: Boolean,
    val showSheetControls: Boolean = true,
    val topInsetPx: Int,
    val contentInsetLeftPx: Int,
    val contentInsetRightPx: Int,
    val integratedWebViewAvailable: Boolean = true,
    val backButtonRequired: Boolean = false,
)

/** Builds the portable comments rendering snapshot from the common feature state. */
object CommentsScreenStateFactory {
    fun create(
        feature: CommentsState,
        platform: CommentsPlatformPresentation,
    ): CommentsScreenState? {
        val story = feature.story ?: return null
        val settings = feature.settings ?: return null
        val state = feature.presenter
        val thread = state.thread
        val headerId = thread.allComments.firstOrNull()?.id
        val comments = if (feature.debugMarkAllCommentsNew) {
            thread.filteredComments.map { if (it.id == headerId) it else it.copy(isNew = true) }
        } else thread.filteredComments
        val debugComments = if (feature.debugMarkAllCommentsNew) comments.associateBy { it.id } else emptyMap()
        // This seeded sample has no remote HN item. Once its cached thread is present,
        // keep expected refresh failures out of the animation-testing surface.
        val debugSampleLoaded = feature.debugMarkAllCommentsNew &&
            story.id == DebugCachedPostFixture.storyId && thread.visibleComments.isNotEmpty()
        return CommentsScreenState(
            story = story,
            accountUser = feature.accountUser,
            comments = comments,
            displaySettings = settings.displaySettings.copy(
                showUpButton = platform.backButtonRequired || settings.displaySettings.showUpButton,
                markNewComments = feature.debugMarkAllCommentsNew || settings.displaySettings.markNewComments,
            ),
            commentsLoaded = state.loaded || debugSampleLoaded,
            showingCached = state.showingCached,
            initialThreadCached = feature.initialThreadCached,
            commentsRefreshInProgress = state.refreshing && !debugSampleLoaded,
            loadingFailed = state.failure != null && !debugSampleLoaded,
            loadingFailedServerError = state.failure ==
                StoryLoadFailure.NOT_FOUND && !debugSampleLoaded,
            usingOfficialApiFallback = state.usingOfficialApiFallback && !debugSampleLoaded,
            showRefreshPrompt = state.showRefreshPrompt && !debugSampleLoaded,
            lastRefreshed = state.lastLoadedMillis,
            opThreadFilterEnabled = thread.opThreadFilterEnabled,
            hasCommentsByOp = thread.hasCommentsByOp,
            userThreadFilterEnabled = thread.userThreadFilterEnabled,
            hasCommentsByUser = thread.hasCommentsByUser,
            adBlockActive = platform.adBlockActive,
            integratedWebView = settings.integratedWebView && platform.integratedWebViewAvailable,
            readerModeAvailable = platform.readerModeAvailable,
            readerModeEnabled = platform.readerModeEnabled,
            showSheetControls = platform.showSheetControls,
            currentSorting = thread.sorting,
            topInsetPx = platform.topInsetPx,
            contentInsetLeftPx = platform.contentInsetLeftPx,
            contentInsetRightPx = platform.contentInsetRightPx,
            storyVoteLoading = state.storyVoteLoading,
            storyCommented = feature.commented,
            storyFavoriteLoading = state.storyFavoriteLoading,
            pollVoteInFlightOptionId = state.pollVoteInFlightOptionId,
            storySummaryLoading = feature.summaryLoading,
            summaryDiagnostics = feature.summaryDiagnostics,
            headerPreviewResource = feature.headerPreviewResource,
            commentFavoriteLoadingId = state.commentFavoriteLoadingId,
            commentVoteLoadingId = state.commentVoteLoadingId,
            commentVoteLoadingAction = state.commentVoteLoadingAction,
            downvotedCommentIds = state.downvotedCommentIds,
            searchQuery = thread.searchQuery,
            searchPreparing = thread.searchPreparing,
            searchResults = if (feature.debugMarkAllCommentsNew) {
                thread.searchResults.map { debugComments[it.id] ?: it }
            } else thread.searchResults,
            visibleComments = if (feature.debugMarkAllCommentsNew) {
                thread.visibleComments.map { it.copy(comment = debugComments[it.comment.id] ?: it.comment) }
            } else thread.visibleComments,
        )
    }
}
