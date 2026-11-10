package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.CloudEndpointAccess
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CloudEndpointAccessTest {
    @Test
    fun bothResponseModesWaitForAccessBeforeSendingTheRequest() = runTest {
        for (stream in listOf(false, true)) {
            var requests = 0
            val entered = CompletableDeferred<String>()
            val granted = CompletableDeferred<Unit>()
            val transport = HttpClient(MockEngine {
                requests++
                if (stream) respond(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"Summary\"}}]}\n\ndata: [DONE]\n\n",
                    headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
                ) else respond(
                    """{"choices":[{"message":{"content":"Summary"}}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            })
            try {
                val repository = KtorCloudSummaryRepository(
                    KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler),
                    CloudEndpointAccess { entered.complete(it); granted.await() },
                )
                val result = async {
                    repository.summarize(config.copy(streamResponses = stream), "Text").toList()
                }
                assertEquals(config.baseUrl, entered.await())
                assertEquals(0, requests)
                granted.complete(Unit)
                assertTrue(result.await().last() is CloudSummaryEvent.Success)
                assertEquals(1, requests)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun deniedModelLookupAndSummaryNeverReachTransport() = runTest {
        val transport = HttpClient(MockEngine { error("Permission denied: must not send HTTP") })
        try {
            val repository = KtorCloudSummaryRepository(
                KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler),
                CloudEndpointAccess { throw CloudSummaryException("Local network access is required") },
            )
            assertFailsWith<CloudSummaryException> { repository.fetchModelIds(config.baseUrl, "key") }
            val error = assertFailsWith<CloudSummaryException> { repository.summarize(config, "Text").toList() }
            assertEquals("Local network access is required", error.message)
        } finally {
            transport.close()
        }
    }

    @Test
    fun cancellingWhileAwaitingPermissionDoesNotSendARequest() = runTest {
        val transport = HttpClient(MockEngine { error("Cancelled: must not send HTTP") })
        try {
            val entered = CompletableDeferred<Unit>()
            val repository = KtorCloudSummaryRepository(
                KtorHttpClient(transport), "test", StandardTestDispatcher(testScheduler),
                CloudEndpointAccess { entered.complete(Unit); CompletableDeferred<Unit>().await() },
            )
            val result = async { repository.summarize(config, "Text").toList() }
            entered.await()
            result.cancelAndJoin()
            runCurrent()
            assertTrue(result.isCancelled)
        } finally {
            transport.close()
        }
    }

    private val config = CloudSummaryConfig("http://192.168.1.2/v1", "key", "test-model")
}
