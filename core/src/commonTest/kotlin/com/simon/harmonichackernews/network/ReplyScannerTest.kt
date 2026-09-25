package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyScannerTest {
    private val now = 1_800_000_000
    private val clock = object : Clock { override fun now() = Instant.fromEpochSeconds(now.toLong()) }

    @Test
    fun boundedReadsPreserveReplyOrderAndAdvancePastUnavailableReplies() = runTest {
        var active = 0
        var peak = 0
        val api = api(listOf(4, 3, 2, 1), maxId = 120) { id ->
            active++
            peak = maxOf(peak, active)
            try {
                delay(if (id % 2 == 0) 150 else 50)
                when {
                    id in 1..4 -> item(id).copy(kids = listOf(id * 100, id * 100 + 1))
                    id == 401 -> null
                    else -> item(id)
                }
            } finally { active-- }
        }
        val result = DefaultReplyScanner(api, clock).scan("me", 50)
        assertEquals(listOf(400, 300, 301, 200, 201, 100, 101), result.replies.map { it.id })
        assertEquals(listOf(4, 3, 3, 2, 2, 1, 1), result.replies.map { it.parentId })
        assertEquals(401, result.lastSeenItemId)
        assertEquals(4, peak)
        assertEquals(450, testScheduler.currentTime)
    }

    @Test
    fun ageCutoffAndReplyFiltersSurviveOutOfOrderCompletion() = runTest {
        val requested = mutableListOf<Int>()
        val api = api(listOf(5, 4, 3, 2, 1), 10) { id ->
            requested += id
            delay(if (id == 4) 150 else 50)
            when (id) {
                5 -> item(id).copy(kids = (100..107).toList())
                4 -> item(id).copy(time = now - 15 * 24 * 60 * 60, kids = listOf(400))
                3 -> error("Speculative read failed beyond cutoff")
                2 -> awaitCancellation()
                1 -> item(id).copy(kids = listOf(100))
                100 -> item(id)
                101 -> item(id).copy(by = "ME")
                102 -> item(id).copy(deleted = true)
                103 -> item(id).copy(dead = true)
                104 -> item(id).copy(type = "story")
                105 -> item(id).copy(by = "")
                106 -> item(id).copy(time = now - 15 * 24 * 60 * 60)
                else -> null
            }
        }
        val scanner = DefaultReplyScanner(api, clock)
        val result = scanner.scan("me", 50)
        assertEquals(listOf(100), result.replies.map { it.id })
        assertEquals(107, result.lastSeenItemId)
        assertFalse(1 in requested) // No subsequent batch after the age cutoff.
        assertFalse(200 in requested || 300 in requested || 400 in requested)
        assertEquals(100, scanner.findLatestReply("me").reply?.id)
    }

    @Test
    fun cancellingScanCancelsAllConcurrentReads() = runTest {
        var active = 0
        val api = api(listOf(4, 3, 2, 1), 100) {
            active++
            try { awaitCancellation() } finally { active-- }
        }
        val scan = async { DefaultReplyScanner(api, clock).scan("me", 50) }
        runCurrent()
        assertEquals(4, active)
        scan.cancel()
        scan.join()
        assertEquals(0, active)
    }

    private fun item(id: Int) = HackerNewsItemDto(id = id, by = "other", type = "comment", time = now, text = "Reply")
    private fun api(submitted: List<Int>, maxId: Int, item: suspend (Int) -> HackerNewsItemDto?) = object : HackerNewsApi {
        override suspend fun getItem(id: Int) = item(id)
        override suspend fun getUser(username: String) = HackerNewsUserDto(id = username, submitted = submitted)
        override suspend fun getMaxItemId() = maxId
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unexpected feed")
    }
}
