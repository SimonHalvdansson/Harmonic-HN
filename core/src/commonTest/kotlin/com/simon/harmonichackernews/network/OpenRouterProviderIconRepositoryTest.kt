package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class OpenRouterProviderIconRepositoryTest {
    @Test
    fun duplicateProviderRequestsShareOneRemoteResolution() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val repository = KtorOpenRouterProviderIconRepository(backgroundScope) { slug ->
            calls++
            release.await()
            OpenRouterProviderIcon.RemoteUrl("https://example.com/$slug.png")
        }

        val first = async { repository.resolve("Acme") }
        runCurrent()
        val second = async { repository.resolve("acme") }
        runCurrent()

        assertEquals(1, calls)
        release.complete(Unit)
        assertEquals(first.await(), second.await())
    }

    @Test
    fun distinctProvidersAreNotSerializedBehindOneRemoteRequest() = runTest {
        val started = mutableSetOf<String>()
        val bothStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = KtorOpenRouterProviderIconRepository(backgroundScope) { slug ->
            started += slug
            if (started.size == 2) bothStarted.complete(Unit)
            release.await()
            OpenRouterProviderIcon.RemoteUrl("https://example.com/$slug.png")
        }

        val first = async { repository.resolve("one") }
        val second = async { repository.resolve("two") }
        runCurrent()

        assertTrue(bothStarted.isCompleted)
        release.complete(Unit)
        first.await()
        second.await()
    }

    @Test
    fun cancellingFirstCallerDoesNotCancelSharedResolution() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = KtorOpenRouterProviderIconRepository(backgroundScope) { slug ->
            started.complete(Unit)
            release.await()
            OpenRouterProviderIcon.RemoteUrl("https://example.com/$slug.png")
        }

        val first = async { repository.resolve("acme") }
        started.await()
        val follower = async { repository.resolve("acme") }
        runCurrent()

        first.cancelAndJoin()
        release.complete(Unit)

        assertEquals(
            OpenRouterProviderIcon.RemoteUrl("https://example.com/acme.png"),
            follower.await().icon,
        )
    }
}
