package com.simon.harmonichackernews.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopConnectivityTest {
    @Test
    fun observersReceiveDisconnectAndReconnectWithoutReopeningTheScreen() = runTest {
        var online = true
        var reads = 0
        val events = mutableListOf<Boolean>()
        val job = backgroundScope.launch {
            observeDesktopConnectivity { reads++; online }.collect(events::add)
        }
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf(true), events)
        online = false
        advanceTimeBy(1_000)
        runCurrent()
        online = true
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(true, false, true), events)
        job.cancel()
        runCurrent()
        val stoppedAt = reads
        advanceTimeBy(10_000)
        assertEquals(stoppedAt, reads)
    }
}
