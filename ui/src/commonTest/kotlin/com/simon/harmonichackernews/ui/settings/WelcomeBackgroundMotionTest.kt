package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WelcomeBackgroundMotionTest {
    @Test
    fun glowFieldsDriftAndPulseIndependentlyWithinSubtleBounds() {
        for (index in 0..1) {
            for (millis in 0..120000 step 100) {
                val field = WelcomeBackgroundMotion.field(index, millis.toDouble())
                assertTrue(field.x in -10f..10f && field.y in -14f..14f)
                assertTrue(field.scale in .94f..1.06f && field.alpha in .7f..1f)
            }
            assertNotEquals(WelcomeBackgroundMotion.field(index, 0.0), WelcomeBackgroundMotion.field(index, 5000.0))
            assertEquals(WelcomeBackgroundMotion.field(index, 0.0), WelcomeBackgroundMotion.field(index, -1.0))
        }
        assertNotEquals(WelcomeBackgroundMotion.field(0, 5000.0), WelcomeBackgroundMotion.field(1, 5000.0))
    }
}
