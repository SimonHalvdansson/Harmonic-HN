package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class NetworkTransportTest {
    @Test
    fun constructingAndClosingUnusedGraphDoesNotCreateEngine() = runTest {
        var creations = 0
        val graph = NetworkGraphFactory.create(NetworkGraphEnvironment(
            scope = backgroundScope,
            userAgent = "test",
            engine = { creations++; MockEngine { respond("42") } },
            transportDispatcher = StandardTestDispatcher(testScheduler),
        ))
        graph.close()
        assertEquals(0, creations)
        assertFailsWith<IllegalStateException> { graph.hackerNewsApi.getMaxItemId() }
        assertEquals(0, creations)
    }

    @Test
    fun concurrentFirstRequestsSuspendUntilDispatcherRunsAndShareTransport() = runTest {
        var creations = 0
        val graph = NetworkGraphFactory.create(NetworkGraphEnvironment(
            scope = backgroundScope,
            userAgent = "test",
            engine = { creations++; MockEngine { respond("42") } },
            transportDispatcher = StandardTestDispatcher(testScheduler),
        ))
        try {
            val requests = List(8) {
                async(start = CoroutineStart.UNDISPATCHED) { graph.hackerNewsApi.getMaxItemId() }
            }
            assertEquals(0, creations)
            assertFalse(requests.any { it.isCompleted })
            assertEquals(List(8) { 42 }, requests.awaitAll())
            assertEquals(1, creations)
            // Derived clients retain deferred transport access and can issue real requests.
            graph.httpClient.newBuilder().readTimeoutMillis(1000).build()
                .execute(HttpRequest.Builder().url("https://example.test/value").build())
                .use { assertEquals("42", it.body.readText()) }
            assertEquals(1, creations)
        } finally {
            graph.close()
        }
    }

    @Test
    fun nativeAccessAndSuspendAccessShareClient() = runTest {
        val client = HttpClient(MockEngine { respond("42") })
        val transport = NetworkTransport(StandardTestDispatcher(testScheduler)) { client }
        try {
            assertSame(client, transport.get())
            assertSame(client, transport.await())
        } finally {
            transport.close()
        }
        assertFailsWith<IllegalStateException> { transport.get() }
    }

    @Test
    fun closingDuringConstructionDoesNotPublishClient() = runTest {
        lateinit var transport: NetworkTransport
        val client = HttpClient(MockEngine { respond("42") })
        transport = NetworkTransport(StandardTestDispatcher(testScheduler)) {
            transport.close()
            client
        }
        assertFailsWith<IllegalStateException> { transport.await() }
        assertFailsWith<IllegalStateException> { transport.get() }
    }
}
