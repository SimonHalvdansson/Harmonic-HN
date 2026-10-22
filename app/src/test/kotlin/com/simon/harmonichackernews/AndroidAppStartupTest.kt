package com.simon.harmonichackernews

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AndroidAppStartupTest {
    @Test
    fun slowInitializationDoesNotBlockCallersAndSurvivesACancelledActivityWait() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val scope = CoroutineScope(SupervisorJob() + dispatcher)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val calls = AtomicInteger()
            val caller = Thread.currentThread()
            val graph = Any()
            val startup = AndroidAppStartup(scope, dispatcher) {
                assertNotSame(caller, Thread.currentThread())
                calls.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                graph
            }
            try {
                startup.start()
                assertTrue(entered.await(5, TimeUnit.SECONDS))
                assertNull(startup.valueOrNull)
                assertThrows(IllegalStateException::class.java) { startup.value }
                val activity = async(start = CoroutineStart.UNDISPATCHED) { startup.await() }
                val worker = async(start = CoroutineStart.UNDISPATCHED) { startup.await() }
                activity.cancelAndJoin()
                assertFalse(worker.isCompleted)
                release.countDown()
                assertSame(graph, withTimeout(5_000) { worker.await() })
                assertSame(graph, startup.value)
                assertSame(graph, startup.await())
                assertEquals(1, calls.get())
            } finally {
                release.countDown()
                scope.cancel()
            }
        }
    }

    @Test
    fun failedInitializationIsReportedWithoutPublishingAPartialGraph() = runBlocking {
        val scope = CoroutineScope(SupervisorJob())
        val failure = IllegalStateException("storage unavailable")
        val startup = AndroidAppStartup<Any>(scope) { throw failure }
        try {
            repeat(2) {
                val result = runCatching { startup.await() }
                assertEquals(failure::class, result.exceptionOrNull()?.let { it::class })
                assertEquals(failure.message, result.exceptionOrNull()?.message)
                assertNull(startup.valueOrNull)
            }
        } finally {
            scope.cancel()
        }
    }
}
