package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoryFeedRepositoryTest {
    @Test
    fun indexRequestDoesNotWaitForUiDispatcherAfterTransportInitialization() = runTest {
        val worker = QueuedDispatcher()
        var sent = false
        val repository = feeds(object : UnusedHackerNewsRepository() {
            override suspend fun getStoryIds(type: StoryType): List<Int> {
                // Model lazy transport initialization suspending on its worker dispatcher.
                withContext(worker) { yield() }
                assertEquals(StoryType.NEW_STORIES, type)
                sent = true
                return listOf(42, 7)
            }
        }, worker)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            repository.load(StoryType.NEW_STORIES)
        }
        assertFalse(sent)
        // The UI scheduler remains occupied (as during first composition). Sending must still
        // progress; applying the result correctly waits for the caller's dispatcher afterward.
        worker.runPending()
        assertTrue(sent)
        assertFalse(result.isCompleted)
        assertEquals(StoryFeedResult.ItemIds(listOf(42, 7)), result.await())
    }

    @Test
    fun cancellationBeforeWorkerRunsDoesNotSendRequest() = runTest {
        val worker = QueuedDispatcher()
        var sent = false
        val repository = feeds(object : UnusedHackerNewsRepository() {
            override suspend fun getStoryIds(type: StoryType): List<Int> {
                sent = true
                return emptyList()
            }
        }, worker)
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            repository.load(StoryType.TOP_STORIES)
        }
        result.cancel()
        worker.runPending()
        result.join()
        assertFalse(sent)
    }

    @Test
    fun networkFailureStillReachesCaller() = runTest {
        val repository = feeds(object : UnusedHackerNewsRepository() {
            override suspend fun getStoryIds(type: StoryType): List<Int> =
                throw HttpStatusException(503, "Unavailable", "https://example.test")
        }, StandardTestDispatcher(testScheduler))
        val error = assertFailsWith<HttpStatusException> { repository.load(StoryType.TOP_STORIES) }
        assertEquals(503, error.statusCode)
    }

    private fun feeds(
        hn: HackerNewsRepository,
        dispatcher: CoroutineDispatcher,
    ) = StoryFeedRepository(
        hackerNewsRepository = hn,
        webRepository = KtorHackerNewsWebRepository(client = { error("Unexpected web request") }),
        unslopRepository = UnslopRepository { error("Unexpected RSS request") },
        requestDispatcher = dispatcher,
    )

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runPending() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private abstract class UnusedHackerNewsRepository : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = error("Unexpected story request")
        override suspend fun getComment(id: Int): Comment? = error("Unexpected comment request")
    }
}
