package com.simon.harmonichackernews.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class StreamingSummaryTextTest {
    @Test
    fun firstTokenIsImmediateAndBurstIsPublishedEvenIfTheProviderPauses() = runTest {
        val updates = mutableListOf<String>()
        val text = StreamingSummaryText(backgroundScope, 1_000) { updates += it }
        text.append("First")
        assertEquals(listOf("First"), updates)
        runCurrent()
        repeat(100) { text.append("!") }
        advanceTimeBy(49)
        assertEquals(1, updates.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("First", "First" + "!".repeat(100)), updates)
        advanceTimeBy(100)
        runCurrent()
        text.append(" Slow")
        assertEquals("First" + "!".repeat(100) + " Slow", updates.last())
        text.finish()
    }

    @Test
    fun finishIncludesPendingSuffixWithoutWaitingOrPublishingAfterward() = runTest {
        val updates = mutableListOf<String>()
        val text = StreamingSummaryText(backgroundScope, 1_000) { updates += it }
        text.append("First")
        text.append(" final")
        assertEquals("First final", text.finish())
        assertEquals(0L, testScheduler.currentTime)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("First"), updates)
    }

    @Test
    fun cancellationStopsPendingUpdatesAndLimitStillAppliesToEveryChunk() = runTest {
        val job = Job()
        val scope = CoroutineScope(backgroundScope.coroutineContext + job)
        val updates = mutableListOf<String>()
        val text = StreamingSummaryText(scope, 5) { updates += it }
        try {
            text.append("12")
            text.append("345")
            assertFailsWith<CloudSummaryException> { text.append("6") }
            scope.cancel()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(listOf("12"), updates)
        } finally { scope.cancel() }
    }
}
