package com.simon.harmonichackernews.summary.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiMemoryPolicyTest {
    @Test
    fun backgroundingReleasesIdleEmbeddingsWithoutInterruptingActiveLlmWork() {
        assertTrue(LocalAiMemoryPolicy.background(20))
        assertFalse(LocalAiMemoryPolicy.pressure(20))
    }

    @Test
    fun runningLowAndBackgroundPressureStopLocalWork() {
        for (level in listOf(10, 12, 15, 40, 60, 80)) assertTrue(LocalAiMemoryPolicy.pressure(level))
        for (level in listOf(0, 5, 20)) assertFalse(LocalAiMemoryPolicy.pressure(level))
    }
}
