package com.simon.harmonichackernews.ui.session

import com.simon.harmonichackernews.app.CommentsFeatureSession
import com.simon.harmonichackernews.app.CommentsFeatureSessionEvent
import com.simon.harmonichackernews.app.EditorFeatureSession
import com.simon.harmonichackernews.app.EditorFeatureSessionEvent
import com.simon.harmonichackernews.app.StoriesFeatureSession
import com.simon.harmonichackernews.app.StoriesFeatureSessionEvent
import com.simon.harmonichackernews.app.SubmissionsFeatureSession
import com.simon.harmonichackernews.app.SubmissionsFeatureSessionEvent
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesSettingsState
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsFeatureListener
import com.simon.harmonichackernews.ui.comments.CommentsScreenState
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.StoriesScreenState
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
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

/** UI-producing host shared by Android, iOS and desktop stories surfaces. */
class StoriesScreenSession(
    private val scope: CoroutineScope,
    private val feature: StoriesFeatureSession,
    private val platform: () -> StoriesPlatformPresentation,
) {
    private var controller: StoriesComposeController? = null
    private val mutableState = MutableStateFlow<StoriesScreenState?>(null)
    private val mutableEffects = MutableSharedFlow<StoriesRuntimeEffect>(extraBufferCapacity = 32)
    private val mutableSettings = MutableSharedFlow<StoriesSettingsState>(extraBufferCapacity = 8)
    private val jobs = mutableListOf<Job>()
    private var started = false
    val state: StateFlow<StoriesScreenState?> = mutableState.asStateFlow()
    val effects: SharedFlow<StoriesRuntimeEffect> = mutableEffects.asSharedFlow()
    val settings: SharedFlow<StoriesSettingsState> = mutableSettings.asSharedFlow()

    fun start(): Boolean {
        if (!started) {
            started = true
            jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
                feature.events.collect { event ->
                    when (event) {
                        is StoriesFeatureSessionEvent.Runtime -> mutableEffects.emit(event.effect)
                        is StoriesFeatureSessionEvent.Settings -> mutableSettings.emit(event.state)
                        StoriesFeatureSessionEvent.ContentChanged -> Unit
                    }
                    refreshPresentation()
                }
            }
        }
        return feature.start().also { refreshPresentation() }
    }

    fun refreshPresentation() {
        mutableState.value = StoriesScreenStateFactory.create(
            feature.runtime,
            platform(),
            feature.storyCache.state.value,
        ).also {
            controller?.updateContent(it)
        }
    }

    fun createController(
        defaultStoryHeightPx: Int,
        platformCallbacks: StoriesFeatureListener.PlatformCallbacks,
    ): StoriesComposeController = StoriesComposeController.create(
        defaultStoryHeightPx,
        feature.runtime.savedItemActions,
        StoriesFeatureListener(feature.runtime, platformCallbacks),
    ).also {
        controller = it
        mutableState.value?.let(it::updateContent)
    }

    fun onStart() = feature.onStart()
    fun onStop() = feature.onStop()
    fun onResume() { feature.onResume(); refreshPresentation() }
    fun dispose() { jobs.forEach(Job::cancel); feature.dispose() }
}

/** UI-producing host shared by Android, iOS and desktop comments surfaces. */
class CommentsScreenSession(
    private val scope: CoroutineScope,
    private val feature: CommentsFeatureSession,
    private val platform: () -> CommentsPlatformPresentation,
) {
    private var controller: CommentsComposeController? = null
    private val mutableState = MutableStateFlow<CommentsScreenState?>(null)
    private val mutableEffects = MutableSharedFlow<CommentsRuntimeEffect>(extraBufferCapacity = 32)
    private val mutableSettings = MutableSharedFlow<CommentsSettingsState>(extraBufferCapacity = 8)
    private val jobs = mutableListOf<Job>()
    private var observing = false
    val state: StateFlow<CommentsScreenState?> = mutableState.asStateFlow()
    val effects: SharedFlow<CommentsRuntimeEffect> = mutableEffects.asSharedFlow()
    val settings: SharedFlow<CommentsSettingsState> = mutableSettings.asSharedFlow()

    fun start(
        initialStory: Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        restoring: Boolean,
        restoredSorting: String?,
    ) {
        if (!observing) {
            observing = true
            jobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
                feature.events.collect { event ->
                    when (event) {
                        is CommentsFeatureSessionEvent.Runtime -> mutableEffects.emit(event.effect)
                        is CommentsFeatureSessionEvent.Settings -> mutableSettings.emit(event.state)
                    }
                    refreshPresentation()
                }
            }
        }
        feature.start(initialStory, showWebsite, scrollToCommentId, restoring, restoredSorting)
        refreshPresentation()
    }

    fun refreshPresentation() {
        val state = CommentsScreenStateFactory.create(feature.runtime, platform())
        mutableState.value = state
        state?.let { controller?.updateContent(it) }
    }

    fun createController(
        story: Story,
        showWebsite: Boolean,
        platformCallbacks: CommentsFeatureListener.PlatformCallbacks,
    ): CommentsComposeController = CommentsComposeController.create(
        shouldSmoothScroll = { feature.runtime.settingsState.value?.smoothScroll ?: true },
        story = story,
        showWebsite = showWebsite,
        accountUser = feature.runtime.accountUser,
        savedItemState = feature.presenter.savedItemState,
        listener = CommentsFeatureListener(feature.runtime, platformCallbacks),
    ).also {
        controller = it
        mutableState.value?.let(it::updateContent)
    }

    fun onResume() { feature.onResume(); refreshPresentation() }
    fun dispose() { jobs.forEach(Job::cancel); feature.dispose() }
}

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
