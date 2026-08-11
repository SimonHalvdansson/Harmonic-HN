package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CommentsPresenterState(
    val thread: CommentThreadUiState = CommentThreadUiState(),
    val lastLoadedMillis: Long = 0L,
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val failure: StoryLoadFailure? = null,
    val showUpdate: Boolean = false,
    val storyVoteLoading: Boolean = false,
    val storyFavoriteLoading: Boolean = false,
)

sealed interface CommentsAction {
    data class ResetThread(
        val story: Story?,
        val header: Comment,
        val sorting: String,
    ) : CommentsAction
    data class ReplaceParsedComments(
        val story: Story?,
        val comments: List<Comment>,
        val sorting: String,
        val collapseTopLevel: Boolean,
    ) : CommentsAction
    data class AppendLoadedComments(
        val story: Story?,
        val comments: List<Comment>,
        val sorting: String,
        val collapseTopLevel: Boolean,
    ) : CommentsAction
    data class SetSorting(val sorting: String) : CommentsAction
    data class ToggleExpanded(val commentId: Int) : CommentsAction
    data class ExpandParents(val commentId: Int) : CommentsAction
    data object ShowCommentsByOp : CommentsAction
    data object ResetCommentsByOp : CommentsAction
    data class SetSearchQuery(val query: String) : CommentsAction
    data class BeginThreadLoad(val nowMillis: Long) : CommentsAction
    data class EvaluateUpdateAvailability(
        val nowMillis: Long,
        val alwaysShow: Boolean,
        val storyTimeEpochSeconds: Int,
    ) : CommentsAction
    data class ThreadLoadFailed(val result: CommentThreadLoadResult.Failure) : CommentsAction
    data class SetLoaded(val loaded: Boolean) : CommentsAction
    data class SetRefreshing(val refreshing: Boolean) : CommentsAction
    data class SetFailure(val failure: StoryLoadFailure?) : CommentsAction
    data class SetShowUpdate(val show: Boolean) : CommentsAction
    data class SetStoryVoteLoading(val loading: Boolean) : CommentsAction
    data class SetStoryFavoriteLoading(val loading: Boolean) : CommentsAction
    data class RequestCommentActions(val comment: Comment) : CommentsAction
    data class LoadThread(
        val requestId: Int,
        val storyId: Int,
        val useAlgolia: Boolean,
        val filteredUsers: Set<String>,
        val topLevelCommentIds: List<Int>,
        val previousResponse: String?,
        val restoreScrollFromCache: Boolean,
    ) : CommentsAction
    data object CancelThreadLoad : CommentsAction
}

sealed interface CommentsEffect {
    data class ShowCommentActions(val comment: Comment) : CommentsEffect
    data class ThreadLoaded(
        val requestId: Int,
        val storyId: Int,
        val result: CommentThreadLoadResult,
        val previousResponse: String?,
    ) : CommentsEffect
    data class CachedThreadParsed(
        val requestId: Int,
        val storyId: Int,
        val response: String,
        val parsed: AlgoliaCommentsResponse,
        val restoreScroll: Boolean,
    ) : CommentsEffect
}

