package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.network.StoryFeedRepository
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.presentation.ArchiveUrlResolver
import com.simon.harmonichackernews.presentation.CommentMasterResolver
import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime
import com.simon.harmonichackernews.presentation.CommentsPresenter
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesPresenter
import com.simon.harmonichackernews.presentation.StoriesSessionState
import com.simon.harmonichackernews.presentation.SubmissionsFeatureRuntime
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import com.simon.harmonichackernews.presentation.EditorSubmissionWorkflow
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesSettingsState
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.data.Story

sealed interface StoriesFeatureSessionEvent {
    data class Runtime(val effect: StoriesRuntimeEffect) : StoriesFeatureSessionEvent
    data class Settings(val state: StoriesSettingsState) : StoriesFeatureSessionEvent
    data object ContentChanged : StoriesFeatureSessionEvent
}

class StoriesFeatureSession internal constructor(
    val presenter: StoriesPresenter,
    val runtime: StoriesFeatureRuntime,
    private val scope: CoroutineScope,
    private val sessionState: StoriesSessionState,
    val storyCache: StoryCacheRuntime,
    private val observeSavedItems: suspend (suspend (SavedItemSource) -> Unit) -> Unit,
    private val observeStoryUpdates: suspend (suspend (Story) -> Unit) -> Unit,
) {
    private val mutableEvents = MutableSharedFlow<StoriesFeatureSessionEvent>(
        extraBufferCapacity = 64,
    )
    private val jobs = mutableListOf<Job>()
    private var started = false
    private var hostStarted = false
    val events: SharedFlow<StoriesFeatureSessionEvent> = mutableEvents.asSharedFlow()

    /** Starts retained state, settings, effects and saved-item synchronization exactly once. */
    fun start(): Boolean {
        if (started) return sessionState.initialized
        started = true
        runtime.initializeHistory()
        val restoring = sessionState.initialized
        runtime.storyResources?.setResourceChangedListener {
            mutableEvents.tryEmit(StoriesFeatureSessionEvent.ContentChanged)
        }
        runtime.initialize(restoring)
        jobs += scope.launch {
            runtime.effects.collect { mutableEvents.emit(StoriesFeatureSessionEvent.Runtime(it)) }
        }
        jobs += scope.launch {
            runtime.settingsState.collect {
                mutableEvents.emit(StoriesFeatureSessionEvent.Settings(it))
            }
        }
        jobs += scope.launch {
            observeSavedItems { source ->
                runtime.notifySavedItemsChanged(source)
                if (runtime.refreshBookmarksIfNeeded(hostStarted)) {
                    mutableEvents.emit(StoriesFeatureSessionEvent.ContentChanged)
                }
            }
        }
        jobs += scope.launch {
            storyCache.state.collect {
                mutableEvents.emit(StoriesFeatureSessionEvent.ContentChanged)
            }
        }
        jobs += scope.launch {
            observeStoryUpdates { story ->
                if (runtime.mergeExternalStoryUpdate(story)) {
                    mutableEvents.emit(StoriesFeatureSessionEvent.ContentChanged)
                }
            }
        }
        sessionState.initialized = true
        if (restoring) {
            mutableEvents.tryEmit(StoriesFeatureSessionEvent.ContentChanged)
            when {
                runtime.shouldRefreshRestoredState() ->
                    runtime.refresh(showSwipeRefreshIndicator = false)
                !presenter.state.value.searching -> runtime.resumeRetainedLoads()
            }
        } else {
            runtime.refresh(showSwipeRefreshIndicator = false)
        }
        return restoring
    }

    fun onStart() {
        hostStarted = true
        if (runtime.refreshBookmarksIfNeeded(hostStarted = true)) {
            mutableEvents.tryEmit(StoriesFeatureSessionEvent.ContentChanged)
        }
    }

    fun onStop() {
        hostStarted = false
    }

    fun onResume() {
        runtime.resume(hostStarted)
        mutableEvents.tryEmit(StoriesFeatureSessionEvent.ContentChanged)
    }

    fun dispose() {
        runtime.storyResources?.setResourceChangedListener(null)
        jobs.forEach(Job::cancel)
        jobs.clear()
        storyCache.dispose()
        runtime.dispose()
    }
}

sealed interface CommentsFeatureSessionEvent {
    data class Runtime(val effect: CommentsRuntimeEffect) : CommentsFeatureSessionEvent
    data class Settings(val state: CommentsSettingsState) : CommentsFeatureSessionEvent
}

