package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.PollOptionsLoader
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
    data class SetSorting(val sorting: String) : CommentsAction
    data class ToggleExpanded(val commentId: Int) : CommentsAction
    data class ExpandParents(val commentId: Int) : CommentsAction
    data class RestoreCollapsedComments(val collapsedIds: Set<Int>) : CommentsAction
    data object ShowCommentsByOp : CommentsAction
    data object ResetCommentsByOp : CommentsAction
    data class SetSearchQuery(val query: String) : CommentsAction
    data class BeginThreadLoad(val nowMillis: Long) : CommentsAction
    data class EvaluateUpdateAvailability(
        val nowMillis: Long,
        val alwaysShow: Boolean,
        val storyTimeEpochSeconds: Int,
    ) : CommentsAction
    data class SetLoaded(val loaded: Boolean) : CommentsAction
    data class SetRefreshing(val refreshing: Boolean) : CommentsAction
    data class SetFailure(val failure: StoryLoadFailure?) : CommentsAction
    data class SetShowUpdate(val show: Boolean) : CommentsAction
    data class SetStoryVoteLoading(val loading: Boolean) : CommentsAction
    data class SetStoryFavoriteLoading(val loading: Boolean) : CommentsAction
    data class RequestCommentActions(val comment: Comment) : CommentsAction
    data class ToggleBookmark(val itemId: Int) : CommentsAction
    data class ToggleStoryVote(val itemId: Int, val isComment: Boolean) : CommentsAction
    data class ToggleStoryFavorite(val itemId: Int, val isComment: Boolean) : CommentsAction
    data class ToggleCommentFavorite(val commentId: Int) : CommentsAction
    data class VoteComment(
        val commentId: Int,
        val direction: String,
        val previousDownvoted: Boolean,
    ) : CommentsAction
    data class LoadThread(
        val story: Story,
        val useAlgolia: Boolean,
        val filteredUsers: Set<String>,
        val sorting: String,
        val collapseTopLevel: Boolean,
        val previousResponse: String?,
        val restoreScrollFromCache: Boolean,
    ) : CommentsAction
    data class LoadPollOptions(val story: Story) : CommentsAction
    data object CancelThreadLoad : CommentsAction
    data object CancelPollOptionsLoad : CommentsAction
}

sealed interface CommentsEffect {
    data class ShowCommentActions(val comment: Comment) : CommentsEffect
    data class ThreadApplied(
        val requestId: Int,
        val storyId: Int,
        val contentApplied: Boolean,
        val networkCompleted: Boolean,
        val responseToCache: String? = null,
        val restoreScroll: Boolean = false,
        val broadcastStoryUpdate: Boolean = false,
        val headerChanged: Boolean = false,
        val usedOfficialFallback: Boolean = false,
    ) : CommentsEffect
    data class ThreadFailed(
        val requestId: Int,
        val storyId: Int,
        val result: CommentThreadLoadResult.Failure,
    ) : CommentsEffect
    data class PollOptionsChanged(
        val storyId: Int,
        val failedOptionId: Int? = null,
    ) : CommentsEffect
    data class PollOptionsLookupFailed(
        val storyId: Int,
        val cause: Throwable,
    ) : CommentsEffect
    data class SavedItemActionStarted(val request: CommentsSavedItemRequest) : CommentsEffect
    data class SavedItemActionCompleted(
        val request: CommentsSavedItemRequest,
        val outcome: SavedItemActionOutcome,
    ) : CommentsEffect
}

sealed interface CommentsSavedItemRequest {
    val itemId: Int

    data class StoryVote(override val itemId: Int) : CommentsSavedItemRequest
    data class StoryFavorite(override val itemId: Int) : CommentsSavedItemRequest
    data class CommentFavorite(override val itemId: Int) : CommentsSavedItemRequest
    data class CommentVote(
        override val itemId: Int,
        val direction: String,
        val previousDownvoted: Boolean,
    ) : CommentsSavedItemRequest
}

