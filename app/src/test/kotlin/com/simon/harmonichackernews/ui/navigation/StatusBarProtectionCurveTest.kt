package com.simon.harmonichackernews.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarProtectionCurveTest {
    @Test
    fun fadeStartsVisibleAndDecreasesToTransparentForDifferentInsets() {
        for (inset in listOf(0f, 8f, 24f, 40f, 64f)) {
            val join = inset / (inset + 4f)
            var previous = statusBarProtectionAlpha(0f, join)
            assertTrue("Visible protection at inset=$inset", previous > 0f && previous <= 1f)
            for (step in 1..100) {
                val alpha = statusBarProtectionAlpha(step / 100f, join)
                assertTrue("inset=$inset step=$step alpha=$alpha", alpha in 0f..previous)
                previous = alpha
            }
            assertEquals("Transparent end at inset=$inset", 0f, previous, 0f)
        }
    }
}
