package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun disablingReaderOrIntegratedBrowserCancelsAPendingReaderRequest() {
        for ((featureEnabled, integrated) in listOf(false to true, true to false)) {
            val reader = ReaderModeStateMachine()
            assertEquals(
                ReaderModeToggleDecision.LoadThenEnable,
                reader.toggle(pageEligible = true, pageReady = false, storyUrlAvailable = true),
            )
            assertTrue(reader.state.pending)

            reader.configure(featureEnabled, integrated, defaultEnabled = false)

            assertFalse(reader.state.pending)
            reader.configure(featureEnabled = true, integrated = true, defaultEnabled = false)
            assertEquals(
                ReaderModePageDecision.CheckAvailability,
                reader.onPageFinished(pageEligible = true),
            )
        }
    }

    @Test
    fun lateScriptResultsCannotReenableReaderAfterItsSettingsAreDisabled() {
        for ((featureEnabled, integrated) in listOf(false to true, true to false)) {
            for (status in listOf(ReaderModeScriptStatus.ENABLED, ReaderModeScriptStatus.DISABLED)) {
                val reader = ReaderModeStateMachine()
                reader.configure(featureEnabled, integrated, defaultEnabled = false)

                reader.applyResult(status)

                assertFalse(reader.state.available)
                assertFalse(reader.state.enabled)
            }
        }
    }
}
