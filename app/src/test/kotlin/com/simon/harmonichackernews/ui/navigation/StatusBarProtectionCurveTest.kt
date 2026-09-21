package com.simon.harmonichackernews.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarProtectionCurveTest {
    @Test
    fun softenedFadePassesThroughReferencePoints() {
        val join = 24f / 28f
        assertEquals(0.92f, statusBarProtectionAlpha(0f, join), 0.00001f)
        assertEquals(0.5175f, statusBarProtectionAlpha(0.5f, join), 0.00001f)
        assertEquals(0.06475635f, statusBarProtectionAlpha(join, join), 0.00001f)
        assertEquals(0.00957934f, statusBarProtectionAlpha(26f / 28f, join), 0.00001f)
        assertEquals(0f, statusBarProtectionAlpha(1f, join), 0f)
    }

    @Test
    fun fadeIsBoundedAndMonotoneForDifferentInsets() {
        for (inset in listOf(0f, 8f, 24f, 40f, 64f)) {
            val join = inset / (inset + 4f)
            var previous = 0.92f
            for (step in 0..1000) {
                val alpha = statusBarProtectionAlpha(step / 1000f, join)
                assertTrue("inset=$inset step=$step alpha=$alpha", alpha in 0f..previous)
                previous = alpha
            }
        }
    }

    @Test
    fun slopesMatchAtInsetAndTailEndsFlat() {
        val delta = 0.0001f
        for (inset in listOf(8f, 24f, 40f, 64f)) {
            val join = inset / (inset + 4f)
            val atJoin = statusBarProtectionAlpha(join, join)
            val leftSlope = (atJoin - statusBarProtectionAlpha(join - delta, join)) / delta
            val rightSlope = (statusBarProtectionAlpha(join + delta, join) - atJoin) / delta
            assertEquals("Slope at inset=$inset", leftSlope, rightSlope, 0.02f)
            val endSlope = -statusBarProtectionAlpha(1f - delta, join) / delta
            assertEquals("Ending slope at inset=$inset", 0f, endSlope, 0.02f)
        }
    }

    @Test
    fun transparentEndHasNoCurvatureJump() {
        val delta = 0.0001f
        for (inset in listOf(0f, 8f, 24f, 40f, 64f)) {
            val join = inset / (inset + 4f)
            val curvature = (statusBarProtectionAlpha(1f - 2f * delta, join) -
                2f * statusBarProtectionAlpha(1f - delta, join)) / (delta * delta)
            assertEquals("Ending curvature at inset=$inset", 0f, curvature, 0.01f)
        }
    }
}
