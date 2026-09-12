package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.writeFully
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.io.IOException

class LinkSummaryRepositoryTest {
    @Test
    fun loadingAndParsingUseTheConfiguredBackgroundDispatcher() = runTest {
        val dispatcher = RecordingDispatcher(Dispatchers.Default)
        val client = HttpClient(MockEngine {
            respond(
                content = """
                    <html><head>
                      <title>Background parsing</title>
                      <meta name="description" content="Parsed away from the caller dispatcher.">
                    </head><body></body></html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        })

        val result = KtorLinkSummaryRepository(
            client = client,
            parsingDispatcher = dispatcher,
        ).load("https://example.com/article")

        assertEquals("Background parsing", result.title)
        assertTrue(dispatcher.dispatchCount > 0)
        client.close()
    }

    @Test
    fun largeHtmlUsesItsBoundedMetadataPrefix() = runTest {
        val html = buildString {
            append("<html><head><title>Large but previewable</title>")
            append("<meta name=\"description\" content=\"Useful metadata near the start.\">")
            append("</head><body>")
            repeat(2 * 1024 * 1024) { append('x') }
        }
        val client = HttpClient(MockEngine {
            respond(
                content = html,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        })

        val result = KtorLinkSummaryRepository(client).load("https://example.com/large")

        assertEquals("Large but previewable", result.title)
        assertEquals("Useful metadata near the start.", result.description)
        client.close()
    }

    @Test
    fun directImageBecomesAnImageSummaryInsteadOfAWebPageError() = runTest {
        val imageUrl = "https://cdn.example.com/media/benchmark.png"
        val client = HttpClient(MockEngine {
            respond(
                content = byteArrayOf(1, 2, 3),
                headers = headersOf(HttpHeaders.ContentType, "image/png"),
            )
        })

        val result = KtorLinkSummaryRepository(client).load(imageUrl, imageUrl)

        assertEquals("benchmark.png", result.title)
        assertEquals("cdn.example.com", result.siteName)
        assertEquals("image/png", result.contentType)
        assertEquals(imageUrl, result.imageUrl)
        assertEquals(imageUrl, result.finalUrl)
        client.close()
    }

    @Test
    fun metadataPrefixCompletesWithoutWaitingForTheRestOfTheResponse() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        val prefix = ByteArray(2 * 1024 * 1024) { ' '.code.toByte() }
        "<html><head><title>Bounded preview</title></head><body>".encodeToByteArray().copyInto(prefix)
        backgroundScope.launch {
            responseBody.writeFully(prefix)
            // Leave the channel open: waiting for EOF would time out instead of producing a preview.
        }
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load("https://example.com/stream")
            }
            assertEquals("Bounded preview", result.title)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun directImageDoesNotWaitForOrReadItsBody() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val imageUrl = "https://cdn.example.com/large.png"
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler), "image/png")
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load(imageUrl)
            }
            assertEquals(imageUrl, result.imageUrl)
            assertEquals("image/png", result.contentType)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun httpErrorReleasesTheUnreadResponse() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(
            responseBody, StandardTestDispatcher(testScheduler), status = HttpStatusCode.NotFound,
        )
        try {
            val error = assertFailsWith<LinkPreviewException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/missing")
                }
            }
            assertEquals("The page returned HTTP 404", error.message)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun cancellationWhileReadingReleasesTheResponseAndPropagates() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        try {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/slow")
                }
            }
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun smallResponsePreservesUtf8AcrossNetworkChunks() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        val html = "<html><head><title>æ漢😀</title>" +
            "<meta name=\"description\" content=\"Crème brûlée &amp; café\"></head></html>"
        backgroundScope.launch {
            for (byte in html.encodeToByteArray()) {
                responseBody.writeFully(byteArrayOf(byte))
                yield()
            }
            responseBody.close()
        }
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load("https://example.com/unicode")
            }
            assertEquals("æ漢😀", result.title)
            assertEquals("Crème brûlée & café", result.description)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun failedBodyReadDoesNotReturnAPartialPreview() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            responseBody.writeFully("<title>Incomplete response</title>".encodeToByteArray())
            yield()
            responseBody.cancel(IOException("Connection interrupted"))
        }
        try {
            val error = assertFailsWith<IOException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/interrupted")
                }
            }
            assertEquals("Connection interrupted", error.message)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    private fun streamingClient(
        responseBody: ByteChannel,
        dispatcher: CoroutineDispatcher,
        contentType: String = "text/html",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(MockEngine.create {
        this.dispatcher = dispatcher
        addHandler {
            respond(responseBody, status = status, headers = headersOf(HttpHeaders.ContentType, contentType))
        }
    })

    private class RecordingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var dispatchCount: Int = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            delegate.dispatch(context, block)
        }
    }
}
