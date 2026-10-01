package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteChannel
import kotlinx.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HarmonicHttpClientTest {
    @Test
    fun readOnlyRequestRetriesConnectionFailureOnce() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            if (++attempts == 1) throw IOException("unexpected end of stream")
            respond("recovered")
        })
        try {
            assertEquals("recovered", client.getTextOrThrow("https://example.com/feed"))
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun readOnlyRequestRetriesBrokenResponseBody() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            if (++attempts == 1) {
                respond(ByteReadChannel("partial").apply { cancel(IOException("truncated body")) })
            } else {
                respond("complete")
            }
        })
        try {
            assertEquals("complete", client.getTextOrThrow("https://example.com/feed"))
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun readOnlyRequestRetriesAlreadyFailedResponseBody() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            if (++attempts == 1) {
                // Unlike a buffered source, this is already closed for reads before consumption.
                respond(ByteChannel().apply { cancel(IOException("truncated body")) })
            } else {
                respond("complete")
            }
        })
        try {
            assertEquals("complete", client.getTextOrThrow("https://example.com/feed"))
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun persistentReadFailureStopsAfterTwoAttempts() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            throw IOException("offline")
        })
        try {
            assertFailsWith<IOException> { client.getTextOrThrow("https://example.com/feed") }
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationDuringBackoffPreventsRetry() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            throw IOException("offline")
        })
        try {
            val request = launch { client.getTextOrThrow("https://example.com/feed") }
            // Run until the retry delay, without advancing virtual time through it.
            while (attempts == 0) {
                runCurrent()
                kotlinx.coroutines.yield()
            }
            request.cancel()
            request.join()
            advanceUntilIdle()
            assertEquals(1, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun httpErrorsAreNotRetried() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            respond("unavailable", HttpStatusCode.ServiceUnavailable)
        })
        try {
            assertFailsWith<HttpStatusException> { client.getTextOrThrow("https://example.com/feed") }
            assertEquals(1, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun actionRequestsAreNotRetriedEvenWhenUsingGet() = runTest {
        var attempts = 0
        val client = HttpClient(MockEngine {
            attempts++
            throw IOException("response lost after action")
        })
        try {
            val transport = KtorHttpClient(client)
            val url = "https://news.ycombinator.com/vote?id=42"
            assertFailsWith<IOException> {
                transport.execute(HttpRequest.Builder().url(url).get().build())
            }
            assertEquals(1, attempts)
            assertFailsWith<IOException> {
                transport.execute(HttpRequest.Builder().url(url).post("id=42".toHttpRequestBody()).build())
            }
            assertEquals(2, attempts)
        } finally {
            client.close()
        }
    }

    @Test
    fun declaredOversizeCancelsResponseBody() = runTest {
        var attempts = 0
        val body = ByteReadChannel(byteArrayOf(1, 2, 3))
        val client = HttpClient(MockEngine {
            attempts++
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentLength,
                    (DEFAULT_MAX_BUFFERED_BODY_BYTES + 1L).toString(),
                ),
            )
        })

        assertFailsWith<HttpBodyLimitException> {
            client.getTextOrThrow("https://example.com/oversized")
        }

        assertEquals(1, attempts)
        advanceUntilIdle()
        // Cancelling SourceByteReadChannel(Buffer) need not exhaust its buffered bytes.
        assertTrue(body.closedCause != null || body.isClosedForRead)
        client.close()
    }

    @Test
    fun streamedOversizeCancelsUnreadRemainder() = runTest {
        val body = ByteReadChannel(ByteArray(DEFAULT_MAX_BUFFERED_BODY_BYTES + 2))
        val client = HttpClient(MockEngine {
            respond(content = body, status = HttpStatusCode.OK)
        })

        assertFailsWith<HttpBodyLimitException> {
            client.getTextOrThrow("https://example.com/streamed-oversize")
        }

        advanceUntilIdle()
        assertTrue(body.closedCause != null || body.isClosedForRead)
        client.close()
    }

    @Test
    fun streamingRequestsBypassHttpCacheBodyBuffering() = runTest {
        var cacheControl = ""
        val client = HttpClient(MockEngine { request ->
            cacheControl = request.headers[HttpHeaders.CacheControl].orEmpty()
            respond(content = "streamed", status = HttpStatusCode.OK)
        })

        val result = KtorHttpClient(client).executeStreaming(
            HttpRequest.Builder().url("https://example.com/model.bin").build(),
        ) { response ->
            response.body.readText()
        }

        assertEquals("streamed", result)
        assertTrue(cacheControl.split(',').any { it.trim() == "no-store" })
        client.close()
    }
}
