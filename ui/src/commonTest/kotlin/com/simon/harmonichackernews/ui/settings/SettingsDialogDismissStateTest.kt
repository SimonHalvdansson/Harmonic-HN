package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDialogDismissStateTest {
    @Test
    fun parentCloseCancelsAnAlreadyResumedChildHandoff() {
        val child = SettingsDialogDismissState()
        val parent = SettingsDialogDismissState()
        var opened = 0
        var closed = 0
        val open = { opened++; Unit }
        val close = { closed++; Unit }
        child.request(open)
        assertTrue(child.isClosing)
        child.cancel()
        parent.request(close)
        // The old coroutine can finish its current IME frame after cancellation.
        child.complete(open)
        parent.complete(close)
        assertEquals(0, opened)
        assertEquals(1, closed)
        assertFalse(child.isClosing)
    }

    @Test
    fun duplicateChildRequestsOpenOnlyTheFirstAndCanBeRequestedAgainAfterCompletion() {
        val state = SettingsDialogDismissState()
        var opened = 0
        val first = { opened++; Unit }
        state.request(first)
        state.request { opened += 10 }
        state.complete(first)
        state.complete(first)
        assertEquals(1, opened)
        val next = { opened++; Unit }
        state.request(next)
        state.complete(next)
        assertEquals(2, opened)
    }
}