/** Portable comments-screen presentation owner; the platform shell handles emitted effects. */
class CommentsPresenter(
    private val scope: CoroutineScope,
    private val sessionState: CommentsSessionState,
    private val commentThreadRepository: CommentThreadRepository,
) {
    val thread: CommentThreadStore = sessionState.commentThread
    private val mutableState = MutableStateFlow(
        CommentsPresenterState(
            thread = thread.state.value,
            lastLoadedMillis = sessionState.lastLoaded,
            loaded = sessionState.commentsLoaded,
            refreshing = sessionState.refreshInProgress,
            failure = when {
                !sessionState.loadingFailed -> null
                sessionState.loadingFailedServerError -> StoryLoadFailure.NOT_FOUND
                else -> StoryLoadFailure.GENERAL
            },
            showUpdate = sessionState.showUpdate,
            storyVoteLoading = sessionState.storyVoteLoading,
            storyFavoriteLoading = sessionState.storyFavoriteLoading,
        ),
    )
    val state: StateFlow<CommentsPresenterState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<CommentsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<CommentsEffect> = mutableEffects.asSharedFlow()
    private var threadLoadJob: Job? = null

    init {
        scope.launch { thread.state.collect { publish(thread = it) } }
    }

    fun dispatch(action: CommentsAction) {
        when (action) {
            is CommentsAction.ResetThread -> thread.reset(action.story, action.header, action.sorting)
            is CommentsAction.ReplaceParsedComments -> thread.replaceParsedComments(
                action.story,
                action.comments,
                action.sorting,
                action.collapseTopLevel,
            )
            is CommentsAction.AppendLoadedComments -> thread.appendLoadedComments(
                action.story,
                action.comments,
                action.sorting,
                action.collapseTopLevel,
            )
            is CommentsAction.SetSorting -> thread.setSorting(action.sorting)
            is CommentsAction.ToggleExpanded -> thread.toggleExpanded(action.commentId)
            is CommentsAction.ExpandParents -> thread.expandParents(action.commentId)
            CommentsAction.ShowCommentsByOp -> thread.showCommentsByOp()
            CommentsAction.ResetCommentsByOp -> thread.resetCommentsByOp()
            is CommentsAction.SetSearchQuery -> thread.setSearchQuery(action.query)
            is CommentsAction.BeginThreadLoad -> publish(
                lastLoadedMillis = action.nowMillis,
                showUpdate = false,
            )
            is CommentsAction.EvaluateUpdateAvailability -> publish(
                showUpdate = CommentsPresentationPolicy.shouldShowUpdateAffordance(
                    nowMillis = action.nowMillis,
                    lastLoadedMillis = state.value.lastLoadedMillis,
                    alwaysShow = action.alwaysShow,
                    storyTimeEpochSeconds = action.storyTimeEpochSeconds,
                ),
            )
            is CommentsAction.ThreadLoadFailed -> publish(
                loaded = true,
                refreshing = false,
                failure = CommentsPresentationPolicy.failureFor(action.result),
            )
            is CommentsAction.SetLoaded -> publish(loaded = action.loaded)
            is CommentsAction.SetRefreshing -> publish(refreshing = action.refreshing)
            is CommentsAction.SetFailure -> publish(failure = action.failure)
            is CommentsAction.SetShowUpdate -> publish(showUpdate = action.show)
            is CommentsAction.SetStoryVoteLoading -> publish(storyVoteLoading = action.loading)
            is CommentsAction.SetStoryFavoriteLoading -> publish(storyFavoriteLoading = action.loading)
            is CommentsAction.RequestCommentActions ->
                mutableEffects.tryEmit(CommentsEffect.ShowCommentActions(action.comment))
            is CommentsAction.LoadThread -> loadThread(action)
            CommentsAction.CancelThreadLoad -> {
                threadLoadJob?.cancel()
                threadLoadJob = null
            }
        }
    }

    private fun loadThread(action: CommentsAction.LoadThread) {
        threadLoadJob?.cancel()
        threadLoadJob = scope.launch {
            action.previousResponse?.let { cachedResponse ->
                runCatching {
                    commentThreadRepository.parseAlgolia(
                        cachedResponse,
                        action.topLevelCommentIds,
                        action.filteredUsers,
                    )
                }.getOrNull()?.let { parsed ->
                    mutableEffects.emit(
                        CommentsEffect.CachedThreadParsed(
                            action.requestId,
                            action.storyId,
                            cachedResponse,
                            parsed,
                            action.restoreScrollFromCache,
                        ),
                    )
                }
            }
            val result = commentThreadRepository.load(
                storyId = action.storyId,
                useAlgolia = action.useAlgolia,
                filteredUsers = action.filteredUsers,
                topLevelCommentIds = action.topLevelCommentIds,
            )
            mutableEffects.emit(
                CommentsEffect.ThreadLoaded(
                    action.requestId,
                    action.storyId,
                    result,
                    action.previousResponse,
                )
            )
        }
    }

    private fun publish(
        thread: CommentThreadUiState = state.value.thread,
        lastLoadedMillis: Long = state.value.lastLoadedMillis,
        loaded: Boolean = state.value.loaded,
        refreshing: Boolean = state.value.refreshing,
        failure: StoryLoadFailure? = state.value.failure,
        showUpdate: Boolean = state.value.showUpdate,
        storyVoteLoading: Boolean = state.value.storyVoteLoading,
        storyFavoriteLoading: Boolean = state.value.storyFavoriteLoading,
    ) {
        sessionState.lastLoaded = lastLoadedMillis
        sessionState.commentsLoaded = loaded
        sessionState.refreshInProgress = refreshing
        sessionState.loadingFailed = failure != null
        sessionState.loadingFailedServerError = failure == StoryLoadFailure.NOT_FOUND
        sessionState.showUpdate = showUpdate
        sessionState.storyVoteLoading = storyVoteLoading
        sessionState.storyFavoriteLoading = storyFavoriteLoading
        mutableState.value = CommentsPresenterState(
            thread = thread,
            lastLoadedMillis = lastLoadedMillis,
            loaded = loaded,
            refreshing = refreshing,
            failure = failure,
            showUpdate = showUpdate,
            storyVoteLoading = storyVoteLoading,
            storyFavoriteLoading = storyFavoriteLoading,
        )
    }
}
