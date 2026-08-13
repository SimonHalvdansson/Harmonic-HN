package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadRepository
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
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import kotlinx.coroutines.CoroutineScope

data class StoriesFeatureSession(
    val presenter: StoriesPresenter,
    val runtime: StoriesFeatureRuntime,
)

data class CommentsFeatureSession(
    val presenter: CommentsPresenter,
    val runtime: CommentsFeatureRuntime,
)

/** Portable host hooks that cannot be supplied by the shared application graph yet. */
data class StoriesFeatureHost(
    val scope: CoroutineScope,
    val sessionState: StoriesSessionState,
    val platform: StoriesPlatformDependencies,
    val userSettings: UserSettings,
    val nowMillis: () -> Long,
    val hydrateCachedStory: (Story) -> Boolean,
    val loadCachedStories: () -> List<Story>,
    val hasCachedStories: () -> Boolean,
)

data class CommentsFeatureHost(
    val scope: CoroutineScope,
    val sessionState: CommentsSessionState,
    val platform: CommentsPlatformDependencies,
    val userSettings: UserSettings,
    val nowMillis: () -> Long,
    val summaryRuntime: StorySummaryRuntime,
    val localSummaryAvailable: () -> Boolean,
    val hydrateCachedStory: (Story) -> Boolean,
    val loadCachedThread: (Int) -> String?,
    val storeCachedThread: (Int, String) -> Unit,
)

/**
 * Application-scoped factories keep feature construction identical across Android, iOS and
 * desktop. Hosts supply only lifecycle scopes and facilities that are still genuinely native.
 */
fun HarmonicAppComposition.createStoriesFeatureSession(
    host: StoriesFeatureHost,
): StoriesFeatureSession {
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = host.nowMillis,
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
        nowMillis = host.nowMillis,
        hydrateCachedStory = host.hydrateCachedStory,
        loadCachedStories = host.loadCachedStories,
        hasCachedStories = host.hasCachedStories,
        previewResourceService = previewResources,
        storyResourceTints = storyResourceTints,
    )
    return StoriesFeatureSession(presenter, runtime)
}

fun HarmonicAppComposition.createCommentsFeatureSession(
    host: CommentsFeatureHost,
): CommentsFeatureSession {
    val actions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = host.nowMillis,
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
        nowMillis = host.nowMillis,
        archiveUrlResolver = ArchiveUrlResolver(network.linkPreviewRepository),
        userSettings = host.userSettings,
        loadContentFilters = contentFilters::load,
        accounts = host.platform.accounts,
        summarySettings = aiSummarySettings,
        localSummaryAvailable = host.localSummaryAvailable,
        summaryRuntime = host.summaryRuntime,
        hydrateCachedStory = host.hydrateCachedStory,
        loadCachedThread = host.loadCachedThread,
        storeCachedThread = host.storeCachedThread,
        previewResourceService = previewResources,
        storyResourceTints = storyResourceTints,
    )
    return CommentsFeatureSession(presenter, runtime)
}

fun HarmonicAppComposition.createSubmissionsFeatureRuntime(
    scope: CoroutineScope,
    sessionState: SubmissionsSessionState,
    userSettings: UserSettings = this.userSettings,
): SubmissionsFeatureRuntime = SubmissionsFeatureRuntime(
    scope = scope,
    sessionState = sessionState,
    commentMasterResolver = CommentMasterResolver(network.hackerNewsRepository),
    useIntegratedWebView = { userSettings.reading.integratedWebView },
)

fun HarmonicAppComposition.createEditorWorkflow(
    type: EditorType,
    itemId: Int,
    titleMaxLength: Int,
    onSubmittingChanged: (Boolean) -> Unit,
    connectivity: ConnectivityService = platform.capabilities.connectivity.requireService(),
): EditorSubmissionWorkflow = EditorSubmissionWorkflow(
    type = type,
    itemId = itemId,
    titleMaxLength = titleMaxLength,
    service = hackerNewsUser,
    connectivity = connectivity,
    onSubmittingChanged = onSubmittingChanged,
)