class CommentsFeatureSession internal constructor(
    val presenter: CommentsPresenter,
    val runtime: CommentsFeatureRuntime,
    private val scope: CoroutineScope,
) {
    private val mutableEvents = MutableSharedFlow<CommentsFeatureSessionEvent>(
        extraBufferCapacity = 64,
    )
    private val jobs = mutableListOf<Job>()
    private var started = false
    val events: SharedFlow<CommentsFeatureSessionEvent> = mutableEvents.asSharedFlow()

    fun start(
        initialStory: com.simon.harmonichackernews.data.Story,
        showWebsite: Boolean,
        scrollToCommentId: Int,
        restoring: Boolean,
        restoredSorting: String?,
    ) {
        if (!started) {
            started = true
            jobs += scope.launch {
                runtime.effects.collect {
                    mutableEvents.emit(CommentsFeatureSessionEvent.Runtime(it))
                }
            }
            jobs += scope.launch {
                runtime.settingsState.collect { state ->
                    state?.let { mutableEvents.emit(CommentsFeatureSessionEvent.Settings(it)) }
                }
            }
        }
        runtime.initializeFromSettings(
            initialStory = initialStory,
            showWebsite = showWebsite,
            scrollToCommentId = scrollToCommentId,
            restoring = restoring,
            restoredSorting = restoredSorting,
        )
    }

    fun onResume() = runtime.resume()

    fun dispose() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        runtime.dispose()
    }
}

sealed interface SubmissionsFeatureSessionEvent {
    data class State(val state: SubmissionsUiState) : SubmissionsFeatureSessionEvent
    data class Runtime(val effect: SubmissionsRuntimeEffect) : SubmissionsFeatureSessionEvent
}

class SubmissionsFeatureSession internal constructor(
    val runtime: SubmissionsFeatureRuntime,
    private val scope: CoroutineScope,
) {
    private val mutableEvents = MutableSharedFlow<SubmissionsFeatureSessionEvent>(
        extraBufferCapacity = 64,
    )
    private val jobs = mutableListOf<Job>()
    val events: SharedFlow<SubmissionsFeatureSessionEvent> = mutableEvents.asSharedFlow()

    fun start(): SubmissionsScrollRestoration? {
        jobs += scope.launch {
            runtime.state.collect {
                mutableEvents.emit(SubmissionsFeatureSessionEvent.State(it))
            }
        }
        jobs += scope.launch {
            runtime.effects.collect {
                mutableEvents.emit(SubmissionsFeatureSessionEvent.Runtime(it))
            }
        }
        return runtime.initialize()
    }

    fun dispose() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        runtime.dispose()
    }
}

sealed interface EditorFeatureSessionEvent {
    data class Submitting(val value: Boolean) : EditorFeatureSessionEvent
    data class Result(val value: EditorWorkflowResult) : EditorFeatureSessionEvent
}

class EditorFeatureSession internal constructor(
    private val scope: CoroutineScope,
    private val workflow: EditorSubmissionWorkflow,
    private val mutableEvents: MutableSharedFlow<EditorFeatureSessionEvent>,
) {
    val events: SharedFlow<EditorFeatureSessionEvent> = mutableEvents.asSharedFlow()
    val isSubmitting: Boolean get() = workflow.isSubmitting

    fun submit(submission: EditorSubmission) {
        scope.launch { mutableEvents.emit(EditorFeatureSessionEvent.Result(workflow.submit(submission))) }
    }

    fun respondToCaptcha(challenge: HackerNewsCaptchaChallenge, response: String) {
        scope.launch {
            mutableEvents.emit(
                EditorFeatureSessionEvent.Result(workflow.respondToCaptcha(challenge, response)),
            )
        }
    }

    fun cancelCaptcha() {
        mutableEvents.tryEmit(EditorFeatureSessionEvent.Result(workflow.cancelCaptcha()))
    }
}

/** Lifecycle inputs supplied by a screen host. Portable services come from the app graph. */
data class StoriesFeatureHost(
    val scope: CoroutineScope,
    val sessionState: StoriesSessionState,
    val platform: StoriesPlatformDependencies,
    val userSettings: UserSettings,
)

data class CommentsFeatureHost(
    val scope: CoroutineScope,
    val sessionState: CommentsSessionState,
    val platform: CommentsPlatformDependencies,
    val userSettings: UserSettings,
)

/**
 * Application-scoped factories keep feature construction identical across Android, iOS and
 * desktop. Hosts supply only lifecycle scopes and facilities that are still genuinely native.
 */
