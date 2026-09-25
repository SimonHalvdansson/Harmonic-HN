package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PollOptionsRepositoryTest {
    @Test
    fun optionsLoadInTwoBoundedRoundsInsteadOfEightSerialRequests() = runTest {
        var active = 0
        var peak = 0
        val repository = PollOptionsRepository(api { id ->
            active++
            peak = maxOf(peak, active)
            try {
                delay(150)
                HackerNewsItemDto(id = id, text = "Option $id", score = id)
            } finally { active-- }
        })
        val options = repository.loadOptions(IntArray(8) { it + 1 }).toList()
        assertEquals((1..8).toSet(), options.map { it.id }.toSet())
        assertTrue(options.all { it.loaded })
        assertEquals(4, peak)
        assertEquals(300, testScheduler.currentTime)
    }

    @Test
    fun slowOrFailedOptionDoesNotHoldUpOtherOptions() = runTest {
        val slowStarted = CompletableDeferred<Unit>()
        val slowCancelled = CompletableDeferred<Unit>()
        val repository = PollOptionsRepository(api { id ->
            if (id == 1) {
                slowStarted.complete(Unit)
                try { awaitCancellation() } finally { slowCancelled.complete(Unit) }
            }
            slowStarted.await()
            if (id == 2) error("Unavailable")
            HackerNewsItemDto(id = id, text = "Ready", score = 10)
        })
        val first = repository.loadOptions(intArrayOf(1, 2, 3)).first { it.loaded }
        assertEquals(3, first.id)
        assertTrue(slowCancelled.isCompleted)
    }

    private fun api(item: suspend (Int) -> HackerNewsItemDto?) = object : HackerNewsApi {
        override suspend fun getItem(id: Int) = item(id)
        override suspend fun getUser(username: String): HackerNewsUserDto? = error("Unexpected user")
        override suspend fun getMaxItemId(): Int = error("Unexpected max item")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected feed")
    }
}
