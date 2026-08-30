package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface SubmissionsIntent {
    data class SelectFilter(val filter: SubmissionFilter) : SubmissionsIntent
    data object Refresh : SubmissionsIntent
    data object LoadMore : SubmissionsIntent
    data class OpenStoryLink(val story: Story) : SubmissionsIntent
    data class OpenStoryComments(val story: Story) : SubmissionsIntent
    data class OpenCommentMaster(val story: Story) : SubmissionsIntent
    data class OpenCommentReplies(val story: Story) : SubmissionsIntent
    data class RecordScrollPosition(
        val firstVisibleStoryPosition: Int,
        val firstVisibleStoryTop: Int,
        val appBarCollapsed: Boolean,
    ) : SubmissionsIntent
}

sealed interface SubmissionsRuntimeEffect {
    data class OpenStory(
        val destination: StoryDestination,
    ) : SubmissionsRuntimeEffect

    data class OpenExternalLink(val url: String) : SubmissionsRuntimeEffect
}

data class SubmissionsScrollRestoration(
    val firstVisibleStoryPosition: Int,
    val firstVisibleStoryTop: Int,
    val appBarCollapsed: Boolean,
)

/** The single state, intent, effect, and lifecycle boundary for a submissions destination. */
class SubmissionsFeatureStore internal constructor(
    private val scope: CoroutineScope,
    private val sessionState: SubmissionsSessionState,
    private val commentMasterResolver: CommentMasterResolver,
    private val useIntegratedWebView: () -> Boolean,
) : FeatureStore<SubmissionsIntent, SubmissionsUiState, SubmissionsRuntimeEffect> {
    private val store = sessionState.submissions
    private val mutableEffects = MutableSharedFlow<SubmissionsRuntimeEffect>(
        extraBufferCapacity = 16,
    )
    override val effects: SharedFlow<SubmissionsRuntimeEffect> = mutableEffects.asSharedFlow()
    override val state: StateFlow<SubmissionsUiState> = store.state

    private var loadJob: Job? = null
    private var started = false
    private var closed = false
    private var initialRestoration: SubmissionsScrollRestoration? = null

    /** Starts loading exactly once and returns retained scroll state when this session was seen before. */
    fun start(): SubmissionsScrollRestoration? {
        if (started || closed) return initialRestoration
        started = true
        val restoration = if (sessionState.initialized) {
            SubmissionsScrollRestoration(
                firstVisibleStoryPosition = sessionState.firstVisibleStoryPosition,
                firstVisibleStoryTop = sessionState.firstVisibleStoryTop,
                appBarCollapsed = sessionState.appBarCollapsed,
            )
        } else {
            sessionState.initialized = true
            null
        }
        initialRestoration = restoration
        if (!state.value.loadedSuccessfully && !state.value.loading) refresh()
        return restoration
    }

    override fun accept(intent: SubmissionsIntent) {
        if (closed) return
        when (intent) {
            is SubmissionsIntent.SelectFilter -> store.selectFilter(intent.filter)
            SubmissionsIntent.Refresh -> refresh()
            SubmissionsIntent.LoadMore -> loadMore()
            is SubmissionsIntent.OpenStoryLink -> openStoryLink(intent.story)
            is SubmissionsIntent.OpenStoryComments -> openStory(intent.story, showWebsite = false)
            is SubmissionsIntent.OpenCommentMaster -> openCommentMaster(intent.story)
            is SubmissionsIntent.OpenCommentReplies -> openStory(intent.story, showWebsite = false)
            is SubmissionsIntent.RecordScrollPosition -> recordScrollPosition(
                firstVisibleStoryPosition = intent.firstVisibleStoryPosition,
                firstVisibleStoryTop = intent.firstVisibleStoryTop,
                appBarCollapsed = intent.appBarCollapsed,
            )
        }
    }

    private fun refresh() = load(resetResultLimit = true)

    private fun loadMore() = load(resetResultLimit = false)

    private fun recordScrollPosition(
        firstVisibleStoryPosition: Int,
        firstVisibleStoryTop: Int,
        appBarCollapsed: Boolean,
    ) {
        sessionState.firstVisibleStoryPosition = firstVisibleStoryPosition
        sessionState.firstVisibleStoryTop = firstVisibleStoryTop
        sessionState.appBarCollapsed = appBarCollapsed
    }

    private fun openStoryLink(story: Story) {
        if (!story.isLink) {
            openStory(story, showWebsite = false)
            return
        }
        if (useIntegratedWebView()) {
            openStory(story, showWebsite = true)
        } else {
            story.url?.let { mutableEffects.tryEmit(SubmissionsRuntimeEffect.OpenExternalLink(it)) }
        }
    }

    private fun openCommentMaster(story: Story) {
        val masterStory = story.toCommentMasterStory()
        if (masterStory == null) {
            openStory(story, showWebsite = false)
            return
        }
        if (masterStory.loaded) {
            openStory(masterStory, showWebsite = false)
            return
        }

        scope.launch {
            val resolved = try {
                commentMasterResolver.resolve(story)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                masterStory
            }
            currentCoroutineContext().ensureActive()
            if (closed) return@launch
            if (state.value.items.contains(story)) store.contentChanged()
            openStory(resolved, showWebsite = false)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        loadJob?.cancel()
        loadJob = null
        store.cancelLoad()
        scope.cancel()
    }

    private fun load(resetResultLimit: Boolean) {
        if (!resetResultLimit && (loadJob?.isActive == true || state.value.loading)) return
        loadJob?.cancel()
        val job = scope.launch {
            if (resetResultLimit) store.refresh() else store.loadMore()
        }
        loadJob = job
        job.invokeOnCompletion {
            if (loadJob === job) loadJob = null
        }
    }

    private fun openStory(story: Story, showWebsite: Boolean) {
        mutableEffects.tryEmit(
            SubmissionsRuntimeEffect.OpenStory(story.toDestination(showWebsite = showWebsite)),
        )
    }
}