/** Portable comments-screen presentation owner; the platform shell handles emitted effects. */
class CommentsPresenter(
    private val scope: CoroutineScope,
    private val sessionState: CommentsSessionState,
    private val commentThreadRepository: CommentThreadRepository,
    private val pollOptionsLoader: PollOptionsLoader,
    private val savedItemActions: SavedItemActionUseCase,
) : Feature<CommentsAction, CommentsPresenterState, CommentsEffect> {
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
    override val state: StateFlow<CommentsPresenterState> = mutableState.asStateFlow()

    private val mutableEffects = MutableSharedFlow<CommentsEffect>(extraBufferCapacity = 8)
    override val effects: SharedFlow<CommentsEffect> = mutableEffects.asSharedFlow()
    private var threadLoadJob: Job? = null
    private var pollOptionsLoadJob: Job? = null
    private var pollOptionsStoryId: Int = 0
    private var pollOptionsLoadStarted = false
    private var pollOptionsLookupStarted = false
    private val threadLoadSession = KeyedRequestSession<Int>()
    private val savedItemActionJobs = mutableMapOf<String, Job>()

    val savedItemState: SavedItemStateReader get() = savedItemActions

    init {
        scope.launch { thread.state.collect { publish(thread = it) } }
    }

    override fun dispatch(intent: CommentsAction) {
        val action = intent
        when (action) {
            is CommentsAction.ResetThread -> thread.reset(action.story, action.header, action.sorting)
            is CommentsAction.SetSorting -> thread.setSorting(action.sorting)
            is CommentsAction.ToggleExpanded -> thread.toggleExpanded(action.commentId)
            is CommentsAction.ExpandParents -> thread.expandParents(action.commentId)
            is CommentsAction.RestoreCollapsedComments ->
                thread.restoreCollapsedComments(action.collapsedIds)
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
            is CommentsAction.SetLoaded -> publish(loaded = action.loaded)
            is CommentsAction.SetRefreshing -> publish(refreshing = action.refreshing)
            is CommentsAction.SetFailure -> publish(failure = action.failure)
            is CommentsAction.SetShowUpdate -> publish(showUpdate = action.show)
            is CommentsAction.SetStoryVoteLoading -> publish(storyVoteLoading = action.loading)
            is CommentsAction.SetStoryFavoriteLoading -> publish(storyFavoriteLoading = action.loading)
            is CommentsAction.RequestCommentActions ->
                mutableEffects.tryEmit(CommentsEffect.ShowCommentActions(action.comment))
            is CommentsAction.ToggleBookmark -> savedItemActions.toggleBookmark(action.itemId)
            is CommentsAction.ToggleStoryVote -> performSavedItemAction(
                request = CommentsSavedItemRequest.StoryVote(action.itemId),
                kind = SavedItemActionKind.VOTE,
            ) { savedItemActions.beginVote(
                    itemId = action.itemId,
                    isComment = action.isComment,
                    direction = if (savedItemActions.isUpvoted(action.itemId, action.isComment)) {
                        "un"
                    } else {
                        "up"
                    },
                ) }
            is CommentsAction.ToggleStoryFavorite -> performSavedItemAction(
                request = CommentsSavedItemRequest.StoryFavorite(action.itemId),
                kind = SavedItemActionKind.FAVORITE,
            ) { savedItemActions.beginFavorite(action.itemId, action.isComment) }
            is CommentsAction.ToggleCommentFavorite -> performSavedItemAction(
                request = CommentsSavedItemRequest.CommentFavorite(action.commentId),
                kind = SavedItemActionKind.FAVORITE,
            ) { savedItemActions.beginFavorite(action.commentId, isComment = true) }
            is CommentsAction.VoteComment -> performSavedItemAction(
                request = CommentsSavedItemRequest.CommentVote(
                    itemId = action.commentId,
                    direction = action.direction,
                    previousDownvoted = action.previousDownvoted,
                ),
                kind = SavedItemActionKind.VOTE,
            ) { savedItemActions.beginVote(
                    itemId = action.commentId,
                    isComment = true,
                    direction = action.direction,
                ) }
            is CommentsAction.LoadThread -> loadThread(action)
            is CommentsAction.LoadPollOptions -> loadPollOptions(action.story)
            CommentsAction.CancelThreadLoad -> {
                threadLoadJob?.cancel()
                threadLoadJob = null
                threadLoadSession.invalidate()
            }
            CommentsAction.CancelPollOptionsLoad -> cancelPollOptionsLoad()
        }
    }

    fun isCurrentThreadLoad(requestId: Int, storyId: Int): Boolean =
        threadLoadSession.isCurrent(requestId, storyId)

    private fun performSavedItemAction(
        request: CommentsSavedItemRequest,
        kind: SavedItemActionKind,
        createPending: () -> PendingSavedItemAction,
    ) {
        val key = "$kind:${request.itemId}"
        if (savedItemActionJobs[key]?.isActive == true) return
        val pending = createPending()
        if (request is CommentsSavedItemRequest.StoryVote) publish(storyVoteLoading = true)
        if (request is CommentsSavedItemRequest.StoryFavorite) publish(storyFavoriteLoading = true)
        mutableEffects.tryEmit(CommentsEffect.SavedItemActionStarted(request))
        savedItemActionJobs[key] = scope.launch {
            val outcome = savedItemActions.execute(pending)
            if (request is CommentsSavedItemRequest.StoryVote) publish(storyVoteLoading = false)
            if (request is CommentsSavedItemRequest.StoryFavorite) {
                publish(storyFavoriteLoading = false)
            }
            mutableEffects.emit(CommentsEffect.SavedItemActionCompleted(request, outcome))
            savedItemActionJobs.remove(key)
        }
    }

    private fun loadThread(action: CommentsAction.LoadThread) {
        threadLoadJob?.cancel()
        val storyId = action.story.id
        val topLevelCommentIds = action.story.kids?.toList().orEmpty()
        val requestId = threadLoadSession.begin(storyId)
        threadLoadJob = scope.launch {
            action.previousResponse?.let { cachedResponse ->
                runCatching {
                    commentThreadRepository.parseAlgolia(
                        cachedResponse,
                        topLevelCommentIds,
                        action.filteredUsers,
                    )
                }.getOrNull()?.let { parsed ->
                    if (threadLoadSession.isCurrent(requestId, storyId)) applyAlgoliaThread(
                        action = action,
                        requestId = requestId,
                        parsed = parsed,
                        networkCompleted = false,
                        responseToCache = null,
                        restoreScroll = action.restoreScrollFromCache,
                        broadcastStoryUpdate = false,
                    )
                }
            }
            val result = commentThreadRepository.load(
                storyId = storyId,
                useAlgolia = action.useAlgolia,
                filteredUsers = action.filteredUsers,
                topLevelCommentIds = topLevelCommentIds,
            )
            if (!threadLoadSession.isCurrent(requestId, storyId)) return@launch
            when (result) {
                is CommentThreadLoadResult.Algolia -> {
                    if (action.previousResponse.isNullOrEmpty() ||
                        action.previousResponse != result.response
                    ) {
                        applyAlgoliaThread(
                            action = action,
                            requestId = requestId,
                            parsed = result.parsed,
                            networkCompleted = true,
                            responseToCache = result.response,
                            restoreScroll = false,
                            broadcastStoryUpdate = action.previousResponse == null,
                        )
                    } else {
                        publish(loaded = true, refreshing = false, failure = null)
                        mutableEffects.emit(
                            CommentsEffect.ThreadApplied(
                                requestId = requestId,
                                storyId = storyId,
                                contentApplied = false,
                                networkCompleted = true,
                            ),
                        )
                    }
                }
                is CommentThreadLoadResult.Official -> {
                    CommentsPresentationPolicy.mergeOfficialStoryHeader(action.story, result.story)
                    thread.appendLoadedComments(
                        action.story,
                        result.comments,
                        action.sorting,
                        action.collapseTopLevel,
                    )
                    publish(loaded = true, refreshing = false, failure = null)
                    mutableEffects.emit(
                        CommentsEffect.ThreadApplied(
                            requestId = requestId,
                            storyId = storyId,
                            contentApplied = true,
                            networkCompleted = true,
                            headerChanged = true,
                            usedOfficialFallback = result.usedAsFallback,
                        ),
                    )
                }
                is CommentThreadLoadResult.Failure -> {
                    publish(
                        loaded = true,
                        refreshing = false,
                        failure = CommentsPresentationPolicy.failureFor(result),
                    )
                    mutableEffects.emit(
                        CommentsEffect.ThreadFailed(requestId, storyId, result),
                    )
                }
            }
        }
    }

    private fun loadPollOptions(story: Story) {
        if (pollOptionsStoryId != story.id) {
            cancelPollOptionsLoad()
            pollOptionsStoryId = story.id
        }
        when (
            CommentsPresentationPolicy.nextPollLoadAction(
                active = true,
                loadStarted = pollOptionsLoadStarted,
                lookupStarted = pollOptionsLookupStarted,
                story = story,
            )
        ) {
            PollLoadAction.NONE -> return
            PollLoadAction.LOAD_KNOWN_OPTIONS -> startPollOptionsLoad(story)
            PollLoadAction.LOOK_UP_OPTIONS -> {
                pollOptionsLookupStarted = true
                pollOptionsLoadJob = scope.launch {
                    try {
                        val optionIds = pollOptionsLoader.findOptionIds(story.id)
                        if (pollOptionsStoryId != story.id) return@launch
                        if (optionIds.isNotEmpty()) {
                            story.pollOptions = optionIds
                            startPollOptionsLoad(story)
                        }
                    } catch (error: kotlinx.coroutines.CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        if (pollOptionsStoryId == story.id) {
                            pollOptionsLookupStarted = false
                            mutableEffects.emit(
                                CommentsEffect.PollOptionsLookupFailed(story.id, error),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startPollOptionsLoad(story: Story) {
        val optionIds = story.pollOptions ?: return
        pollOptionsLoadStarted = true
        story.pollOptionArrayList = ArrayList(pollOptionsLoader.placeholders(optionIds))
        pollOptionsLoadJob = scope.launch {
            pollOptionsLoader.loadOptions(optionIds).collect { loaded ->
                if (pollOptionsStoryId != story.id) return@collect
                val pollOption = story.pollOptionArrayList
                    ?.firstOrNull { it.id == loaded.id }
                    ?: return@collect
                pollOption.points = loaded.points
                pollOption.text = loaded.text
                pollOption.loaded = loaded.loaded
                pollOption.loadFailed = loaded.loadFailed
                mutableEffects.emit(
                    CommentsEffect.PollOptionsChanged(
                        storyId = story.id,
                        failedOptionId = loaded.id.takeIf { loaded.loadFailed },
                    ),
                )
            }
        }
    }

    private fun cancelPollOptionsLoad() {
        pollOptionsLoadJob?.cancel()
        pollOptionsLoadJob = null
        pollOptionsStoryId = 0
        pollOptionsLoadStarted = false
        pollOptionsLookupStarted = false
    }

    private suspend fun applyAlgoliaThread(
        action: CommentsAction.LoadThread,
        requestId: Int,
        parsed: com.simon.harmonichackernews.network.AlgoliaCommentsResponse,
        networkCompleted: Boolean,
        responseToCache: String?,
        restoreScroll: Boolean,
        broadcastStoryUpdate: Boolean,
    ) {
        val headerChanged = parsed.updateStoryInformation(action.story, thread.allComments.size)
        thread.replaceParsedComments(
            action.story,
            parsed.comments,
            action.sorting,
            action.collapseTopLevel,
        )
        publish(
            loaded = true,
            refreshing = if (networkCompleted) false else state.value.refreshing,
            failure = null,
        )
        mutableEffects.emit(
            CommentsEffect.ThreadApplied(
                requestId = requestId,
                storyId = action.story.id,
                contentApplied = true,
                networkCompleted = networkCompleted,
                responseToCache = responseToCache,
                restoreScroll = restoreScroll,
                broadcastStoryUpdate = broadcastStoryUpdate,
                headerChanged = headerChanged || broadcastStoryUpdate,
            ),
        )
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
