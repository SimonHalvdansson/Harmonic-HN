package com.simon.harmonichackernews.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HarmonicHttpClientOwnershipTest {
    @Test
    fun closingLastClientClosesItsOwnedEngine() = runTest {
        val engine = MockEngine { respond("ok") }
        val client = createHarmonicHttpClient(engine, "test")
        val copy = client.config { }
        client.close()
        client.coroutineContext[Job]!!.join()
        assertTrue(engine.coroutineContext[Job]!!.isActive)
        copy.close()
        copy.coroutineContext[Job]!!.join()
        engine.coroutineContext[Job]!!.join()
        assertFalse(engine.coroutineContext[Job]!!.isActive)
    }
}
