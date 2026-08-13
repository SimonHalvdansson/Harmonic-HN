package com.simon.harmonichackernews.ui.session

import com.simon.harmonichackernews.app.CommentsFeatureSession
import com.simon.harmonichackernews.app.CommentsFeatureSessionEvent
import com.simon.harmonichackernews.app.StoriesFeatureSession
import com.simon.harmonichackernews.app.StoriesFeatureSessionEvent
import com.simon.harmonichackernews.app.SubmissionsFeatureSession
import com.simon.harmonichackernews.app.SubmissionsFeatureSessionEvent
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesSettingsState
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsScreenState
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenState
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
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
        mutableState.value = StoriesScreenStateFactory.create(feature.runtime, platform())
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
        mutableState.value = CommentsScreenStateFactory.create(feature.runtime, platform())
    }

    fun onResume() { feature.onResume(); refreshPresentation() }
    fun dispose() { jobs.forEach(Job::cancel); feature.dispose() }
}

/** State/effect host for submissions; platform coordinators only translate navigation effects. */
class SubmissionsScreenSession(
    private val scope: CoroutineScope,
    private val feature: SubmissionsFeatureSession,
) {
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
                        is SubmissionsFeatureSessionEvent.State -> mutableState.value = event.state
                        is SubmissionsFeatureSessionEvent.Runtime -> mutableEffects.emit(event.effect)
                    }
                }
            }
        }
        return feature.start()
    }

    fun dispose() { jobs.forEach(Job::cancel); feature.dispose() }
}
