package com.simon.harmonichackernews.platform

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class UnavailablePlatformCapabilitiesTest {
    @Test
    fun localSummaryReportsUnavailableAndFailsExplicitlyWhenInvoked() = runTest {
        val engine = UnavailableLocalSummaryEngine("No test runtime")

        assertFalse(engine.isAvailable())
        val error = assertFailsWith<PlatformCapabilityUnavailableException> {
            engine.summarize(SummaryRequest("text"))
        }
        assertEquals("Local summaries", error.capability)
        assertEquals("No test runtime", error.reason)
    }
}