fun HarmonicAppComposition.createStoriesFeatureSession(
    host: StoriesFeatureHost,
): StoriesFeatureSession {
    val storyCacheRuntime = createStoryCacheRuntime(host.scope)
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = nowMillis,
        voteRequest = { id, direction -> hackerNewsUser.vote(id.toString(), direction) },
        favoriteRequest = hackerNewsUser::setFavorite,
    )
    val presenter = StoriesPresenter(
        scope = host.scope,
        sessionState = host.sessionState,
        algoliaRepository = network.algoliaRepository,
        hackerNewsRepository = network.hackerNewsRepository,
        hackerNewsApi = network.hackerNewsApi,
        userItemsLoader = hackerNewsUser,
        savedItemsRepository = savedItems,
        storyFeedLoader = StoryFeedRepository(
            network.hackerNewsRepository,
            network.hackerNewsWebRepository,
        ),
        clickedStoryIds = { host.platform.history.load().map { it.id } },
        isStoryClicked = host.platform.history::contains,
        shouldHideClickedStories = { host.userSettings.story.hideClicked },
    )
    val runtime = StoriesFeatureRuntime(
        scope = host.scope,
        sessionState = host.sessionState,
        presenter = presenter,
        savedItems = savedItems,
        savedItemActions = actions,
        historyStore = host.platform.history,
        accounts = host.platform.accounts,
        connectivity = host.platform.connectivity,
        userSettings = host.userSettings,
        loadContentFilters = contentFilters::load,
        commentMasterResolver = CommentMasterResolver(network.hackerNewsRepository),
        nowMillis = nowMillis,
        hydrateCachedStory = storyCache::hydrateStory,
        loadCachedStories = storyCache::recentStories,
        hasCachedStories = storyCache::hasRecentStories,
        startStoryCache = { storyCacheRuntime.start(it) },
        previewResourceService = previewResources,
        storyResourceTints = storyResourceTints,
    )
    return StoriesFeatureSession(
        presenter = presenter,
        runtime = runtime,
        scope = host.scope,
        sessionState = host.sessionState,
        storyCache = storyCacheRuntime,
        observeSavedItems = { emit -> savedItems.changes.collect { emit(it.source) } },
        observeStoryUpdates = { emit -> storyUpdates.updates.collect { emit(it) } },
    )
}

fun HarmonicAppComposition.createCommentsFeatureSession(
    host: CommentsFeatureHost,
): CommentsFeatureSession {
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = nowMillis,
        voteRequest = { id, direction -> hackerNewsUser.vote(id.toString(), direction) },
        favoriteRequest = hackerNewsUser::setFavorite,
    )
    val presenter = CommentsPresenter(
        host.scope,
        host.sessionState,
        CommentThreadRepository(network.algoliaRepository, network.hackerNewsRepository),
        network.pollOptionsRepository,
        actions,
        hackerNewsUser,
    )
    val runtime = CommentsFeatureRuntime(
        scope = host.scope,
        sessionState = host.sessionState,
        presenter = presenter,
        nowMillis = nowMillis,
        archiveUrlResolver = ArchiveUrlResolver(network.linkPreviewRepository),
        userSettings = host.userSettings,
        loadContentFilters = contentFilters::load,
        accounts = host.platform.accounts,
        summarySettings = aiSummarySettings,
        localSummaryAvailable = { localSummaryCanAttempt },
        summaryRuntime = createStorySummaryRuntime(host.scope),
        hydrateCachedStory = storyCache::hydrateStory,
        loadCachedThread = storyCache::loadStoryPayload,
        storeCachedThread = { storyId, payload ->
            storyCache.repository.storeStory(storyId, payload, nowMillis())
        },
        publishStoryUpdate = storyUpdates::publish,
        previewResourceService = previewResources,
        storyResourceTints = storyResourceTints,
    )
    return CommentsFeatureSession(presenter, runtime, host.scope)
}

fun HarmonicAppComposition.createSubmissionsFeatureSession(
    scope: CoroutineScope,
    sessionState: SubmissionsSessionState,
    userSettings: UserSettings = this.userSettings,
): SubmissionsFeatureSession = SubmissionsFeatureSession(
    runtime = SubmissionsFeatureRuntime(
        scope = scope,
        sessionState = sessionState,
        commentMasterResolver = CommentMasterResolver(network.hackerNewsRepository),
        useIntegratedWebView = { userSettings.reading.integratedWebView },
    ),
    scope = scope,
)

fun HarmonicAppComposition.createEditorFeatureSession(
    scope: CoroutineScope,
    type: EditorType,
    itemId: Int,
    connectivity: ConnectivityService = platform.connectivity,
): EditorFeatureSession {
    val events = MutableSharedFlow<EditorFeatureSessionEvent>(extraBufferCapacity = 16)
    val workflow = EditorSubmissionWorkflow(
        type = type,
        itemId = itemId,
        service = hackerNewsUser,
        connectivity = connectivity,
        onSubmittingChanged = { events.tryEmit(EditorFeatureSessionEvent.Submitting(it)) },
    )
    return EditorFeatureSession(scope, workflow, events)
}
