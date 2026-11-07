package com.simon.harmonichackernews.platform

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class IosConnectivityStateTest {
    @Test
    fun costChangesAreEmittedEvenWhileTheDeviceStaysOnline() = runTest {
        val state = IosConnectivityState()
        val online = mutableListOf<Boolean>()
        val unmetered = mutableListOf<Boolean>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { state.onlineChanges.toList(online) }
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { state.unmeteredChanges.toList(unmetered) }

        state.update(online = true, unmetered = false)
        runCurrent()
        state.update(online = true, unmetered = true)
        runCurrent()
        assertEquals(listOf(true), online)
        assertEquals(listOf(true, false, true), unmetered)

        // An offline path can never be considered an unmetered connection.
        state.update(online = false, unmetered = true)
        runCurrent()
        state.update(online = true, unmetered = true)
        runCurrent()
        assertEquals(listOf(true, false, true), online)
        assertEquals(listOf(true, false, true, false, true), unmetered)
    }
}
