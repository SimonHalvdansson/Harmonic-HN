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
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSummaryStreamingTest {
    @Test
    fun fastTokenBurstCoalescesProgressAndPreservesTheCompleteResponse() = runTest {
        val body = buildString {
            repeat(100) { append("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n") }
            append("data: [DONE]\n\n")
        }
        val transport = HttpClient(MockEngine { respond(body) })
        try {
            val events = KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                .summarize(streamConfig(false), "Article").toList()
            assertEquals(listOf(CloudSummaryEvent.Progress("x")), events.filterIsInstance<CloudSummaryEvent.Progress>())
            assertEquals(CloudSummaryEvent.Success("x".repeat(100)), events.last())
        } finally { transport.close() }
    }

    @Test
    fun prematureEofNeverPublishesSuccessForEitherProvider() = runTest {
        for (anthropic in listOf(false, true)) for (ending in listOf("", "\n\n")) {
            val chunk = if (anthropic) {
                """{"type":"content_block_delta","delta":{"text":"Partial answer"}}"""
            } else {
                """{"choices":[{"delta":{"content":"Partial answer"},"finish_reason":null}]}"""
            }
            val transport = HttpClient(MockEngine { respond("data: $chunk$ending") })
            try {
                var failure: Throwable? = null
                val events = KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                    .summarize(streamConfig(anthropic), "Article")
                    .catch { failure = it }.toList()
                assertTrue(failure is CloudSummaryException)
                assertTrue(failure?.message.orEmpty().contains("before completion"))
                assertTrue(events.none { it is CloudSummaryEvent.Success })
            } finally { transport.close() }
        }
    }

    @Test
    fun providerTerminalEventsIncludingUnterminatedLastLinePublishSuccess() = runTest {
        val openAi = """{"choices":[{"delta":{"content":"Complete"},"finish_reason":null}]}"""
        val anthropic = """{"type":"content_block_delta","delta":{"text":"Complete"}}"""
        for ((isAnthropic, body) in listOf(
            false to "data: $openAi\n\ndata: [DONE]",
            false to "data: $openAi\n\ndata: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
            false to "data: {\"choices\":[{\"delta\":{\"content\":\"Complete\"},\"finish_reason\":\"length\"}]}\n\n",
            true to "event: content_block_delta\ndata: $anthropic\n\nevent: message_stop\ndata: {\"type\":\"message_stop\"}",
        )) {
            val transport = HttpClient(MockEngine { respond(body) })
            try {
                val events = KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                    .summarize(streamConfig(isAnthropic), "Article").toList()
                assertEquals(CloudSummaryEvent.Success("Complete"), events.last())
            } finally { transport.close() }
        }
    }

    @Test
    fun plainJsonFallbackStillWorksForBothProviders() = runTest {
        for ((anthropic, body) in listOf(
            false to """{"choices":[{"message":{"content":"Complete"}}]}""",
            true to """{"content":[{"type":"text","text":"Complete"}]}""",
        )) {
            val transport = HttpClient(MockEngine { respond(body) })
            try {
                val events = KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                    .summarize(streamConfig(anthropic), "Article").toList()
                assertEquals(CloudSummaryEvent.Success("Complete"), events.last())
            } finally { transport.close() }
        }
    }

    private fun streamConfig(anthropic: Boolean) = CloudSummaryConfig(
        if (anthropic) "https://api.anthropic.com/v1" else "https://provider.test/v1", "key", "model",
    )

    @Test
    fun articleExtractionRejectsOversizeBeforeEof() = runTest {
        val body = ByteChannel(autoFlush = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html")) }
            }
        }
        backgroundScope.launch {
            val chunk = ByteArray(64 * 1024)
            repeat(4 * 1024 * 1024 / chunk.size) { body.writeFully(chunk) }
            body.writeFully(byteArrayOf(1))
        }
        try {
            withTimeout(2_000) {
                assertFailsWith<HttpBodyLimitException> {
                    KtorCloudSummaryRepository(KtorHttpClient(transport), "test", dispatcher)
                        .extractMainContent("https://example.com/article")
                }
            }
            assertTrue(body.isClosedForRead || body.closedCause != null)
        } finally { body.cancel(); transport.close() }
    }

    @Test
    fun ordinaryArticleExtractionStillReturnsReadableText() = runTest {
        val client = HttpClient(MockEngine { respond("<html><body><p>Article text.</p></body></html>") })
        try {
            assertEquals("Article text.", KtorCloudSummaryRepository(KtorHttpClient(client), "test", StandardTestDispatcher(testScheduler))
                .extractMainContent("https://example.com/article"))
        } finally { client.close() }
    }

    @Test
    fun doneEventFinishesWithoutWaitingForSocketEof() = runTest {
        val body = ByteChannel(autoFlush = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val transport = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream")) }
            }
        }
        backgroundScope.launch {
            body.writeStringUtf8("data: {\"choices\":[{\"delta\":{\"content\":\"Complete\"}}]}\n\ndata: [DONE]\n\n")
        }
        try {
            val events = withTimeout(1_000) {
                KtorCloudSummaryRepository(KtorHttpClient(transport), "test", dispatcher)
                    .summarize(CloudSummaryConfig("https://provider.test/v1", "key", "model"), "Article")
                    .toList()
            }
            assertEquals(CloudSummaryEvent.Success("Complete"), events.last())
            assertTrue(body.isClosedForRead)
        } finally { body.cancel(); transport.close() }
    }

    @Test
    fun firstTokenArrivesBeforeProviderClosesBodyAndCancellationClosesStream() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val transport = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler {
                    respond(responseBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
                }
            }
        }
        backgroundScope.launch {
            responseBody.writeStringUtf8("data: {\"choices\":[{\"delta\":{\"content\":\"First token\"}}]}\n\n")
            // The provider keeps the socket open while generating the rest of the response.
        }
        try {
            val progress = withTimeout(1_000) {
                KtorCloudSummaryRepository(KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler))
                    .summarize(CloudSummaryConfig("https://provider.test/v1", "test-key", "test-model"), "Article")
                    .filterIsInstance<CloudSummaryEvent.Progress>()
                    .first()
            }
            assertEquals("First token", progress.summary)
            assertTrue(responseBody.isClosedForRead || responseBody.closedCause != null)
        } finally {
            responseBody.cancel()
            transport.close()
        }
    }
}
