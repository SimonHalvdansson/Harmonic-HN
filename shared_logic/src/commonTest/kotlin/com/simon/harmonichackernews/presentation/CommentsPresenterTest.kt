package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PollOption
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.PollOptionsLoader
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsPresenterTest {
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
        assertEquals(listOf(7), presenter.thread.state.value.allComments.drop(1).map(Comment::id))
        assertTrue(presenter.state.value.loaded)
        assertEquals(null, presenter.state.value.failure)
        assertTrue(applied.contentApplied)
        assertTrue(applied.networkCompleted)
        assertEquals(response, applied.responseToCache)
        assertTrue(applied.broadcastStoryUpdate)
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

    private class FakeAlgoliaRepository(
        private val response: String,
    ) : AlgoliaRepository {
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
            error("Not used")

        override suspend fun search(url: String): List<Story> = error("Not used")

        override suspend fun getItemJson(id: Int): String = response
    }

    private object UnusedHackerNewsRepository : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = error("Not used")
        override suspend fun getComment(id: Int): Comment? = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private object UnusedPollOptions : PollOptionsLoader {
        override suspend fun findOptionIds(storyId: Int): IntArray = error("Not used")
        override fun placeholders(optionIds: IntArray) = error("Not used")
        override fun loadOptions(optionIds: IntArray) = error("Not used")
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

    private class MemoryKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, Any?>()
        override fun contains(key: String) = key in values
        override fun remove(key: String) { values.remove(key) }
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun putString(key: String, value: String?) { values[key] = value }
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
