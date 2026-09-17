package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemSnapshots
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsUserItemsLoader
import com.simon.harmonichackernews.network.HackerNewsUserItemsResult
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.StoryFeedLoader
import com.simon.harmonichackernews.settings.ContentFilters
import com.simon.harmonichackernews.network.StoryFeedResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

enum class StoryListTarget { MAIN, SEARCH }

sealed interface StoriesAction {
    data class SelectStoryLink(
        val story: Story,
        val alwaysOpenComments: Boolean,
        val useIntegratedWebView: Boolean,
    ) : StoriesAction
    data class SelectStoryComments(val story: Story) : StoriesAction
    data class LoadFeed(
        val storyType: StoryType,
        val frontDay: String?,
        val generation: Int,
    ) : StoriesAction
    data class LoadNextScrapedPage(
        val storyType: StoryType,
        val nextPageUrl: String,
        val generation: Int,
    ) : StoriesAction
    data object CancelFeedLoads : StoriesAction
    data class LoadStoryRow(
        val story: Story,
        val preserveTime: Boolean,
        val generation: Int,
    ) : StoriesAction
    data class SyncUserItems(
        val source: SavedItemSource,
        val generation: Int,
        val savedAtMillis: Long,
    ) : StoriesAction
}

sealed interface StoriesEffect {
    data class OpenComments(
        val story: Story,
        val showWebsite: Boolean,
    ) : StoriesEffect

    data class OpenExternalStory(
        val story: Story,
        val url: String,
    ) : StoriesEffect

    data class RetryStory(val story: Story) : StoriesEffect
    data class FeedLoaded(
        val storyType: StoryType,
        val generation: Int,
        val result: StoryFeedResult,
    ) : StoriesEffect
    data class FeedFailed(
        val storyType: StoryType,
        val generation: Int,
        val cause: Throwable,
    ) : StoriesEffect
    data class NextScrapedPageLoaded(
        val storyType: StoryType,
        val generation: Int,
        val page: HackerNewsListPage,
    ) : StoriesEffect
    data class NextScrapedPageFailed(
        val storyType: StoryType,
        val generation: Int,
        val cause: Throwable,
    ) : StoriesEffect
    data class StoryRowLoaded(
        val story: Story,
        val generation: Int,
    ) : StoriesEffect
    data class StoryRowRejected(
        val story: Story,
        val generation: Int,
    ) : StoriesEffect
    data class StoryRowLoadAttemptFailed(
        val story: Story,
        val generation: Int,
        val attempt: Int,
        val finalAttempt: Boolean,
        val cause: Throwable,
    ) : StoriesEffect
    data class UserItemsSynced(
        val source: SavedItemSource,
        val generation: Int,
        val snapshot: SavedItemSnapshot,
    ) : StoriesEffect
    data class UserItemsSyncFailed(
        val source: SavedItemSource,
        val generation: Int,
        val summary: String,
        val detail: String? = null,
        val cause: Throwable? = null,
    ) : StoriesEffect
}

/**
 * Executes story requests and emits their results for [StoriesFeatureRuntime] to apply.
 *
 * Feed selection and retained state belong to [StoriesSessionState]; list and search state stay in
 * their own stores. This class owns cancellable requests, not another copy of feature state.
 */
