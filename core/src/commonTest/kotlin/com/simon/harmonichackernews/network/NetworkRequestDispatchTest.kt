package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class NetworkRequestDispatchTest {
    @Test
    fun repositoriesReachTransportWithoutWaitingForUiDispatcher() = runTest {
        val worker = QueuedDispatcher()
        var reachedTransport = 0
        val provider: suspend () -> HttpClient = {
            // Model lazy initialization yielding before the repository can send its request.
            withContext(worker) { yield() }
            assertEquals(worker, currentCoroutineContext()[ContinuationInterceptor])
            reachedTransport++
            throw TransportReached()
        }
        val operations: List<suspend () -> Unit> = listOf(
            { KtorHackerNewsApi(provider, requestDispatcher = worker).getItem(42) },
            { KtorAlgoliaRepository(provider, requestDispatcher = worker).search("https://hn.algolia.com/api/v1/search") },
            { KtorHackerNewsWebRepository(provider, worker).getStoryList("newest") },
            { KtorLinkPreviewRepository(provider, worker).getArchiveUrl("https://example.test") },
        )
        for ((index, operation) in operations.withIndex()) {
            val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { operation() } }
            assertEquals(index, reachedTransport)
            // Keep the UI test scheduler occupied while allowing network work to progress.
            worker.runPending()
            assertEquals(index + 1, reachedTransport)
            assertFalse(result.isCompleted)
            assertIs<TransportReached>(result.await().exceptionOrNull())
        }
    }

    private class TransportReached : Exception()
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runPending() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
}
