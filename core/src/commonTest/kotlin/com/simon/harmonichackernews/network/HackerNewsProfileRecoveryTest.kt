package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HackerNewsProfileRecoveryTest {
    @Test
    fun connectionFailureRetriesTheSamePublicGet() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("https://hacker-news.firebaseio.com/v0/user/Alice.json", request.url.toString())
            if (++attempts == 1) throw IOException("Stale pooled connection")
            respond(PROFILE)
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            assertEquals("Alice", api.getUser(" Alice ")?.id)
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun interruptedResponseBodyIsFetchedAgain() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            if (++attempts == 1) {
                respond(ByteChannel().apply { cancel(IOException("Connection interrupted")) })
            } else {
                respond(PROFILE)
            }
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            assertEquals("Alice", api.getUser("Alice")?.id)
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun persistentConnectionFailureStopsAfterTwoAttempts() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            throw IOException("Offline")
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            assertFailsWith<IOException> { api.getUser("Alice") }
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationDoesNotStartAnotherRequest() = runTest {
        var attempts = 0
        val started = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            attempts++
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                // Some transports report an I/O error while cancellation closes their socket.
                throw IOException("Socket closed")
            }
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            val request = async { api.getUser("Alice") }
            started.await()
            request.cancel()
            assertFailsWith<CancellationException> { request.await() }
            request.join()
            assertEquals(1, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun completedResponsesAreNotRetried() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine { request ->
            attempts++
            when (request.url.encodedPath.substringAfterLast('/')) {
                "Missing.json" -> respond("null")
                "Invalid.json" -> respond("not JSON")
                "Unavailable.json" -> respond("Unavailable", HttpStatusCode.ServiceUnavailable)
                else -> respond(PROFILE, headers = headersOf(
                    HttpHeaders.ContentLength, (DEFAULT_MAX_BUFFERED_BODY_BYTES + 1L).toString(),
                ))
            }
        })
        try {
            val api = KtorHackerNewsApi(client, requestDispatcher = StandardTestDispatcher(testScheduler))
            assertNull(api.getUser("Missing"))
            assertEquals(1, attempts)
            assertFailsWith<ApiDecodingException> { api.getUser("Invalid") }
            assertEquals(2, attempts)
            assertFailsWith<HttpStatusException> { api.getUser("Unavailable") }
            assertEquals(3, attempts)
            assertFailsWith<HttpBodyLimitException> { api.getUser("Oversized") }
            assertEquals(4, attempts)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val PROFILE = """{"id":"Alice","created":1000,"karma":42}"""
    }
}
