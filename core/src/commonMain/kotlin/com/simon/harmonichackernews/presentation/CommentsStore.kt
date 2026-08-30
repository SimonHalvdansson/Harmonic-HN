package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

/** Complete immutable comments feature state shared by Android, desktop, and iOS hosts. */
data class CommentsState(
    val story: StoryListItemSnapshot? = null,
    val showWebsite: Boolean = false,
    val accountUser: String? = null,
    val initialThreadCached: Boolean = false,
    val presenter: CommentsPresenterState = CommentsPresenterState(),
    val settings: CommentsSettingsState? = null,
    val summaryLoading: Boolean = false,
    val summaryDiagnostics: com.simon.harmonichackernews.summary.StorySummaryDiagnostics? = null,
    val headerPreviewResource: com.simon.harmonichackernews.network.StoryPreviewResourceState? = null,
) {
    val thread: PortableCommentThreadState get() = presenter.thread
}

sealed interface CommentsIntent {
    data class ToggleComment(val commentId: Int) : CommentsIntent
    data class RecordScrollPosition(val commentId: Int, val offset: Int) : CommentsIntent
    data class CommentAction(
        val comment: PortableCommentItem,
        val action: CommentMenuAction,
    ) : CommentsIntent
    data object OpenHeaderLink : CommentsIntent
    data class HeaderPreviewImageResult(val imageUrl: String, val success: Boolean) : CommentsIntent
    data class HeaderAction(val action: CommentsHeaderAction) : CommentsIntent
    data class ShareAction(val action: CommentsShareAction) : CommentsIntent
    data class MoreAction(val action: CommentsMoreAction) : CommentsIntent
    data class ExpandParents(val commentId: Int) : CommentsIntent
    data class SearchQuery(val query: String) : CommentsIntent
    data class Sort(val sorting: String) : CommentsIntent
    data class SheetAction(val action: CommentsSheetAction) : CommentsIntent
    data class PollOption(val optionId: Int) : CommentsIntent
}

/** Canonical owner for Comments state, intents, effects, and retained workflow state. */
class CommentsStore internal constructor(
    private val scope: CoroutineScope,
    private val runtime: CommentsFeatureRuntime,
) : FeatureStore<CommentsIntent, CommentsState, CommentsRuntimeEffect> {
    private val mutableState = MutableStateFlow(snapshot())
    override val state: StateFlow<CommentsState> = mutableState.asStateFlow()
    private val mutableEffects = MutableSharedFlow<CommentsRuntimeEffect>(extraBufferCapacity = 64)
    override val effects: SharedFlow<CommentsRuntimeEffect> = mutableEffects.asSharedFlow()
    private val jobs = mutableListOf<Job>()
    private var observing = false
    private var closed = false

    val savedItemState: SavedItemStateReader get() = runtime.savedItems

    fun start(
        initialStory: Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        restoring: Boolean,
        restoredSorting: String?,
    ) {
        if (closed) return
        observeOnce()
        runtime.initializeFromSettings(
            initialStory = initialStory,
            showWebsite = showWebsite,
            scrollToCommentId = scrollToCommentId,
            restoring = restoring,
            restoredSorting = restoredSorting,
        )
        publish()
    }

    fun onResume() {
        runtime.resume()
        publish()
    }

    fun reconcileSettings() {
        runtime.reconcileSettings()
        publish()
    }

    fun startSummary(articleText: String?) {
        runtime.startSummary(articleText)
        publish()
    }

    fun updatePresentationCapabilities(capabilities: CommentsPresentationCapabilities) {
        runtime.updatePresentationCapabilities(capabilities)
        publish()
    }

    /** Rebuilds the immutable UI snapshot after a story-scoped service mutates presentation data. */
    fun refreshStoryPresentation() {
        if (closed) return
        publish()
    }

    fun loadInitial(restoreScrollFromCache: Boolean) =
        runtime.loadInitial(restoreScrollFromCache).also { publish() }

    fun captureCollapsedComments() = runtime.captureCollapsedComments()
    fun restoreScrollProgress(): CommentsScrollRestoration? = runtime.restoreScrollProgress()
        .also { publish() }
    fun consumeCommentTarget(): CommentTargetResolution = runtime.consumeCommentTarget()
        .also { publish() }
    fun comment(commentId: Int): PortableCommentItem? = runtime.comment(commentId)
    fun canSwitchStoryView(storyId: Int): Boolean = runtime.canSwitchStoryView(storyId)

    fun recordHeaderPreviewTint(
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
    ): Int? = runtime.recordHeaderPreviewTint(
        sourceUrl,
        baseColorArgb,
        paletteConfigKey,
        tintColorArgb,
    ).also { publish() }

    override fun accept(intent: CommentsIntent) {
        if (closed) return
        when (intent) {
            is CommentsIntent.ToggleComment -> runtime.toggleExpanded(intent.commentId)
            is CommentsIntent.RecordScrollPosition -> {
                runtime.recordScrollPosition(intent.commentId, intent.offset)
                // Scroll restoration lives in CommentsSessionState, not CommentsState. Publishing
                // here rebuilt the complete immutable story snapshot for every scroll offset even
                // though collectors could not observe a state change.
                return
            }
            is CommentsIntent.CommentAction -> runtime.commentAction(
                action = intent.action,
                comment = intent.comment,
                voteLoading = runtime.state.commentVoteLoadingId == intent.comment.id,
                downvoted = intent.comment.id in runtime.state.downvotedCommentIds,
            )
            CommentsIntent.OpenHeaderLink -> runtime.openHeaderLink()
            is CommentsIntent.HeaderPreviewImageResult ->
                runtime.completeHeaderPreviewImageLoad(intent.imageUrl, intent.success)
            is CommentsIntent.HeaderAction -> runtime.header(intent.action)
            is CommentsIntent.ShareAction -> runtime.share(intent.action)
            is CommentsIntent.MoreAction -> runtime.more(intent.action)
            is CommentsIntent.ExpandParents -> runtime.expandParents(intent.commentId)
            is CommentsIntent.SearchQuery -> runtime.setSearchQuery(intent.query)
            is CommentsIntent.Sort -> runtime.setSorting(intent.sorting)
            is CommentsIntent.SheetAction -> runtime.sheet(intent.action)
            is CommentsIntent.PollOption -> runtime.votePollOption(intent.optionId)
        }
        publish()
    }

    override fun close() {
        if (closed) return
        closed = true
        jobs.forEach(Job::cancel)
        jobs.clear()
        runtime.dispose()
        scope.cancel()
    }

    private fun observeOnce() {
        if (observing) return
        observing = true
        jobs += scope.launch {
            runtime.effects.collect { effect ->
                mutableEffects.emit(effect)
                publish()
            }
        }
        jobs += scope.launch { runtime.presenter.state.collect { publish() } }
        jobs += scope.launch { runtime.thread.state.collect { publish() } }
        jobs += scope.launch { runtime.settingsState.collect { publish() } }
    }

    private fun publish() {
        mutableState.value = snapshot()
    }

    private fun snapshot(): CommentsState = CommentsState(
        story = runtime.story?.let { StoryListItemSnapshot(it.toSnapshot(), it.presentationSnapshot()) },
        showWebsite = runtime.sessionState.showWebsite,
        accountUser = runtime.accountUser,
        initialThreadCached = runtime.initialThreadCached,
        presenter = runtime.presenter.state.value,
        settings = runtime.settingsState.value,
        summaryLoading = runtime.summaryLoading,
        summaryDiagnostics = runtime.summaryDiagnostics,
        headerPreviewResource = runtime.headerPreviewResource,
    )
}
