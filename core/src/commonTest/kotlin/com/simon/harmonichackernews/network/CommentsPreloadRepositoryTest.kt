package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentsPreloadRepositoryTest {
    @Test
    fun downloadsParsesPersistsAndConsumesPreparedThread() = runTest {
        val source = RecordingAlgoliaRepository(RESPONSE)
        val stored = mutableListOf<Pair<Int, String>>()
        val repository = CommentsPreloadRepository(
            algolia = source,
            storeResponse = { storyId, response -> stored += storyId to response },
            nowMillis = { 1_000L },
        )

        val loaded = repository.preload(42, listOf(7, 8), setOf("blocked"))
        val reused = repository.preload(42, listOf(7, 8), setOf("BLOCKED"))

        assertNotNull(loaded)
        assertEquals(listOf(8), loaded.parsed.comments.map { it.id })
        assertEquals(1, source.itemRequests)
        assertEquals(loaded, reused)
        assertEquals(listOf(42 to RESPONSE), stored)
        assertTrue(repository.isPrepared(42, listOf(7, 8), setOf("blocked")))

        val consumed = repository.takeOrAwait(42, listOf(7, 8), setOf("blocked"))

        assertEquals(loaded, consumed)
        assertFalse(repository.isPrepared(42, listOf(7, 8), setOf("blocked")))
        assertEquals(0, repository.preparedCount())
    }

    @Test
    fun cancellationRemovesTheInFlightEntry() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val repository = CommentsPreloadRepository(
            algolia = object : AlgoliaRepository {
                override suspend fun getSubmissions(userName: String, limit: Int) = emptyList<Story>()
                override suspend fun search(url: String) = emptyList<Story>()
                override suspend fun getItemJson(id: Int): String {
                    requestStarted.complete(Unit)
                    return response.await()
                }
            },
            nowMillis = { 1_000L },
        )

        val preload = launch { repository.preload(42) }
        requestStarted.await()
        preload.cancelAndJoin()

        assertFalse(repository.isPrepared(42))
        assertEquals(0, repository.preparedCount())
    }

    @Test
    fun downloadsPreparesAndConsumesOfficialApiThread() = runTest {
        val official = RecordingHackerNewsRepository()
        val repository = CommentsPreloadRepository(
            algolia = RecordingAlgoliaRepository(RESPONSE),
            official = OfficialCommentThreadLoader(official),
            nowMillis = { 1_000L },
        )

        val loaded = repository.preloadOfficial(42, listOf(7))
        val reused = repository.preloadOfficial(42, listOf(7))

        assertNotNull(loaded)
        assertEquals(listOf(7, 8), loaded.comments.map(Comment::id))
        assertEquals(listOf(0, 1), loaded.comments.map(Comment::depth))
        assertEquals(1, official.storyRequests)
        assertEquals(listOf(7, 8), official.commentRequests)
        assertEquals(loaded, reused)
        assertTrue(repository.isOfficialPrepared(42, listOf(7)))

        val consumed = repository.takeOfficialOrAwait(42, listOf(7))

        assertEquals(loaded, consumed)
        assertFalse(repository.isOfficialPrepared(42, listOf(7)))
    }

    private class RecordingAlgoliaRepository(
        private val response: String,
    ) : AlgoliaRepository {
        var itemRequests = 0

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> = emptyList()
        override suspend fun search(url: String): List<Story> = emptyList()

        override suspend fun getItemJson(id: Int): String {
            itemRequests++
            return response
        }
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
                it.by = "user$id"
                it.text = "Comment $id"
                if (id == 7) it.kidsIds = intArrayOf(8)
            }
        }

        override suspend fun getStoryIds(type: StoryType): List<Int> = emptyList()
    }

    private companion object {
        val RESPONSE =
            """
            {
              "id": 42,
              "title": "Prepared discussion",
              "children": [
                {"id": 7, "parent_id": 42, "author": "blocked", "text": "hidden"},
                {"id": 8, "parent_id": 42, "author": "visible", "text": "ready"}
              ]
            }
            """.trimIndent()
    }
}