class StoriesPresenter(
    private val scope: CoroutineScope,
    private val sessionState: StoriesSessionState,
    algoliaRepository: AlgoliaRepository,
    hackerNewsRepository: HackerNewsRepository,
    hackerNewsApi: HackerNewsApi,
    private val userItemsLoader: HackerNewsUserItemsLoader,
    private val savedItemsRepository: SavedItemsRepository,
    private val storyFeedLoader: StoryFeedLoader,
    clickedStoryIds: () -> List<Int>,
    isStoryClicked: (Int) -> Boolean,
    shouldHideClickedStories: () -> Boolean,
) {
    private val storyVisibilityPolicy = StoryVisibilityPolicy()
    val searchStore = StorySearchStore(
        scope = scope,
        algoliaRepository = algoliaRepository,
        hackerNewsRepository = hackerNewsRepository,
        clickedStoryIds = clickedStoryIds,
        isStoryClicked = isStoryClicked,
        shouldFilterStory = { story ->
            storyVisibilityPolicy.shouldHide(
                story,
                if (sessionState.searching) {
                    sessionState.searchStoryType
                } else {
                    sessionState.mainStoryType
                },
            )
        },
        shouldHideClickedStories = shouldHideClickedStories,
    )

    private val mutableEffects = MutableSharedFlow<StoriesEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<StoriesEffect> = mutableEffects.asSharedFlow()
    private var feedLoadJob: Job? = null

    fun configureVisibility(filters: ContentFilters, hideJobs: Boolean): Boolean =
        storyVisibilityPolicy.update(filters, hideJobs)

    fun shouldHideStory(story: Story, type: StoryType): Boolean =
        storyVisibilityPolicy.shouldHide(story, type)
    private var nextScrapedPageJob: Job? = null
    private var userItemsLoadJob: Job? = null
    private val storyRowLoader = StoryRowLoadOrchestrator(
        scope = scope,
        hackerNewsApi = hackerNewsApi,
        staleLoadMillis = STORY_ROW_STALE_MILLIS,
        nowMillis = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    )

    init {
        searchStore.restoreOptions(sessionState.searchOptions)
        scope.launch { storyRowLoader.effects.collect(::applyStoryRowLoadEffect) }
    }

    fun dispatch(intent: StoriesAction) {
        val action = intent
        when (action) {
            is StoriesAction.SelectStoryLink -> selectStoryLink(action)
            is StoriesAction.SelectStoryComments -> selectStoryComments(action)
            is StoriesAction.LoadFeed -> loadFeed(action)
            is StoriesAction.LoadNextScrapedPage -> loadNextScrapedPage(action)
            StoriesAction.CancelFeedLoads -> cancelFeedLoads()
            is StoriesAction.LoadStoryRow -> storyRowLoader.load(
                story = action.story,
                preserveTime = action.preserveTime,
                requestGeneration = action.generation,
            )
            is StoriesAction.SyncUserItems -> syncUserItems(action)
        }
    }

    private fun selectStoryLink(action: StoriesAction.SelectStoryLink) {
        val story = action.story
        val effect = when {
            !story.loaded && story.loadingFailed -> StoriesEffect.RetryStory(story)
            !story.loaded -> null
            story.isFrontpageLink -> story.url?.let {
                StoriesEffect.OpenExternalStory(story, it)
            }
            action.alwaysOpenComments ->
                StoriesEffect.OpenComments(story, showWebsite = false)
            story.isLink && action.useIntegratedWebView ->
                StoriesEffect.OpenComments(story, showWebsite = true)
            story.isLink -> story.url?.let {
                StoriesEffect.OpenExternalStory(story, it)
            }
            else -> StoriesEffect.OpenComments(story, showWebsite = false)
        }
        effect?.let(mutableEffects::tryEmit)
    }

    private fun selectStoryComments(action: StoriesAction.SelectStoryComments) {
        if (!action.story.loaded) return
        val effect = if (action.story.isFrontpageLink) {
            action.story.url?.let {
                StoriesEffect.OpenExternalStory(action.story, it)
            }
        } else {
            StoriesEffect.OpenComments(action.story, showWebsite = false)
        }
        effect?.let(mutableEffects::tryEmit)
    }

    private fun loadFeed(action: StoriesAction.LoadFeed) {
        feedLoadJob?.cancel()
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = null
        feedLoadJob = scope.launch {
            try {
                mutableEffects.emit(
                    StoriesEffect.FeedLoaded(
                        action.storyType,
                        action.generation,
                        storyFeedLoader.load(action.storyType, action.frontDay),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.FeedFailed(action.storyType, action.generation, error),
                )
            }
        }
    }

    private fun loadNextScrapedPage(action: StoriesAction.LoadNextScrapedPage) {
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = scope.launch {
            try {
                mutableEffects.emit(
                    StoriesEffect.NextScrapedPageLoaded(
                        action.storyType,
                        action.generation,
                        storyFeedLoader.loadNextScrapedPage(
                            action.storyType,
                            action.nextPageUrl,
                        ),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.NextScrapedPageFailed(
                        action.storyType,
                        action.generation,
                        error,
                    ),
                )
            }
        }
    }

    private fun cancelFeedLoads() {
        feedLoadJob?.cancel()
        feedLoadJob = null
        nextScrapedPageJob?.cancel()
        nextScrapedPageJob = null
        userItemsLoadJob?.cancel()
        userItemsLoadJob = null
    }

    private fun syncUserItems(action: StoriesAction.SyncUserItems) {
        userItemsLoadJob?.cancel()
        userItemsLoadJob = scope.launch {
            val upvoted = action.source == SavedItemSource.UPVOTED
            val path = if (upvoted) "upvoted" else "favorites"
            try {
                when (val result = userItemsLoader.getUserItems(path, loginRequired = upvoted)) {
                    is HackerNewsUserItemsResult.Success -> {
                        val snapshot = SavedItemSnapshots.normalize(
                            result.items.itemIds,
                            result.items.commentIds,
                        )
                        if (savedItemsRepository.loadSnapshot(action.source) != snapshot) {
                            savedItemsRepository.saveSnapshotAtomic(
                                action.source,
                                snapshot,
                                action.savedAtMillis,
                            )
                        }
                        mutableEffects.emit(
                            StoriesEffect.UserItemsSynced(
                                action.source,
                                action.generation,
                                snapshot,
                            ),
                        )
                    }
                    is HackerNewsUserItemsResult.Failure -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = action.source,
                            generation = action.generation,
                            summary = result.summary,
                            detail = result.detail,
                        ),
                    )
                    is HackerNewsUserItemsResult.Captcha -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = action.source,
                            generation = action.generation,
                            summary = "Captcha required",
                            detail = "HN asked for a captcha before syncing $path.",
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mutableEffects.emit(
                    StoriesEffect.UserItemsSyncFailed(
                        source = action.source,
                        generation = action.generation,
                        summary = "Couldn't sync $path",
                        detail = error.message,
                        cause = error,
                    ),
                )
            }
        }
    }

    fun beginStoryLoadGeneration(): Int {
        cancelFeedLoads()
        return storyRowLoader.beginGeneration()
    }

    val storyLoadGeneration: Int get() = storyRowLoader.generation

    fun isCurrentStoryLoadGeneration(generation: Int): Boolean =
        storyRowLoader.isCurrent(generation)

    fun isStoryRowLoadInProgress(storyId: Int): Boolean =
        storyRowLoader.isInProgress(storyId)

    fun cancelStoryRowLoad(storyId: Int) = storyRowLoader.cancel(storyId)

    fun clearStoryRowLoads() = storyRowLoader.clear()

    private suspend fun applyStoryRowLoadEffect(effect: StoryRowLoadEffect) {
        mutableEffects.emitAsStoriesEffect(effect)
    }

    private suspend fun MutableSharedFlow<StoriesEffect>.emitAsStoriesEffect(
        effect: StoryRowLoadEffect,
    ) {
        emit(
            when (effect) {
                is StoryRowLoadEffect.Loaded ->
                    StoriesEffect.StoryRowLoaded(effect.story, effect.generation)
                is StoryRowLoadEffect.Rejected ->
                    StoriesEffect.StoryRowRejected(effect.story, effect.generation)
                is StoryRowLoadEffect.AttemptFailed ->
                    StoriesEffect.StoryRowLoadAttemptFailed(
                        story = effect.story,
                        generation = effect.generation,
                        attempt = effect.attempt,
                        finalAttempt = effect.finalAttempt,
                        cause = effect.cause,
                    )
            },
        )
    }

    private companion object {
        const val STORY_ROW_STALE_MILLIS = 30_000L
    }
}
