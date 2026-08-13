package com.simon.harmonichackernews.ui.session

import com.simon.harmonichackernews.app.EditorFeatureSession
import com.simon.harmonichackernews.app.EditorFeatureSessionEvent
import com.simon.harmonichackernews.app.SubmissionsFeatureSession
import com.simon.harmonichackernews.app.SubmissionsFeatureSessionEvent
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.ui.submissions.SubmissionsComposeController
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State/effect host for submissions; platform coordinators only translate navigation effects. */
class SubmissionsScreenSession(
    private val scope: CoroutineScope,
    private val feature: SubmissionsFeatureSession,
) {
    private var controller: SubmissionsComposeController? = null
    private val mutableState = MutableStateFlow(feature.runtime.state.value)
    private val mutableEffects = MutableSharedFlow<SubmissionsRuntimeEffect>(extraBufferCapacity = 16)
    private val jobs = mutableListOf<Job>()
    private var observing = false
    val state: StateFlow<SubmissionsUiState> = mutableState.asStateFlow()
    val effects: SharedFlow<SubmissionsRuntimeEffect> = mutableEffects.asSharedFlow()

    fun start(): SubmissionsScrollRestoration? {
        if (!observing) {
            observing = true
            jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
                feature.events.collect { event ->
                    when (event) {
                        is SubmissionsFeatureSessionEvent.State -> {
                            mutableState.value = event.state
                            controller?.let { renderSubmissions(it, event.state) }
                        }
                        is SubmissionsFeatureSessionEvent.Runtime -> mutableEffects.emit(event.effect)
                    }
                }
            }
        }
        return feature.start()
    }

    fun dispose() { jobs.forEach(Job::cancel); feature.dispose() }

    fun createController(
        userName: String,
        displaySettings: StoryDisplaySettings,
    ): SubmissionsComposeController = SubmissionsComposeController(
        userName = userName,
        initialFilter = feature.runtime.state.value.filter,
        initialDisplaySettings = displaySettings,
        listener = object : SubmissionsComposeController.Listener {
            override fun onFilterSelected(filter: com.simon.harmonichackernews.presentation.SubmissionFilter) =
                feature.runtime.selectFilter(filter)
            override fun onRefresh() = feature.runtime.refresh()
            override fun onStoryLinkClick(story: Story) = feature.runtime.openStoryLink(story)
            override fun onStoryCommentsClick(story: Story) = feature.runtime.openStoryComments(story)
            override fun onCommentStoryClick(story: Story) = feature.runtime.openCommentMaster(story)
            override fun onCommentRepliesClick(story: Story) = feature.runtime.openCommentReplies(story)
            override fun onLoadMore() = feature.runtime.loadMore()
            override fun onScrollStateChanged(
                firstVisibleStoryPosition: Int,
                firstVisibleStoryTop: Int,
                appBarCollapsed: Boolean,
            ) = feature.runtime.recordScrollPosition(
                firstVisibleStoryPosition,
                firstVisibleStoryTop,
                appBarCollapsed,
            )
        },
    ).also { created ->
        controller = created
        renderSubmissions(created, mutableState.value)
        start()?.let {
            created.restoreScrollState(
                it.firstVisibleStoryPosition,
                it.firstVisibleStoryTop,
                it.appBarCollapsed,
            )
        }
    }

    private fun renderSubmissions(
        target: SubmissionsComposeController,
        state: SubmissionsUiState,
    ) {
        target.updateLoading(state.loading, state.showInitialLoading, state.refreshing)
        target.updateContent(
            state.items,
            state.filter,
            state.hasUnfilteredItems,
            state.canLoadMore,
            state.loadedSuccessfully,
            state.emptyText,
            state.revision,
        )
    }
}

/** Shared editor state/effect bridge; platform hosts only present native dialogs/navigation. */
class EditorScreenSession(
    private val scope: CoroutineScope,
    private val feature: EditorFeatureSession,
) {
    private val mutableSubmitting = MutableStateFlow(feature.isSubmitting)
    private val mutableResults = MutableSharedFlow<EditorWorkflowResult>(extraBufferCapacity = 8)
    private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        feature.events.collect { event ->
            when (event) {
                is EditorFeatureSessionEvent.Submitting -> mutableSubmitting.value = event.value
                is EditorFeatureSessionEvent.Result -> mutableResults.emit(event.value)
            }
        }
    }
    val submitting: StateFlow<Boolean> = mutableSubmitting.asStateFlow()
    val results: SharedFlow<EditorWorkflowResult> = mutableResults.asSharedFlow()

    fun submit(submission: EditorSubmission) = feature.submit(submission)
    fun respondToCaptcha(challenge: HackerNewsCaptchaChallenge, response: String) =
        feature.respondToCaptcha(challenge, response)
    fun cancelCaptcha() = feature.cancelCaptcha()
    fun dispose() = job.cancel()
}
