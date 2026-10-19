package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.KtorHackerNewsApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class StoryRowLoadOrchestratorTest {
    @Test
    fun transportRetryBudgetIsNotMultipliedAndPermanentFailuresAreNotRetried() = runTest {
        for (status in listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.NotFound)) {
            var attempts = 0
            val client = HttpClient(MockEngine { attempts++; respond("failure", status) })
            try {
                val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
                val orchestrator = StoryRowLoadOrchestrator(backgroundScope, api, 30_000) { testScheduler.currentTime }
                val effects = async { orchestrator.effects.take(1).toList() }
                runCurrent()
                val story = Story("Loading", 42, false, false)
                orchestrator.load(story, false)
                val failure = assertIs<StoryRowLoadEffect.AttemptFailed>(effects.await().single())
                assertTrue(failure.finalAttempt)
                assertTrue(story.loadingFailed)
                assertEquals(if (status.value == 503) 3 else 1, attempts)
            } finally { client.close() }
        }
    }

    @Test
    fun transportRecoversWithinItsBudgetBeforePublishingLoadedRow() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            if (++attempts < 3) respond("unavailable", HttpStatusCode.ServiceUnavailable)
            else respond("""{"id":42,"type":"story","by":"reader","title":"Recovered"}""")
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            val orchestrator = StoryRowLoadOrchestrator(backgroundScope, api, 30_000) { testScheduler.currentTime }
            val effects = async { orchestrator.effects.take(1).toList() }
            runCurrent()
            val story = Story("Loading", 42, false, false)
            orchestrator.load(story, false)
            assertIs<StoryRowLoadEffect.Loaded>(effects.await().single())
            assertEquals(3, attempts)
            assertEquals("Recovered", story.title)
        } finally { client.close() }
    }

    @Test
    fun simulatedFailureAndManualRetryResamples() = runTest {
        val api = SuccessfulApi()
        var fail = true
        var samples = 0
        val orchestrator = StoryRowLoadOrchestrator(
            scope = backgroundScope,
            hackerNewsApi = api,
            staleLoadMillis = 30_000,
            shouldSimulateFailure = { samples++; fail },
            nowMillis = { testScheduler.currentTime },
        )
        val story = Story("Loading", 42, false, false)
        orchestrator.load(story, false)
        runCurrent()
        assertTrue(story.loadingFailed)
        assertFalse(story.loaded)
        assertFalse(orchestrator.isInProgress(42))
        assertEquals(0, api.attempts)
        assertEquals(1, samples)

        fail = false
        story.loadingFailed = false
        orchestrator.load(story, false)
        runCurrent()
        assertTrue(story.loaded)
        assertFalse(story.loadingFailed)
        assertEquals(2, samples)

        fail = true
        orchestrator.invalidateLoadedStories(listOf(42))
        orchestrator.load(story, false)
        runCurrent()
        assertTrue(story.loaded)
        assertFalse(story.loadingFailed)
        assertEquals(2, samples)
    }

    @Test
    fun cachedContentArrivingDuringLoadRemainsUsableWhenHttpFails() = runTest {
        val reply = kotlinx.coroutines.CompletableDeferred<Unit>()
        val api = object : HackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto? { reply.await(); error("Offline") }
            override suspend fun getUser(username: String): HackerNewsUserDto? = error("Unused")
            override suspend fun getMaxItemId(): Int = error("Unused")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
        }
        val orchestrator = StoryRowLoadOrchestrator(backgroundScope, api, 30_000) { testScheduler.currentTime }
        val story = Story("Loading", 1, false, false)
        orchestrator.load(story, false)
        runCurrent()
        story.title = "Offline cache"
        story.loaded = true
        reply.complete(Unit)
        runCurrent()
        assertTrue(story.loaded)
        assertFalse(story.loadingFailed)
        assertEquals("Offline cache", story.title)
    }

    @Test
    fun failedMetadataRefreshKeepsLoadedContentAndCanRetryInTheNextGeneration() = runTest {
        var attempts = 0
        val api = object : HackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto? { attempts++; error("Offline") }
            override suspend fun getUser(username: String): HackerNewsUserDto? = error("Unused")
            override suspend fun getMaxItemId(): Int = error("Unused")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
        }
        val orchestrator = StoryRowLoadOrchestrator(backgroundScope, api, 30_000) { testScheduler.currentTime }
        val story = Story("Retained", 1, true, true).apply { score = 10 }
        repeat(2) {
            orchestrator.beginGeneration()
            orchestrator.invalidateLoadedStories(listOf(1))
            orchestrator.load(story, false)
            runCurrent()
            assertTrue(story.loaded)
            assertFalse(story.loadingFailed)
            assertEquals("Retained", story.title)
            assertEquals(10, story.score)
            assertFalse(orchestrator.needsRefresh(1))
            orchestrator.load(story, false)
            runCurrent()
            assertEquals(it + 1, attempts)
        }
    }

    @Test
    fun successfulLoadAppliesStoryMutationAndPublishesOnce() = runTest {
        val api = SuccessfulApi()
        val orchestrator = StoryRowLoadOrchestrator(
            scope = backgroundScope,
            hackerNewsApi = api,
            staleLoadMillis = 30_000,
            nowMillis = { testScheduler.currentTime },
        )
        val effects = async { orchestrator.effects.take(1).toList() }
        runCurrent()
        val generation = orchestrator.beginGeneration()
        val story = Story("Loading", 42, false, false)

        orchestrator.load(story, preserveTime = false, requestGeneration = generation)
        runCurrent()

        val results = effects.await()
        assertEquals(1, api.attempts)
        assertIs<StoryRowLoadEffect.Loaded>(results.single())
        assertEquals("Shared row", story.title)
        assertTrue(story.loaded)
    }

    @Test
    fun beginGenerationSafelyCancelsMultipleInFlightLoads() = runTest {
        val api = SuspendingApi()
        val orchestrator = suspendingOrchestrator(api)
        val generation = orchestrator.beginGeneration()
        val stories = listOf(
            Story("Loading", 42, false, false),
            Story("Loading", 43, false, false),
        )

        stories.forEach { story ->
            orchestrator.load(story, preserveTime = false, requestGeneration = generation)
        }

        assertEquals(2, api.started)
        assertEquals(generation + 1, orchestrator.beginGeneration())
        assertEquals(2, api.cancelled)
        assertTrue(stories.none { orchestrator.isInProgress(it.id) })
    }

    @Test
    fun clearSafelyCancelsMultipleInFlightLoads() = runTest {
        val api = SuspendingApi()
        val orchestrator = suspendingOrchestrator(api)
        val generation = orchestrator.beginGeneration()
        val stories = listOf(
            Story("Loading", 42, false, false),
            Story("Loading", 43, false, false),
        )

        stories.forEach { story ->
            orchestrator.load(story, preserveTime = false, requestGeneration = generation)
        }

        assertEquals(2, api.started)
        orchestrator.clear()
        assertEquals(2, api.cancelled)
        assertTrue(stories.none { orchestrator.isInProgress(it.id) })
    }

    private fun TestScope.suspendingOrchestrator(api: HackerNewsApi) = StoryRowLoadOrchestrator(
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        hackerNewsApi = api,
        staleLoadMillis = 30_000,
        nowMillis = { testScheduler.currentTime },
    )

    private class SuccessfulApi : HackerNewsApi {
        var attempts = 0

        override suspend fun getItem(id: Int): HackerNewsItemDto {
            attempts++
            return HackerNewsItemDto(
                id = id,
                type = "story",
                by = "simon",
                title = "Shared row",
            )
        }

        override suspend fun getUser(username: String): HackerNewsUserDto? = error("Not used")
        override suspend fun getMaxItemId(): Int = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private class SuspendingApi : HackerNewsApi {
        var started = 0
        var cancelled = 0

        override suspend fun getItem(id: Int): HackerNewsItemDto? {
            started++
            try {
                awaitCancellation()
            } finally {
                cancelled++
            }
        }

        override suspend fun getUser(username: String): HackerNewsUserDto? = error("Not used")
        override suspend fun getMaxItemId(): Int = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }
}
