package com.simon.harmonichackernews.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.window.Dialog
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopMotionPreferencesTest {
    @Test
    fun liveOsMotionScaleReachesTheMainCompositionAndNestedDialogs() = SwingUtilities.invokeAndWait {
        var systemScale = 0f
        val motion = DesktopMotionDurationScale { systemScale }
        val target = mutableStateOf(0f)
        lateinit var animated: State<Float>
        var rootScale: MotionDurationScale? = null
        var dialogScale: MotionDurationScale? = null
        val scene = ImageComposeScene(240, 180, coroutineContext = Dispatchers.Unconfined + motion) {
            animated = animateFloatAsState(target.value, tween(400), label = "native motion test")
            LaunchedEffect(Unit) { rootScale = currentCoroutineContext()[MotionDurationScale] }
            Dialog(onDismissRequest = {}) {
                LaunchedEffect(Unit) { dialogScale = currentCoroutineContext()[MotionDurationScale] }
                Text("Nested dialog")
            }
        }
        var time = 0L
        fun frames(count: Int = 6) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        fun samples(count: Int): List<Float> = List(count) { frames(1); animated.value }
        try {
            frames()
            assertSame(motion, rootScale)
            assertSame(motion, dialogScale)
            target.value = 1f
            // Target recomposition and animateAsState's conflated channel need startup frames.
            // Sample the animation State directly, without waiting for another SideEffect copy.
            // Eight frames (~133ms) cannot complete the ordinary 400ms animation.
            val reduced = samples(8)
            assertEquals(1f, reduced.last(), "OS reduced motion must settle before a normal tween can finish: $reduced")
            assertTrue(reduced.all { it == 0f || it == 1f }, "Reduced motion must snap without intermediate values: $reduced")

            systemScale = 1f
            motion.refresh()
            target.value = 0f
            val enabled = samples(8)
            assertTrue(enabled.last() > 0f && enabled.last() < 1f,
                "Turning OS animations on must resume normal animation: $enabled")

            systemScale = 0f
            motion.refresh()
            val stopped = samples(2)
            assertEquals(0f, stopped.last(), "A live OS change must stop the running animation on its next frame: $stopped")
            assertEquals(0f, dialogScale!!.scaleFactor)
        } finally { scene.close() }
    }

    @Test
    fun anUnavailablePreferenceDoesNotReenableMotionAfterItWasDisabled() {
        var next: Float? = 0f
        val motion = DesktopMotionDurationScale { next }
        next = null
        motion.refresh()
        assertEquals(0f, motion.scaleFactor)
        next = Float.NaN
        motion.refresh()
        assertEquals(0f, motion.scaleFactor)
        next = 1f
        motion.refresh()
        assertEquals(1f, motion.scaleFactor)
    }

    @Test
    fun linuxNativePreferenceFormatsPreserveDisabledAndSlowAnimationValues() {
        assertEquals(0f, gnomeAnimationScale("false\n"))
        assertEquals(1f, gnomeAnimationScale("true"))
        assertNull(gnomeAnimationScale("No such schema"))
        assertEquals(0f, kdeAnimationScale("[Other]\nAnimationDurationFactor=2\n[KDE]\nAnimationDurationFactor=0\n"))
        assertEquals(1.5f, kdeAnimationScale("[KDE]\nAnimationDurationFactor=1.5"))
        assertNull(kdeAnimationScale("[Other]\nAnimationDurationFactor=0"))
        assertNull(kdeAnimationScale("[KDE]\nAnimationDurationFactor=-1"))
    }

    @Test
    fun theMacNativePreferenceCanBeReadWithoutChangingTheSetting() {
        if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return
        val scale = assertNotNull(DesktopMotionPreferences.readScale())
        assertTrue(scale == 0f || scale == 1f)
    }
}
