package com.simon.harmonichackernews

import androidx.datastore.core.CorruptionException
import com.simon.harmonichackernews.settings.DataStoreKeyValueStore
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
                startup.retry()
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
                startup.retry()
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

    @Test
    fun retryCanReopenRepairedPreferencesWithoutDiscardingDataOrPublishingAPartialGraph() = runBlocking {
        val directory = Files.createTempDirectory("harmonic-startup-recovery-")
        val file = directory.resolve("settings.preferences_pb")
        val seedJob = SupervisorJob()
        val startupJob = SupervisorJob()
        val scope = CoroutineScope(startupJob + Dispatchers.IO)
        try {
            val seed = DataStoreKeyValueStore.openFile(file.toString(), CoroutineScope(seedJob + Dispatchers.IO))
            seed.putString("saved", "kept")
            seed.awaitPersisted()
            seedJob.cancelAndJoin()
            val validBytes = Files.readAllBytes(file)
            val corruptBytes = byteArrayOf(0xff.toByte(), 0xff.toByte())
            Files.write(file, corruptBytes)
            val calls = AtomicInteger()
            val startup = AndroidAppStartup(scope) {
                calls.incrementAndGet()
                DataStoreKeyValueStore.openFile(file.toString(), scope)
            }
            startup.start()
            assertTrue(withTimeout(5_000) { runCatching { startup.await() }.exceptionOrNull() } is CorruptionException)
            assertNull(startup.valueOrNull)
            assertArrayEquals(corruptBytes, Files.readAllBytes(file))

            Files.write(file, validBytes)
            repeat(3) { startup.retry() }
            val store = withTimeout(5_000) { startup.await() }
            assertSame(store, startup.value)
            assertEquals("kept", store.getString("saved"))
            assertEquals(2, calls.get())
        } finally {
            seedJob.cancelAndJoin()
            startupJob.cancelAndJoin()
            directory.toFile().deleteRecursively()
        }
    }
}
