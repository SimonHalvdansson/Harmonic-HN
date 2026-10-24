package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WelcomeHeaderMotionTest {
    @Test
    fun allShapesArriveAndContinueMoving() {
        for (shape in WelcomeHeaderMotion.Shape.entries) {
            assertEquals(0f, WelcomeHeaderMotion.pose(shape, 0.0).alpha)
            val settled = WelcomeHeaderMotion.pose(shape, WelcomeHeaderMotion.SettledMillis.toDouble())
            assertEquals(1f, settled.alpha)
            assertEquals(1f, settled.scale)
            assertEquals(0f, settled.enterY)
            assertNotEquals(settled, WelcomeHeaderMotion.pose(shape, 5000.0))
            for (time in 0..12000 step 10) {
                val p = WelcomeHeaderMotion.pose(shape, time.toDouble())
                assertTrue(p.scale in .65f..1.04f)
                assertTrue(p.alpha in 0f..1f)
                assertTrue(listOf(p.x, p.y, p.angle, p.enterAngle, p.enterY).all { it.isFinite() })
            }
        }
    }

    @Test
    fun reducedMotionShowsTheCompleteStaticHeader() {
        for (shape in WelcomeHeaderMotion.Shape.entries) {
            assertEquals(WelcomeHeaderMotion.Pose(1f, 1f, 0f, 0f, 0f, 0f, 0f), WelcomeHeaderMotion.pose(shape, -1.0))
        }
        assertEquals(1f, WelcomeHeaderMotion.textAlpha(-1.0))
        for (order in -1..3) {
            assertEquals(1f, WelcomeHeaderMotion.contentAlpha(-1.0, order))
        }
    }

    @Test
    fun contentFadesInFromTopToBottomAndStaysVisible() {
        val duringEntrance = (0..3).map { WelcomeHeaderMotion.contentAlpha(650.0, it) }
        assertTrue(duringEntrance.zipWithNext().all { (above, below) -> above > below })
        for (order in 0..3) {
            assertEquals(0f, WelcomeHeaderMotion.contentAlpha(0.0, order))
            assertEquals(1f, WelcomeHeaderMotion.contentAlpha(1000.0, order))
            assertEquals(1f, WelcomeHeaderMotion.contentAlpha(5000.0, order))
        }
    }

    @Test
    fun logoWavesEaseIntoSubtleIndependentMotion() {
        for (index in 0..1) {
            assertEquals(0f, WelcomeHeaderMotion.logoBend(index, -1.0))
            assertEquals(0f, WelcomeHeaderMotion.logoBend(index, 0.0))
            assertEquals(0f, WelcomeHeaderMotion.logoBend(index, WelcomeHeaderMotion.SettledMillis.toDouble()))
            assertEquals(0f, WelcomeHeaderMotion.logoBend(index, WelcomeHeaderMotion.SettledMillis + .01), .000001f)
            for (time in 0..20000 step 10) {
                assertTrue(WelcomeHeaderMotion.logoBend(index, time.toDouble()) in -6f..6f)
            }
            assertNotEquals(WelcomeHeaderMotion.logoBend(index, 5000.0), WelcomeHeaderMotion.logoBend(index, 7000.0))
        }
        assertNotEquals(WelcomeHeaderMotion.logoBend(0, 5000.0), WelcomeHeaderMotion.logoBend(1, 5000.0))
    }

    @Test
    fun nativeMotionMatchesTheWebStudyWithTheSelectedDefaults() {
        val bloom = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, 5000.0)
        assertEquals(-1.431045f, bloom.y, .00001f)
        assertEquals(29.417143f, bloom.angle, .00001f)
        val conversation = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Conversation, 5000.0)
        assertEquals(1.839915f, conversation.x, .00001f)
        assertEquals(-.576950f, conversation.y, .00001f)
        assertEquals(-2.044350f, conversation.angle, .00001f)
        assertEquals(1440f, WelcomeHeaderMotion.SettledMillis)
    }

    @Test
    fun bloomTakes35SecondsPerTurnAfterItsSmoothStart() {
        val t = WelcomeHeaderMotion.SettledMillis + 1400.0
        val a = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, t)
        val b = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, t + 35000)
        assertEquals(360f, b.angle - a.angle, .0001f)
        val start = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, WelcomeHeaderMotion.SettledMillis.toDouble())
        val after = WelcomeHeaderMotion.pose(WelcomeHeaderMotion.Shape.Bloom, WelcomeHeaderMotion.SettledMillis + .01)
        assertEquals(start.angle, after.angle, .000001f)
        assertEquals(start.y, after.y, .000001f)
    }
}
