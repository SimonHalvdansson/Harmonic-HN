package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderModeStateMachineTest {
    @Test
    fun eligiblePageLoadsHideReaderModeUntilAvailabilityIsRechecked() {
        val reader = ReaderModeStateMachine()
        reader.configure(
            featureEnabled = true,
            integrated = true,
            defaultEnabled = false,
        )

        reader.confirmAvailable()
        assertTrue(reader.state.available)

        reader.onEligiblePageLoadStarted()

        assertFalse(reader.state.available)

        reader.confirmAvailable()
        assertTrue(reader.state.available)
    }
}
