package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryFeedRepository
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.presentation.ArchiveUrlResolver
import com.simon.harmonichackernews.presentation.CommentMasterResolver
import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime
import com.simon.harmonichackernews.presentation.CommentsPresenter
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.CommentsStore
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesPresenter
import com.simon.harmonichackernews.presentation.StoriesSessionState
import com.simon.harmonichackernews.presentation.StoriesStore
import com.simon.harmonichackernews.presentation.SubmissionsFeatureRuntime
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import com.simon.harmonichackernews.presentation.EditorSubmissionWorkflow
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.settings.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.simon.harmonichackernews.presentation.SubmissionsRuntimeEffect
import com.simon.harmonichackernews.presentation.SubmissionsUiState
import com.simon.harmonichackernews.presentation.SubmissionsScrollRestoration
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.network.StoryLinkPreviewSession
import com.simon.harmonichackernews.settings.ReadingPreferences

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
fun HarmonicAppComposition.createStoriesStore(
    host: StoriesFeatureHost,
): StoriesStore {
    val featureScope = host.scope.childFeatureScope()
    val storyCacheRuntime = createStoryCacheRuntime(featureScope)
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = nowMillis,
        voteRequest = { id, direction -> hackerNewsUser.vote(id.toString(), direction) },
        favoriteRequest = hackerNewsUser::setFavorite,
    )
    val presenter = StoriesPresenter(
        scope = featureScope,
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
        scope = featureScope,
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
    return StoriesStore(
        scope = featureScope,
        sessionState = host.sessionState,
        presenter = presenter,
        runtime = runtime,
        storyCache = storyCacheRuntime,
        observeSavedItems = { emit -> savedItems.changes.collect { emit(it.source) } },
        observeStoryUpdates = { emit -> storyUpdates.updates.collect { emit(it) } },
    )
}

fun HarmonicAppComposition.createCommentsStore(
    host: CommentsFeatureHost,
): CommentsStore {
    val featureScope = host.scope.childFeatureScope()
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = nowMillis,
        voteRequest = { id, direction -> hackerNewsUser.vote(id.toString(), direction) },
        favoriteRequest = hackerNewsUser::setFavorite,
    )
    val presenter = CommentsPresenter(
        featureScope,
        host.sessionState,
        CommentThreadRepository(
            network.algoliaRepository,
            network.hackerNewsRepository,
            preloads = commentsPreloads,
        ),
        network.pollOptionsRepository,
        actions,
        hackerNewsUser,
    )
    val runtime = CommentsFeatureRuntime(
        scope = featureScope,
        sessionState = host.sessionState,
        presenter = presenter,
        nowMillis = nowMillis,
        archiveUrlResolver = ArchiveUrlResolver(network.linkPreviewRepository),
        userSettings = host.userSettings,
        loadContentFilters = contentFilters::load,
        accounts = host.platform.accounts,
        summarySettings = aiSummarySettings,
        localSummaryAvailable = { localSummaryCanAttempt },
        summaryRuntime = createStorySummaryRuntime(featureScope),
        hydrateCachedStory = storyCache::hydrateStory,
        loadCachedThread = storyCache::loadStoryPayload,
        storeCachedThread = storyCache::cacheStory,
        publishStoryUpdate = storyUpdates::publish,
        previewResourceService = previewResources,
        storyResourceTints = storyResourceTints,
    )
    return CommentsStore(featureScope, runtime)
}

private fun CoroutineScope.childFeatureScope(): CoroutineScope = CoroutineScope(
    coroutineContext + SupervisorJob(coroutineContext[Job]),
)

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

fun HarmonicAppComposition.createStoryLinkPreviewSession(
    scope: CoroutineScope,
    story: Story?,
    readingPreferences: ReadingPreferences,
    onPreviewChanged: () -> Unit,
): StoryLinkPreviewSession = StoryLinkPreviewSession(
    scope = scope,
    story = story,
    useCase = LinkPreviewUseCase(network.linkPreviewRepository),
    readingPreferences = readingPreferences,
    onPreviewChanged = onPreviewChanged,
)
