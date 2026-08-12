package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface CommentsRuntimeEffect {
    data class Presenter(val effect: CommentsEffect) : CommentsRuntimeEffect
    data class Platform(val effect: CommentsPlatformEffect) : CommentsRuntimeEffect
    data class StateChanged(val refreshNavigation: Boolean = false) : CommentsRuntimeEffect
}

/** Lifecycle-independent comments workflow used by every platform shell. */
class CommentsFeatureRuntime(
    private val scope: CoroutineScope,
    val sessionState: CommentsSessionState,
    val presenter: CommentsPresenter,
    private val nowMillis: () -> Long,
) {
    private val mutableEffects = MutableSharedFlow<CommentsRuntimeEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<CommentsRuntimeEffect> = mutableEffects.asSharedFlow()

    val thread: CommentThreadStore get() = presenter.thread
    val comments: MutableList<Comment> get() = thread.displayedComments
    val allComments: MutableList<Comment> get() = thread.allComments
    val story: Story? get() = sessionState.story
    val state: CommentsPresenterState get() = presenter.state.value
    val savedItems: SavedItemStateReader get() = presenter.savedItemState

    private var useAlgolia = true
    private var filteredUsers: Set<String> = emptySet()
    private var collapseTopLevel = false
    private var hasAccount = false

    init {
        scope.launch {
            presenter.effects.collect { mutableEffects.emit(CommentsRuntimeEffect.Presenter(it)) }
        }
    }

    fun initialize(
        initialStory: Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        sorting: String,
        restoring: Boolean,
    ) {
        if (!restoring) {
            sessionState.story = initialStory
            sessionState.showWebsite = showWebsite
            sessionState.scrollToCommentId = scrollToCommentId
            presenter.dispatch(CommentsAction.ResetThread(initialStory, Comment(), sorting))
        }
        sessionState.initialized = true
    }

    fun configure(
        hasAccount: Boolean,
        useAlgolia: Boolean,
        filteredUsers: Set<String>,
        collapseTopLevel: Boolean,
    ) {
        this.hasAccount = hasAccount
        this.useAlgolia = useAlgolia
        this.filteredUsers = filteredUsers
        this.collapseTopLevel = collapseTopLevel
    }

    fun load(
        cachedResponse: String?,
        restoreScrollFromCache: Boolean = false,
        refreshing: Boolean = false,
    ) {
        val story = story ?: return
        presenter.dispatch(CommentsAction.SetRefreshing(refreshing))
        presenter.dispatch(CommentsAction.BeginThreadLoad(nowMillis()))
        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = useAlgolia,
                filteredUsers = filteredUsers,
                sorting = sorting,
                collapseTopLevel = collapseTopLevel,
                previousResponse = cachedResponse,
                restoreScrollFromCache = restoreScrollFromCache,
            ),
        )
        presenter.dispatch(CommentsAction.LoadPollOptions(story))
        changed()
    }

    fun retry(cachedResponse: String? = null) = load(cachedResponse, refreshing = true)

    fun evaluateUpdate(alwaysShow: Boolean) {
        presenter.dispatch(
            CommentsAction.EvaluateUpdateAvailability(
                nowMillis = nowMillis(),
                alwaysShow = alwaysShow,
                storyTimeEpochSeconds = story?.time ?: 0,
            ),
        )
        changed()
    }

    fun toggleExpanded(comment: Comment) {
        presenter.dispatch(CommentsAction.ToggleExpanded(comment.id))
        changed(refreshNavigation = true)
    }

    fun expandParents(comment: Comment) {
        presenter.dispatch(CommentsAction.ExpandParents(comment.id))
        changed(refreshNavigation = true)
    }

    fun restoreCollapsedComments(ids: Set<Int>) {
        presenter.dispatch(CommentsAction.RestoreCollapsedComments(ids))
        changed(refreshNavigation = true)
    }

    fun setSearchQuery(query: String) {
        presenter.dispatch(CommentsAction.SetSearchQuery(query))
        changed()
    }

    fun recordScrollPosition(commentId: Int, offset: Int) {
        val progress = sessionState.scrollProgress
        progress.initialized = true
        progress.storyId = story?.id ?: 0
        progress.topCommentId = commentId
        progress.topCommentOffset = -offset
    }

    fun captureCollapsedComments() {
        val progress = sessionState.scrollProgress
        progress.initialized = true
        progress.storyId = story?.id ?: 0
        progress.collapsedIDs.clear()
        comments.asSequence()
            .filter { !it.expanded }
            .mapTo(progress.collapsedIDs) { it.id }
    }

    fun setSorting(sorting: String) {
        presenter.dispatch(CommentsAction.SetSorting(sorting))
        changed(refreshNavigation = true)
    }

    fun resetCommentsByOp() {
        presenter.dispatch(CommentsAction.ResetCommentsByOp)
        changed(refreshNavigation = true)
    }

    fun requestCommentActions(comment: Comment) =
        presenter.dispatch(CommentsAction.RequestCommentActions(comment))

    fun header(action: CommentsHeaderAction) {
        val story = story ?: return
        execute(CommentsUiOrchestrator.header(action, CommentsHeaderContext(story, hasAccount)))
    }

    fun share(action: CommentsShareAction) {
        story?.let { platform(CommentsUiOrchestrator.share(action, it)) }
    }

    fun more(action: CommentsMoreAction) {
        val story = story ?: return
        execute(CommentsUiOrchestrator.more(action, story, thread.state.value.commentsByOp))
    }

    fun sheet(action: CommentsSheetAction) = platform(CommentsUiOrchestrator.sheet(action))

    fun commentAction(
        action: CommentMenuAction,
        comment: Comment,
        voteLoading: Boolean,
        downvoted: Boolean,
    ) {
        execute(
            CommentsUiOrchestrator.comment(
                action = action,
                context = CommentActionContext(
                    comment = comment,
                    storyTitle = story?.title,
                    hasAccount = hasAccount,
                    voteLoading = voteLoading,
                    upvoted = savedItems.isUpvoted(comment.id, true),
                    downvoted = downvoted,
                    nowMillis = nowMillis(),
                ),
            ),
        )
    }

    fun findComment(id: Int): Comment? = thread.findComment(id)

    fun dispose() {
        presenter.dispatch(CommentsAction.CancelThreadLoad)
        presenter.dispatch(CommentsAction.CancelPollOptionsLoad)
    }

    private fun execute(decision: FeatureDecision<CommentsAction, CommentsPlatformEffect>) {
        decision.actions.forEach(presenter::dispatch)
        if (decision.refreshState || decision.refreshNavigation) {
            changed(decision.refreshNavigation)
        }
        decision.effects.forEach(::platform)
    }

    private fun platform(effect: CommentsPlatformEffect) {
        mutableEffects.tryEmit(CommentsRuntimeEffect.Platform(effect))
    }

    private fun changed(refreshNavigation: Boolean = false) {
        mutableEffects.tryEmit(CommentsRuntimeEffect.StateChanged(refreshNavigation))
    }

    private val sorting: String
        get() = thread.state.value.sorting.orEmpty()
}
