package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSummaryStreamingTest {
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
