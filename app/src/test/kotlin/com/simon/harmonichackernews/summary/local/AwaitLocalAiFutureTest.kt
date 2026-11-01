package com.simon.harmonichackernews.summary.local

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class AwaitLocalAiFutureTest {
    @Test
    fun cancellationInterruptsTheWaitAndCancelsTheNativeRequest() = runBlocking {
        val entered = CountDownLatch(1)
        val future = object : CompletableFuture<String>() {
            override fun get(): String {
                entered.countDown()
                return super.get()
            }
        }
        val request = launch { awaitLocalAiFuture(future) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(5, TimeUnit.SECONDS) })
            withTimeout(5_000) { request.cancelAndJoin() }
            assertTrue(future.isCancelled)
        } finally {
            future.cancel(true)
            request.cancelAndJoin()
        }
    }
}
