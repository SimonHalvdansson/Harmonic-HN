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
    data object Retry : SubmissionsIntent
    data object LoadMore : SubmissionsIntent
    data class OpenStoryLink(val story: Story) : SubmissionsIntent
    data class OpenStoryComments(val story: Story) : SubmissionsIntent
    data class OpenRootStory(val story: Story) : SubmissionsIntent
    data class OpenCommentReplies(val story: Story) : SubmissionsIntent
    data class RecordScrollPosition(
        val firstVisibleStoryPosition: Int,
        val firstVisibleStoryTop: Int,
        val appBarCollapsed: Boolean,
    ) : SubmissionsIntent
}

sealed interface SubmissionsFeatureEffect {
    data class OpenStory(
        val destination: StoryDestination,
    ) : SubmissionsFeatureEffect

    data class OpenExternalLink(val url: String) : SubmissionsFeatureEffect
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
    private val rootStoryResolver: CommentMasterResolver,
    private val useIntegratedWebView: () -> Boolean,
) : FeatureStore<SubmissionsIntent, SubmissionsUiState, SubmissionsFeatureEffect> {
    private val store = sessionState.submissions
    private val mutableEffects = MutableSharedFlow<SubmissionsFeatureEffect>(
        extraBufferCapacity = 16,
    )
    override val effects: SharedFlow<SubmissionsFeatureEffect> = mutableEffects.asSharedFlow()
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
        if (!state.value.loading) loadJob = scope.launch { store.ensureLoaded() }
        return restoration
    }

    override fun accept(intent: SubmissionsIntent) {
        if (closed) return
        when (intent) {
            is SubmissionsIntent.SelectFilter -> {
                if (state.value.filter != intent.filter) {
                    loadJob?.cancel()
                    store.selectFilter(intent.filter)
                    loadJob = scope.launch { store.ensureLoaded() }
                }
            }
            SubmissionsIntent.Refresh -> refresh()
            SubmissionsIntent.Retry -> {
                if (loadJob?.isActive != true && !state.value.loading) {
                    loadJob = scope.launch { store.retry() }
                }
            }
            SubmissionsIntent.LoadMore -> loadMore()
            is SubmissionsIntent.OpenStoryLink -> openStoryLink(intent.story)
            is SubmissionsIntent.OpenStoryComments -> openStory(intent.story, showWebsite = false)
            is SubmissionsIntent.OpenRootStory -> openRootStory(intent.story)
            is SubmissionsIntent.OpenCommentReplies -> openStory(intent.story, showWebsite = false)
            is SubmissionsIntent.RecordScrollPosition -> recordScrollPosition(
                firstVisibleStoryPosition = intent.firstVisibleStoryPosition,
                firstVisibleStoryTop = intent.firstVisibleStoryTop,
                appBarCollapsed = intent.appBarCollapsed,
            )
        }
    }

    private fun refresh() = load(refresh = true)

    private fun loadMore() = load(refresh = false)

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
            story.url?.let { mutableEffects.tryEmit(SubmissionsFeatureEffect.OpenExternalLink(it)) }
        }
    }

    private fun openRootStory(story: Story) {
        val masterStory = story.toRootStory()
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
                rootStoryResolver.resolve(story)
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

    private fun load(refresh: Boolean) {
        if (!refresh && (loadJob?.isActive == true || state.value.loading)) return
        loadJob?.cancel()
        val job = scope.launch {
            if (refresh) store.refresh() else store.loadMore()
        }
        loadJob = job
        job.invokeOnCompletion {
            if (loadJob === job) loadJob = null
        }
    }

    private fun openStory(story: Story, showWebsite: Boolean) {
        mutableEffects.tryEmit(
            SubmissionsFeatureEffect.OpenStory(story.toDestination(showWebsite = showWebsite)),
        )
    }
}
