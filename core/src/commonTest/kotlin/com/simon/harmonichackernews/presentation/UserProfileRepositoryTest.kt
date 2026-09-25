package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileRepositoryTest {
    @Test
    fun freshnessCaseIdentityEvictionAndCompactPublicData() = runTest {
        var now = 100L
        val requests = mutableListOf<String>()
        val repository = UserProfileRepository(
            api { name -> requests += name; user(name).copy(submitted = (1..100_000).toList()) },
            backgroundScope, nowMillis = { now }, maxEntries = 2, freshMillis = 50,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        assertTrue(repository.load(" Alice ").hasSubmissions)
        repository.load("Alice")
        repository.load("alice")
        assertEquals(listOf("Alice", "alice"), requests)
        now += 51
        assertEquals("Alice", repository.cached("Alice")?.id) // Stale remains displayable.
        repository.load("Alice")
        repository.load("Bob")
        assertNull(repository.cached("alice")) // Alice was touched; case variants do not alias.
        assertEquals(listOf("Alice", "alice", "Alice", "Bob"), requests)
        assertEquals("Bob", repository.cached("Bob")?.id)
    }

    @Test
    fun textBudgetBoundsRetentionAndFailureKeepsStaleData() = runTest {
        var fail = false
        val repository = UserProfileRepository(
            api { name -> if (fail) error("Offline") else user(name).copy(about = if (name == "Large") "x".repeat(1000) else "Bio") },
            backgroundScope, maxTextBytes = 100, dispatcher = StandardTestDispatcher(testScheduler),
        )
        repository.load("Small")
        assertEquals(1000, repository.load("Large").about.length)
        assertNull(repository.cached("Large"))
        fail = true
        assertTrue(runCatching { repository.refresh("Small") }.isFailure)
        assertEquals("Bio", repository.cached("Small")?.about)
    }

    @Test
    fun simultaneousCallersShareAndOneCancellationKeepsTheOtherAlive() = runTest {
        val response = CompletableDeferred<HackerNewsUserDto>()
        var requests = 0
        var cancelled = false
        val repository = UserProfileRepository(
            api { requests++; try { response.await() } finally { if (!response.isCompleted) cancelled = true } },
            backgroundScope, dispatcher = StandardTestDispatcher(testScheduler),
        )
        val first = async { repository.load("Alice") }
        val second = async { repository.load("Alice") }
        runCurrent()
        assertEquals(1, requests)
        first.cancel(); runCurrent()
        assertFalse(cancelled)
        response.complete(user("Alice"))
        assertEquals("Alice", second.await().id)
        assertEquals(1, requests)
    }

    @Test
    fun lastCancellationStopsNetworkAndAllowsANewRequest() = runTest {
        var requests = 0
        var cancelled = 0
        val repository = UserProfileRepository(
            api { requests++; try { awaitCancellation() } finally { cancelled++ } },
            backgroundScope, dispatcher = StandardTestDispatcher(testScheduler),
        )
        val first = async { repository.load("Alice") }
        runCurrent(); first.cancel(); runCurrent()
        assertEquals(1, cancelled)
        assertNull(repository.cached("Alice"))
        val second = async { repository.load("Alice") }
        runCurrent()
        assertEquals(2, requests)
        second.cancel(); runCurrent()
        assertEquals(2, cancelled)
    }

    @Test
    fun uncooperativeOldResponseCannotOverwriteReplacement() = runTest {
        val old = CompletableDeferred<HackerNewsUserDto>()
        var requests = 0
        val repository = UserProfileRepository(
            api { if (++requests == 1) withContext(NonCancellable) { old.await() } else user(it).copy(karma = 200) },
            backgroundScope, dispatcher = StandardTestDispatcher(testScheduler),
        )
        val first = async { repository.load("Alice") }
        runCurrent(); first.cancel(); runCurrent()
        assertEquals(200, repository.load("Alice").karma)
        old.complete(user("Alice").copy(karma = 1)); runCurrent()
        assertEquals(200, repository.cached("Alice")?.karma)
    }

    private fun user(name: String) = HackerNewsUserDto(id = name, karma = 10, created = 1_000)

    private fun api(load: suspend (String) -> HackerNewsUserDto?) = object : HackerNewsApi {
        override suspend fun getUser(username: String) = load(username)
        override suspend fun getItem(id: Int): HackerNewsItemDto? = error("Unused")
        override suspend fun getMaxItemId(): Int = error("Unused")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
    }
}
