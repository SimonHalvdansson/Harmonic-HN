package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryRowLoadOrchestratorTest {
    @Test
    fun retriesAndStoryMutationAreOwnedByCommonOrchestration() = runTest {
        val api = RetryingApi()
        val orchestrator = StoryRowLoadOrchestrator(
            scope = backgroundScope,
            hackerNewsApi = api,
            staleLoadMillis = 30_000,
            nowMillis = { testScheduler.currentTime },
        )
        val effects = async { orchestrator.effects.take(3).toList() }
        runCurrent()
        val generation = orchestrator.beginGeneration()
        val story = Story("Loading", 42, false, false)

        orchestrator.load(story, preserveTime = false, requestGeneration = generation)
        runCurrent()

        val results = effects.await()
        assertEquals(3, api.attempts)
        assertIs<StoryRowLoadEffect.AttemptFailed>(results[0])
        assertIs<StoryRowLoadEffect.AttemptFailed>(results[1])
        assertIs<StoryRowLoadEffect.Loaded>(results[2])
        assertEquals("Shared row", story.title)
        assertTrue(story.loaded)
    }

    private class RetryingApi : HackerNewsApi {
        var attempts = 0

        override suspend fun getItem(id: Int): HackerNewsItemDto {
            attempts++
            if (attempts < 3) error("temporary")
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
}
