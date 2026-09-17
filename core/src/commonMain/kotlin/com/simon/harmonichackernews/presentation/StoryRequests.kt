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

sealed interface StoriesEffect {
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
 * Loads story data for [StoriesFeatureRuntime]. Feed requests return directly to their caller;
 * row retries and saved-item synchronization publish asynchronous results.
 *
 * Feed selection and retained state belong to [StoriesSessionState]; list and search state stay in
 * their own stores. This class owns cancellable requests, not another copy of feature state.
 */
class StoryRequests(
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

    fun configureVisibility(filters: ContentFilters, hideJobs: Boolean): Boolean =
        storyVisibilityPolicy.update(filters, hideJobs)

    fun shouldHideStory(story: Story, type: StoryType): Boolean =
        storyVisibilityPolicy.shouldHide(story, type)
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

    fun loadStoryRow(story: Story, preserveTime: Boolean, generation: Int) {
        storyRowLoader.load(story, preserveTime, generation)
    }

    suspend fun loadFeed(storyType: StoryType, frontDay: String?): StoryFeedResult =
        storyFeedLoader.load(storyType, frontDay)

    suspend fun loadNextScrapedPage(storyType: StoryType, nextPageUrl: String): HackerNewsListPage =
        storyFeedLoader.loadNextScrapedPage(storyType, nextPageUrl)

    fun cancelUserItemsLoad() {
        userItemsLoadJob?.cancel()
        userItemsLoadJob = null
    }

    fun syncUserItems(source: SavedItemSource, generation: Int, savedAtMillis: Long) {
        userItemsLoadJob?.cancel()
        userItemsLoadJob = scope.launch {
            val upvoted = source == SavedItemSource.UPVOTED
            val path = if (upvoted) "upvoted" else "favorites"
            try {
                when (val result = userItemsLoader.getUserItems(path, loginRequired = upvoted)) {
                    is HackerNewsUserItemsResult.Success -> {
                        val snapshot = SavedItemSnapshots.normalize(
                            result.items.itemIds,
                            result.items.commentIds,
                        )
                        if (savedItemsRepository.loadSnapshot(source) != snapshot) {
                            savedItemsRepository.saveSnapshotAtomic(
                                source,
                                snapshot,
                                savedAtMillis,
                            )
                        }
                        mutableEffects.emit(
                            StoriesEffect.UserItemsSynced(
                                source,
                                generation,
                                snapshot,
                            ),
                        )
                    }
                    is HackerNewsUserItemsResult.Failure -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = source,
                            generation = generation,
                            summary = result.summary,
                            detail = result.detail,
                        ),
                    )
                    is HackerNewsUserItemsResult.Captcha -> mutableEffects.emit(
                        StoriesEffect.UserItemsSyncFailed(
                            source = source,
                            generation = generation,
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
                        source = source,
                        generation = generation,
                        summary = "Couldn't sync $path",
                        detail = error.message,
                        cause = error,
                    ),
                )
            }
        }
    }

    fun beginStoryLoadGeneration(): Int {
        cancelUserItemsLoad()
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
