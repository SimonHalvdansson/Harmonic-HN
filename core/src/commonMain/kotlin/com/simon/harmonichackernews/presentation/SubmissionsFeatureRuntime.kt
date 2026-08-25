package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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

/** Lifecycle-independent submissions workflow used by every platform shell. */
class SubmissionsFeatureRuntime(
    private val scope: CoroutineScope,
    val sessionState: SubmissionsSessionState,
    private val commentMasterResolver: CommentMasterResolver,
    private val useIntegratedWebView: () -> Boolean,
) {
    private val store = sessionState.submissions
    private val mutableEffects = MutableSharedFlow<SubmissionsRuntimeEffect>(
        extraBufferCapacity = 16,
    )
    val effects: SharedFlow<SubmissionsRuntimeEffect> = mutableEffects.asSharedFlow()
    val state: StateFlow<SubmissionsUiState> = store.state

    private var loadJob: Job? = null

    /** Initializes loading and returns retained scroll state when this session was seen before. */
    fun initialize(): SubmissionsScrollRestoration? {
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
        if (!state.value.loadedSuccessfully && !state.value.loading) refresh()
        return restoration
    }

    fun selectFilter(filter: SubmissionFilter) = store.selectFilter(filter)

    fun refresh() = load(resetResultLimit = true)

    fun loadMore() = load(resetResultLimit = false)

    fun recordScrollPosition(
        firstVisibleStoryPosition: Int,
        firstVisibleStoryTop: Int,
        appBarCollapsed: Boolean,
    ) {
        sessionState.firstVisibleStoryPosition = firstVisibleStoryPosition
        sessionState.firstVisibleStoryTop = firstVisibleStoryTop
        sessionState.appBarCollapsed = appBarCollapsed
    }

    fun openStoryLink(story: Story) {
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

    fun openStoryComments(story: Story) = openStory(story, showWebsite = false)

    fun openCommentReplies(story: Story) = openStory(story, showWebsite = false)

    fun openCommentMaster(story: Story) {
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
            if (state.value.items.contains(story)) store.contentChanged()
            openStory(resolved, showWebsite = false)
        }
    }

    fun dispose() {
        loadJob?.cancel()
        loadJob = null
        store.cancelLoad()
    }

    private fun load(resetResultLimit: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (resetResultLimit) store.refresh() else store.loadMore()
            loadJob = null
        }
    }

    private fun openStory(story: Story, showWebsite: Boolean) {
        mutableEffects.tryEmit(
            SubmissionsRuntimeEffect.OpenStory(story.toDestination(showWebsite = showWebsite)),
        )
    }
}
