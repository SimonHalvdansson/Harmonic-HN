@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SettingsDialogSizeAnimationTest {
    @Test
    fun measuredContentGrowsAndShrinksThroughIntermediateHeights() = SwingUtilities.invokeAndWait {
        val requestedHeight = mutableStateOf(100.dp)
        val harness = Harness {
            Box(Modifier.testTag("card").animateSettingsDialogContentSize()) {
                Box(Modifier.width(200.dp).height(requestedHeight.value))
            }
        }
        try {
            harness.frames()
            assertEquals(100f, harness.cardBounds().height)
            requestedHeight.value = 300.dp
            harness.frames(8)
            val growing = harness.cardBounds().height
            assertTrue(growing > 100f && growing < 300f, "Expected an intermediate growing height: $growing")
            harness.frames(30)
            assertEquals(300f, harness.cardBounds().height)

            requestedHeight.value = 60.dp
            harness.frames(8)
            val shrinking = harness.cardBounds().height
            assertTrue(shrinking > 60f && shrinking < 300f, "Expected an intermediate shrinking height: $shrinking")
            harness.frames(30)
            assertEquals(60f, harness.cardBounds().height)
        } finally {
            harness.close()
        }
    }

    @Test
    fun changingAvailableSpaceSnapsAnOngoingAnimationAndDoesNotChaseInsets() = SwingUtilities.invokeAndWait {
        val requestedHeight = mutableStateOf(100.dp)
        val harness = Harness {
            Box(Modifier.testTag("card").animateSettingsDialogContentSize()) {
                Box(Modifier.width(200.dp).height(requestedHeight.value))
            }
        }
        try {
            harness.frames()
            requestedHeight.value = 300.dp
            harness.frames(8)
            assertTrue(harness.cardBounds().height in 101f..299f)

            // IME/window insets reach this modifier as changed measurement constraints.
            harness.resize(140, 120)
            harness.frames(1)
            assertEquals(Rect(0f, 0f, 140f, 120f), harness.cardBounds())
            harness.frames(30)
            assertEquals(Rect(0f, 0f, 140f, 120f), harness.cardBounds())

            harness.resize(400, 800)
            harness.frames(1)
            assertEquals(Rect(0f, 0f, 200f, 300f), harness.cardBounds())
            harness.frames(30)
            assertEquals(Rect(0f, 0f, 200f, 300f), harness.cardBounds())
        } finally {
            harness.close()
        }
    }

    @Test
    fun resizingRetainsTheChildCompositionAndRememberedState() = SwingUtilities.invokeAndWait {
        val requestedHeight = mutableStateOf(100.dp)
        var childState: MutableState<Int>? = null
        var disposals = 0
        val harness = Harness {
            Box(Modifier.testTag("card").animateSettingsDialogContentSize()) {
                childState = remember { mutableStateOf(0) }
                DisposableEffect(Unit) { onDispose { disposals++ } }
                Box(Modifier.width(200.dp).height(requestedHeight.value))
            }
        }
        try {
            harness.frames()
            val original = childState!!
            original.value = 7
            requestedHeight.value = 300.dp
            harness.frames(8)
            harness.resize(180, 120)
            harness.frames(2)
            harness.resize(400, 800)
            harness.frames(30)
            assertSame(original, childState)
            assertEquals(7, childState!!.value)
            assertEquals(0, disposals, "Measurement changes must not rebuild the child composition")
        } finally {
            harness.close()
        }
        assertEquals(1, disposals)
    }

    @Test
    fun disabledMotionSettlesContentChangesWithoutDelayedResizing() = SwingUtilities.invokeAndWait {
        val requestedHeight = mutableStateOf(100.dp)
        val harness = Harness(motionScale = 0f) {
            Box(Modifier.testTag("card").animateSettingsDialogContentSize()) {
                Box(Modifier.width(200.dp).height(requestedHeight.value))
            }
        }
        try {
            harness.frames()
            requestedHeight.value = 300.dp
            harness.frames(3)
            assertEquals(300f, harness.cardBounds().height)
            requestedHeight.value = 60.dp
            harness.frames(3)
            val settled = harness.cardBounds()
            assertEquals(60f, settled.height)
            harness.frames(30)
            assertEquals(settled, harness.cardBounds())
        } finally {
            harness.close()
        }
    }

    private class Harness(motionScale: Float = 1f, content: @Composable () -> Unit) {
        private var time = 0L
        private val durationScale = object : MotionDurationScale { override val scaleFactor = motionScale }
        private val scene = ImageComposeScene(
            400, 800, coroutineContext = Dispatchers.Unconfined + durationScale,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) { content() }
        }

        fun frames(count: Int = 5) {
            repeat(count) {
                scene.render(time).close()
                time += 16_666_667L
            }
        }

        fun resize(width: Int, height: Int) {
            scene.constraints = Constraints(maxWidth = width, maxHeight = height)
        }

        fun cardBounds(): Rect = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsProperties.TestTag) == "card" }.boundsInRoot

        fun close() = scene.close()

        private fun descendants(node: SemanticsNode): List<SemanticsNode> =
            listOf(node) + node.children.flatMap(::descendants)
    }
}
