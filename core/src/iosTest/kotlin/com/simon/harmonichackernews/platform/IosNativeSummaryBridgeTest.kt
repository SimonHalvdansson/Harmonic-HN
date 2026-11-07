package com.simon.harmonichackernews.platform

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosNativeSummaryBridgeTest {
    private val request = SummaryRequest(text = "Article text", preserveInput = true)

    @Test
    fun cancellationStopsNativeTaskAndIgnoresLateResult() = runTest {
        val bridge = StubBridge()
        val engine = IosNativeLocalSummaryEngine(bridge)
        val first = async { engine.summarize(request) }
        runCurrent()
        val cancelled = bridge.tasks.single()

        first.cancelAndJoin()
        assertTrue(cancelled.cancelled)
        cancelled.callback.complete("Stale summary", null)
        assertTrue(first.isCancelled)

        val next = async { engine.summarize(request) }
        runCurrent()
        val active = bridge.tasks.last()
        assertFalse(active.cancelled)
        active.callback.complete("New summary", null)
        assertEquals("New summary", next.await().text)
        assertFalse(active.cancelled)
    }

    @Test
    fun cancellationDuringNativeStartupCancelsReturnedHandle() = runTest {
        val bridge = StubBridge()
        val engine = IosNativeLocalSummaryEngine(bridge)
        val job = launch(start = CoroutineStart.LAZY) { engine.summarize(request) }
        bridge.onStart = { job.cancel() }

        job.start()
        runCurrent()
        job.join()

        assertTrue(job.isCancelled)
        assertTrue(bridge.tasks.single().cancelled)
    }

    @Test
    fun synchronousCompletionDoesNotCancelSuccessfulTask() = runTest {
        val bridge = StubBridge()
        bridge.onStart = { bridge.tasks.last().callback.complete("Immediate summary", null) }

        assertEquals("Immediate summary", IosNativeLocalSummaryEngine(bridge).summarize(request).text)
        assertFalse(bridge.tasks.single().cancelled)
    }

    private class StubTask(val callback: IosNativeSummaryCallback) : IosNativeSummaryTask {
        var cancelled = false
        override fun cancel() { cancelled = true }
    }

    private class StubBridge : IosNativeSummaryBridge {
        val tasks = mutableListOf<StubTask>()
        var onStart: () -> Unit = {}
        override fun canAttempt() = true
        override fun isAvailable() = true
        override fun availabilityMessage(): String? = null

        override fun summarize(
            text: String,
            instruction: String,
            callback: IosNativeSummaryCallback,
        ): IosNativeSummaryTask = StubTask(callback).also {
            tasks += it
            onStart()
        }
    }
}
