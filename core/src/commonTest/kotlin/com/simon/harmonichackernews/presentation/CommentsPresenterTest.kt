package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PollOption
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.CommentsPreloadRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsVotingService
import com.simon.harmonichackernews.network.HttpStatusException
import com.simon.harmonichackernews.network.OfficialCommentThreadLoader
import com.simon.harmonichackernews.network.PollOptionsLoader
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.settings.AiSummaryPreferenceKeys
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.TestCredentialStore
import com.simon.harmonichackernews.settings.TestKeyValueStore
import com.simon.harmonichackernews.summary.LOCAL_SUMMARY_ARTICLE_TOO_SHORT
import com.simon.harmonichackernews.summary.SUMMARY_ARTICLE_HTTP_UNAUTHORIZED
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsPresenterTest {
    @Test
    fun localTooShortFailureRequestsPageTextOnlyOnce() = runTest {
        val session = CommentsSessionState()
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val settings = AiSummarySettingsRepository(
            TestKeyValueStore(
                mapOf(
                    AiSummaryPreferenceKeys.ENABLED to true,
                    AiSummaryPreferenceKeys.MODE to AiSummaryMode.LOCAL.storedValue,
                ),
            ),
            TestCredentialStore(),
            flowOf(),
        )
        var attempts = 0
        val tooShortBackend = StorySummaryBackend {
            flow {
                attempts++
                emit(StorySummaryEvent.Failure(LOCAL_SUMMARY_ARTICLE_TOO_SHORT))
            }
        }
        val runtime = CommentsFeatureRuntime(
            scope = backgroundScope,
            sessionState = session,
            presenter = presenter,
            summarySettings = settings,
            localSummaryAvailable = { true },
            summaryRuntime = StorySummaryRuntime(backgroundScope, tooShortBackend, tooShortBackend),
            canLoadArticleTextOnDemand = true,
            nowMillis = { 0L },
        )
        val story = Story().apply {
            id = 1
            url = "https://example.com/article"
            isLink = true
        }
        runtime.initialize(story, false, -1, "Default", restoring = false)
        val effects = mutableListOf<CommentsRuntimeEffect>()
        backgroundScope.launch { runtime.effects.collect(effects::add) }
        runCurrent()

        runtime.startSummary(null)
        runCurrent()

        assertTrue(runtime.summaryLoading)
        assertEquals(null, story.summary)
        assertEquals(1, effects.count { it == CommentsRuntimeEffect.RequestSummaryPageTextRetry })

        runtime.startSummary("text extracted from the loaded WebView")
        runCurrent()

        assertFalse(runtime.summaryLoading)
        assertEquals(2, attempts)
        assertEquals(1, effects.count { it == CommentsRuntimeEffect.RequestSummaryPageTextRetry })
    }

    @Test
    fun localHttp401ExtractionFailureRequestsPageTextOnlyOnce() = runTest {
        val session = CommentsSessionState()
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val settings = AiSummarySettingsRepository(
            TestKeyValueStore(
                mapOf(
                    AiSummaryPreferenceKeys.ENABLED to true,
                    AiSummaryPreferenceKeys.MODE to AiSummaryMode.LOCAL.storedValue,
                ),
            ),
            TestCredentialStore(),
            flowOf(),
        )
        var attempts = 0
        val unauthorizedBackend = StorySummaryBackend {
            flow {
                attempts++
                emit(StorySummaryEvent.Failure(SUMMARY_ARTICLE_HTTP_UNAUTHORIZED))
            }
        }
        val runtime = CommentsFeatureRuntime(
            scope = backgroundScope,
            sessionState = session,
            presenter = presenter,
            summarySettings = settings,
            localSummaryAvailable = { true },
            summaryRuntime = StorySummaryRuntime(
                backgroundScope,
                unauthorizedBackend,
                unauthorizedBackend,
            ),
            canLoadArticleTextOnDemand = true,
            nowMillis = { 0L },
        )
        val story = Story().apply {
            id = 1
            url = "https://example.com/requires-browser"
            isLink = true
        }
        runtime.initialize(story, false, -1, "Default", restoring = false)
        val effects = mutableListOf<CommentsRuntimeEffect>()
        backgroundScope.launch { runtime.effects.collect(effects::add) }
        runCurrent()

        runtime.startSummary(null)
        runCurrent()

        assertTrue(runtime.summaryLoading)
        assertEquals(null, story.summary)
        assertEquals(1, effects.count { it == CommentsRuntimeEffect.RequestSummaryPageTextRetry })

        runtime.startSummary("text extracted from the loaded WebView")
        runCurrent()

        assertFalse(runtime.summaryLoading)
        assertEquals(2, attempts)
        assertEquals(1, effects.count { it == CommentsRuntimeEffect.RequestSummaryPageTextRetry })
    }

    @Test
    fun bookmarkTogglePublishesSavedItemStateChange() = runTest {
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val initialState = presenter.state.value

        presenter.dispatch(CommentsAction.ToggleBookmark(42))

        assertTrue(presenter.savedItemState.isBookmarked(42))
        assertEquals(initialState.savedItemRevision + 1L, presenter.state.value.savedItemRevision)

        presenter.dispatch(CommentsAction.ToggleBookmark(42))

        assertFalse(presenter.savedItemState.isBookmarked(42))
        assertEquals(initialState.savedItemRevision + 2L, presenter.state.value.savedItemRevision)
    }

    @Test
    fun scrollPositionIntentUpdatesSessionWithoutPublishingCommentsState() = runTest {
        val session = CommentsSessionState()
        val story = Story("Shared", 42, true, false)
        session.story = story
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val runtime = CommentsFeatureRuntime(backgroundScope, session, presenter) { 123L }
        val store = CommentsStore(backgroundScope, runtime)
        val initialState = store.state.value
        val emissions = mutableListOf<CommentsState>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.state.collect(emissions::add)
        }
        runCurrent()

        // Make a rebuilt snapshot observably different so this test catches an accidental publish,
        // rather than relying on StateFlow to conflate equal state values.
        story.title = "Changed behind the published snapshot"
        store.accept(CommentsIntent.RecordScrollPosition(commentId = 7, offset = 24))
        runCurrent()

        assertSame(initialState, store.state.value)
        assertEquals(1, emissions.size)
        assertTrue(session.scrollProgress.initialized)
        assertEquals(story.id, session.scrollProgress.storyId)
        assertEquals(7, session.scrollProgress.topCommentId)
        assertEquals(-24, session.scrollProgress.topCommentOffset)
        collection.cancel()
    }

    @Test
    fun storyPresentationRefreshPublishesExternallyMutatedPreviewState() = runTest {
        val session = CommentsSessionState()
        val story = Story("Shared", 42, true, false)
        session.story = story
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val store = CommentsStore(
            backgroundScope,
            CommentsFeatureRuntime(backgroundScope, session, presenter) { 123L },
        )

        assertFalse(store.state.value.story!!.linkPreviewLoading)

        story.linkPreviewLoading = true
        store.refreshStoryPresentation()

        assertTrue(store.state.value.story!!.linkPreviewLoading)
    }

    @Test
    fun featureRuntimeOwnsInitializationSearchAndPlatformDecisionRouting() = runTest {
        val session = CommentsSessionState()
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val runtime = CommentsFeatureRuntime(backgroundScope, session, presenter) { 123L }
        val story = Story("Shared", 42, true, false)
        runtime.initialize(story, showWebsite = true, scrollToCommentId = 7, "new", false)

        assertEquals(story, runtime.story)
        assertEquals("new", runtime.thread.state.value.sorting)
        assertTrue(session.showWebsite)
        assertEquals(7, session.scrollToCommentId)

        runtime.setSearchQuery("kotlin")
        assertEquals("kotlin", runtime.thread.state.value.searchQuery)

        val effect = async {
            runtime.effects.filterIsInstance<CommentsRuntimeEffect.Platform>().first()
        }
        runCurrent()
        runtime.header(CommentsHeaderAction.REPLY)
        assertIs<CommentsPlatformEffect.RequestLogin>(effect.await().effect)
    }

    @Test
    fun featureRuntimeConsumesRouteTargetAndRestoresPortableThreadProgress() = runTest {
        val session = CommentsSessionState()
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val runtime = CommentsFeatureRuntime(backgroundScope, session, presenter) { 123L }
        val story = Story("Shared", 42, true, false)
        val parent = Comment().also {
            it.id = 7
            it.parent = story.id
            it.expanded = false
        }
        val child = Comment().also {
            it.id = 8
            it.parent = parent.id
        }
        runtime.initialize(story, false, child.id, "new", false)
        runtime.thread.appendLoadedComments(story, listOf(parent, child), "new", false)

        assertEquals(CommentTargetResolution.Found(child.id), runtime.consumeCommentTarget())
        assertTrue(parent.expanded)
        assertEquals(-1, session.scrollToCommentId)

        session.scrollProgress.apply {
            initialized = true
            storyId = story.id
            topCommentId = child.id
            topCommentOffset = -24
            collapsedIDs += parent.id
        }
        val restoration = runtime.restoreScrollProgress()

        assertEquals(CommentsScrollRestoration(child.id, -24), restoration)
        assertFalse(parent.expanded)
    }

    @Test
    fun algoliaLoadAppliesThreadStateBeforeEmittingPlatformWork() = runTest {
        val response = """
            {
              "id": 42,
              "title": "Shared comments",
              "points": 10,
              "author": "simon",
              "type": "story",
              "children": [
                {"id": 7, "parent_id": 42, "author": "alice", "text": "Hello"}
              ]
            }
        """.trimIndent()
        val repository = CommentThreadRepository(
            algoliaRepository = FakeAlgoliaRepository(response),
            hackerNewsRepository = UnusedHackerNewsRepository,
            algoliaCommentsParser = AlgoliaCommentsParser(
                parsingDispatcher = UnconfinedTestDispatcher(testScheduler),
            ),
        )
        val session = CommentsSessionState()
        val story = Story("Loading", 42, false, false)
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            repository,
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(CommentsAction.BeginThreadLoad(nowMillis = 100))
        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = true,
                filteredUsers = emptySet(),
                sorting = "default",
                collapseTopLevel = false,
                previousResponse = null,
                restoreScrollFromCache = false,
            ),
        )
        val applied = assertIs<CommentsEffect.ThreadApplied>(effect.await())

        assertEquals("Shared comments", story.title)
        assertEquals(listOf(7), presenter.thread.state.value.allComments.drop(1).map { it.comment.id })
        assertTrue(presenter.state.value.loaded)
        assertEquals(null, presenter.state.value.failure)
        assertTrue(applied.contentApplied)
        assertTrue(applied.networkCompleted)
        assertEquals(response, applied.responseToCache)
        assertTrue(applied.broadcastStoryUpdate)
    }

    @Test
    fun algoliaFallbackStateIsPublishedBeforeOfficialApiCompletes() = runTest {
        val officialStory = CompletableDeferred<Story?>()
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = object : AlgoliaRepository {
                    override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
                        error("Not used")

                    override suspend fun search(url: String): List<Story> = error("Not used")

                    override suspend fun getItemJson(id: Int): String =
                        throw HttpStatusException(503, "Unavailable", "https://hn.algolia.com")
                },
                hackerNewsRepository = object : HackerNewsRepository {
                    override suspend fun getStory(id: Int): Story? = officialStory.await()
                    override suspend fun getComment(id: Int): Comment? = error("Not used")
                    override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
                },
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val story = Story("Loading", 42, false, false)

        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = true,
                filteredUsers = emptySet(),
                sorting = "default",
                collapseTopLevel = false,
                previousResponse = null,
                restoreScrollFromCache = false,
            ),
        )
        runCurrent()

        assertTrue(presenter.state.value.usingOfficialApiFallback)
        assertFalse(presenter.state.value.loaded)

        officialStory.complete(Story("Official", 42, false, false))
        runCurrent()

        assertTrue(presenter.state.value.usingOfficialApiFallback)
        assertTrue(presenter.state.value.loaded)
    }

    @Test
    fun preparedAlgoliaThreadOpensWithoutASecondNetworkRequest() = runTest {
        val response = """
            {
              "id": 42,
              "title": "Prepared comments",
              "points": 10,
              "author": "simon",
              "type": "story",
              "children": [
                {"id": 7, "parent_id": 42, "author": "alice", "text": "Ready"}
              ]
            }
        """.trimIndent()
        val source = FakeAlgoliaRepository(response)
        val parser = AlgoliaCommentsParser(
            parsingDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val preloads = CommentsPreloadRepository(
            algolia = source,
            parser = parser,
            nowMillis = { 100L },
        )
        preloads.preload(42, listOf(7))
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = source,
                hackerNewsRepository = UnusedHackerNewsRepository,
                algoliaCommentsParser = parser,
                preloads = preloads,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val story = Story("Loading", 42, false, false).also { it.kids = intArrayOf(7) }
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = true,
                filteredUsers = emptySet(),
                sorting = "default",
                collapseTopLevel = false,
                previousResponse = null,
                restoreScrollFromCache = false,
            ),
        )
        val applied = assertIs<CommentsEffect.ThreadApplied>(effect.await())

        assertEquals(1, source.itemRequests)
        assertEquals("Prepared comments", story.title)
        assertEquals(listOf(7), presenter.thread.state.value.allComments.drop(1).map { it.comment.id })
        assertTrue(applied.networkCompleted)
        assertEquals(null, applied.responseToCache)
        assertFalse(applied.broadcastStoryUpdate)
    }

    @Test
    fun preparedOfficialThreadOpensWithoutASecondNetworkRequest() = runTest {
        val official = RecordingHackerNewsRepository()
        val source = FakeAlgoliaRepository("{}")
        val preloads = CommentsPreloadRepository(
            algolia = source,
            official = OfficialCommentThreadLoader(official),
            nowMillis = { 100L },
        )
        preloads.preloadOfficial(42, listOf(7))
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = source,
                hackerNewsRepository = official,
                preloads = preloads,
            ),
            UnusedPollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val story = Story("Loading", 42, false, false).also { it.kids = intArrayOf(7) }
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(
            CommentsAction.LoadThread(
                story = story,
                useAlgolia = false,
                filteredUsers = emptySet(),
                sorting = "default",
                collapseTopLevel = false,
                previousResponse = null,
                restoreScrollFromCache = false,
            ),
        )
        val applied = assertIs<CommentsEffect.ThreadApplied>(effect.await())

        assertEquals(1, official.storyRequests)
        assertEquals(listOf(7), official.commentRequests)
        assertEquals("Official discussion", story.title)
        assertEquals(listOf(7), presenter.thread.state.value.allComments.drop(1).map { it.comment.id })
        assertTrue(applied.networkCompleted)
        assertFalse(applied.usedOfficialFallback)
    }

    @Test
    fun knownPollOptionsAreLoadedAndAppliedInCommonPresentation() = runTest {
        val repository = CommentThreadRepository(
            algoliaRepository = FakeAlgoliaRepository("{}"),
            hackerNewsRepository = UnusedHackerNewsRepository,
        )
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            repository,
            FakePollOptions,
            savedItemActions(),
            UnusedVotingService,
        )
        val story = Story("Poll: choose", 42, true, false).also {
            it.pollOptions = intArrayOf(7)
        }
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(CommentsAction.LoadPollOptions(story))
        val changed = assertIs<CommentsEffect.PollOptionsChanged>(effect.await())

        assertEquals(42, changed.storyId)
        val option = story.pollOptionArrayList?.single()
        assertEquals(7, option?.id)
        assertEquals("Kotlin", option?.text)
        assertEquals(12, option?.points)
        assertTrue(option?.loaded == true)
    }

    @Test
    fun storyVoteTransactionAndLoadingStateAreOwnedByPresenter() = runTest {
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(HackerNewsActionResult.Success()),
            UnusedVotingService,
        )
        val effects = async { presenter.effects.take(2).toList() }
        runCurrent()

        presenter.dispatch(CommentsAction.ToggleStoryVote(itemId = 42, isComment = false))
        assertTrue(presenter.state.value.storyVoteLoading)
        runCurrent()

        assertIs<CommentsEffect.SavedItemActionStarted>(effects.await()[0])
        assertIs<CommentsEffect.SavedItemActionCompleted>(effects.await()[1])
        assertTrue(presenter.savedItemState.isUpvoted(42, false))
        assertEquals(false, presenter.state.value.storyVoteLoading)
    }

    @Test
    fun failedOptimisticStartClearsLoadingAndAllowsRetry() = runTest {
        val store = MemoryKeyValueStore(failWrites = true)
        val actions = SavedItemActionUseCase(
            repository = SavedItemsRepository(store),
            nowMillis = { 0L },
            voteRequest = { _, _ -> HackerNewsActionResult.Success() },
            favoriteRequest = { _, _ -> HackerNewsActionResult.Success() },
        )
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            actions,
            UnusedVotingService,
        )
        val effects = async { presenter.effects.take(4).toList() }
        runCurrent()

        presenter.dispatch(CommentsAction.ToggleStoryVote(itemId = 42, isComment = false))
        runCurrent()
        assertFalse(presenter.state.value.storyVoteLoading)
        store.failWrites = false

        presenter.dispatch(CommentsAction.ToggleStoryVote(itemId = 42, isComment = false))
        runCurrent()

        val emitted = effects.await()
        assertIs<CommentsEffect.SavedItemActionStartFailed>(emitted[1])
        assertIs<CommentsEffect.SavedItemActionCompleted>(emitted[3])
        assertTrue(actions.isUpvoted(42, false))
        assertFalse(presenter.state.value.storyVoteLoading)
    }

    @Test
    fun commentVotesForDifferentItemsAreNotSilentlyDropped() = runTest {
        val firstResult = CompletableDeferred<HackerNewsActionResult>()
        val secondResult = CompletableDeferred<HackerNewsActionResult>()
        val actions = SavedItemActionUseCase(
            repository = SavedItemsRepository(MemoryKeyValueStore()),
            nowMillis = { 0L },
            voteRequest = { itemId, _ ->
                if (itemId == 1) firstResult.await() else secondResult.await()
            },
            favoriteRequest = { _, _ -> HackerNewsActionResult.Success() },
        )
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            actions,
            UnusedVotingService,
        )

        presenter.dispatch(CommentsAction.VoteComment(1, "up", previousDownvoted = false))
        runCurrent()
        presenter.dispatch(CommentsAction.VoteComment(2, "up", previousDownvoted = false))
        runCurrent()

        assertEquals(2, presenter.state.value.commentVoteLoadingId)
        assertTrue(actions.isUpvoted(1, true))
        assertTrue(actions.isUpvoted(2, true))
        secondResult.complete(HackerNewsActionResult.Success())
        runCurrent()
        assertEquals(1, presenter.state.value.commentVoteLoadingId)
        firstResult.complete(HackerNewsActionResult.Success())
        runCurrent()
        assertEquals(-1, presenter.state.value.commentVoteLoadingId)
    }

    @Test
    fun pollVoteRuntimeOwnsInFlightStateDuplicateSuppressionAndFailureOutcome() = runTest {
        val voting = RecordingVotingService()
        val session = CommentsSessionState()
        val presenter = CommentsPresenter(
            backgroundScope,
            session,
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            voting,
        )
        val runtime = CommentsFeatureRuntime(backgroundScope, session, presenter) { 0L }
        val effects = async { presenter.effects.take(2).toList() }
        runCurrent()

        runtime.votePollOption(7)
        assertEquals(7, presenter.state.value.pollVoteInFlightOptionId)
        runCurrent()
        runtime.votePollOption(8)
        runCurrent()
        assertEquals(listOf("7" to "up"), voting.requests)

        voting.result.complete(HackerNewsActionResult.Failure("Vote failed", "Try again"))
        runCurrent()

        val emitted = effects.await()
        assertEquals(7, assertIs<CommentsEffect.PollVoteStarted>(emitted[0]).optionId)
        val completed = assertIs<CommentsEffect.PollVoteCompleted>(emitted[1])
        val failure = assertIs<PollVoteOutcome.Failure>(completed.outcome)
        assertEquals("Vote failed", assertIs<HackerNewsActionResult.Failure>(failure.result).summary)
        assertEquals(null, presenter.state.value.pollVoteInFlightOptionId)
    }

    @Test
    fun successfulPollVoteHasAnExplicitPortableOutcome() = runTest {
        val voting = RecordingVotingService().also {
            it.result.complete(HackerNewsActionResult.Success())
        }
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            voting,
        )
        val completed = async {
            presenter.effects.filterIsInstance<CommentsEffect.PollVoteCompleted>().first()
        }
        runCurrent()

        presenter.dispatch(CommentsAction.VotePollOption(9))
        runCurrent()

        assertIs<PollVoteOutcome.Success>(completed.await().outcome)
        assertEquals(listOf("9" to "up"), voting.requests)
        assertEquals(null, presenter.state.value.pollVoteInFlightOptionId)
    }

    @Test
    fun cancelledPollVoteCannotClearANewerVoteSpinner() = runTest {
        val voting = RecordingVotingService()
        val presenter = CommentsPresenter(
            backgroundScope,
            CommentsSessionState(),
            CommentThreadRepository(
                algoliaRepository = FakeAlgoliaRepository("{}"),
                hackerNewsRepository = UnusedHackerNewsRepository,
            ),
            UnusedPollOptions,
            savedItemActions(),
            voting,
        )

        presenter.dispatch(CommentsAction.VotePollOption(7))
        runCurrent()
        presenter.dispatch(CommentsAction.CancelPollVote)
        presenter.dispatch(CommentsAction.VotePollOption(8))
        assertEquals(8, presenter.state.value.pollVoteInFlightOptionId)

        runCurrent()

        assertEquals(8, presenter.state.value.pollVoteInFlightOptionId)
        assertEquals(listOf("7" to "up", "8" to "up"), voting.requests)
        voting.result.complete(HackerNewsActionResult.Success())
        runCurrent()
        assertEquals(null, presenter.state.value.pollVoteInFlightOptionId)
    }

    private class FakeAlgoliaRepository(
        private val response: String,
    ) : AlgoliaRepository {
        var itemRequests = 0

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
            error("Not used")

        override suspend fun search(url: String): List<Story> = error("Not used")

        override suspend fun getItemJson(id: Int): String {
            itemRequests++
            return response
        }
    }

    private object UnusedHackerNewsRepository : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = error("Not used")
        override suspend fun getComment(id: Int): Comment? = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private class RecordingHackerNewsRepository : HackerNewsRepository {
        var storyRequests = 0
        val commentRequests = mutableListOf<Int>()

        override suspend fun getStory(id: Int): Story {
            storyRequests++
            return Story("Official discussion", id, true, false).also {
                it.kids = intArrayOf(7)
            }
        }

        override suspend fun getComment(id: Int): Comment {
            commentRequests += id
            return Comment().also {
                it.id = id
                it.by = "alice"
                it.text = "Ready"
            }
        }

        override suspend fun getStoryIds(type: StoryType): List<Int> = emptyList()
    }

    private object UnusedPollOptions : PollOptionsLoader {
        override suspend fun findOptionIds(storyId: Int): IntArray = error("Not used")
        override fun placeholders(optionIds: IntArray) = error("Not used")
        override fun loadOptions(optionIds: IntArray) = error("Not used")
    }

    private object UnusedVotingService : HackerNewsVotingService {
        override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult =
            error("Not used")
    }

    private class RecordingVotingService : HackerNewsVotingService {
        val requests = mutableListOf<Pair<String, String>>()
        val result = CompletableDeferred<HackerNewsActionResult>()

        override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult {
            requests += itemId to direction
            return result.await()
        }
    }

    private object FakePollOptions : PollOptionsLoader {
        override suspend fun findOptionIds(storyId: Int): IntArray = error("Not used")
        override fun placeholders(optionIds: IntArray): List<PollOption> = optionIds.map { id ->
            PollOption().also { it.id = id }
        }
        override fun loadOptions(optionIds: IntArray) = flowOf(
            PollOption().also {
                it.id = optionIds.single()
                it.text = "Kotlin"
                it.points = 12
                it.loaded = true
            },
        )
    }

    private fun savedItemActions(
        result: HackerNewsActionResult? = null,
    ) = SavedItemActionUseCase(
        repository = SavedItemsRepository(MemoryKeyValueStore()),
        nowMillis = { 0L },
        voteRequest = { _, _ -> result ?: error("Not used") },
        favoriteRequest = { _, _ -> result ?: error("Not used") },
    )

    private class MemoryKeyValueStore(
        var failWrites: Boolean = false,
    ) : KeyValueStore {
        private val values = mutableMapOf<String, Any?>()
        override fun contains(key: String) = key in values
        override fun remove(key: String) { values.remove(key) }
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun putString(key: String, value: String?) {
            if (failWrites) error("Write failed")
            values[key] = value
        }
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
        override fun putFloat(key: String, value: Float) { values[key] = value }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String) = values[key] as? Set<String> ?: emptySet()
        override fun putStringSet(key: String, value: Set<String>?) { values[key] = value }
    }
}
