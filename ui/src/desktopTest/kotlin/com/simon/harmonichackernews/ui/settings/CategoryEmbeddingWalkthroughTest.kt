package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryEmbeddingWalkthroughTest {
    @Test
    fun stepMotionReversesDirectionAndKeepsControlsOutsideTheHorizontalMotion() = SwingUtilities.invokeAndWait {
        val step = mutableIntStateOf(0)
        val scene = ImageComposeScene(360, 1000, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CategoryEmbeddingWalkthrough(step.intValue) { step.intValue = it }
            }
        }
        var time = 0L
        fun frames(count: Int) { repeat(count) { time += 16_000_000L; scene.render(time).close() } }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun nodes() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
        fun textNode(text: String) = nodes().first {
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text }
        }
        fun textBounds(text: String) = textNode(text).boundsInRoot
        try {
            frames(10)
            val restingLeft = textBounds("Turn text into numbers").left
            val previous = textBounds("Previous")
            val next = textBounds("Next")
            step.intValue = 1
            frames(3)
            assertTrue(textBounds("Remove the average").left > restingLeft, "Next should enter from the right")
            assertEquals(previous.left, textBounds("Previous").left)
            assertEquals(next.left, textBounds("Next").left)
            frames(90)
            assertEquals(restingLeft, textBounds("Remove the average").left)
            step.intValue = 0
            frames(3)
            // The container clips the incoming page at its resting left edge; inspect its unclipped position.
            assertTrue(textNode("Turn text into numbers").positionInRoot.x < restingLeft, "Previous should enter from the left")
            assertEquals(previous.left, textBounds("Previous").left)
            assertEquals(next.left, textBounds("Next").left)
            frames(90)
            assertEquals(restingLeft, textBounds("Turn text into numbers").left)
        } finally { scene.close() }
    }

    @Test
    fun walkthroughCanBeReadInBothDirectionsAtNarrowWidthsAndLargeFontSizes() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) {
            val step = mutableIntStateOf(0)
            val width = if (dark) 320 else 360
            val scene = ImageComposeScene(width, 1500, Density(1f, if (dark) 1.5f else 1f)) {
                val palette = HarmonicThemeCatalog.resolve(if (dark) "material_dark" else "material_light", dark)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    CategoryEmbeddingWalkthrough(step.intValue) { step.intValue = it }
                }
            }
            var time = 0L
            fun frame() { time += 16_000_000L; scene.render(time).close() }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            fun nodes() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
            fun texts(node: SemanticsNode) = descendants(node).flatMap {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty()
            }.map { it.text }
            fun button(label: String) = nodes().first {
                it.config.getOrNull(SemanticsProperties.Role) == Role.Button && label in texts(it)
            }
            fun click(label: String) {
                val center = button(label).boundsInRoot.center
                scene.sendPointerEvent(PointerEventType.Press, center, timeMillis = time / 1_000_000)
                scene.sendPointerEvent(PointerEventType.Release, center, timeMillis = time / 1_000_000 + 1)
                repeat(90) { frame() }
            }
            fun verifyPage(index: Int) {
                assertEquals(index, step.intValue)
                assertTrue(nodes().any { "Step ${index + 1} of 4" in texts(it) })
                assertEquals(index == 0, button("Previous").config.contains(SemanticsProperties.Disabled))
                assertEquals(index == 3, button("Next").config.contains(SemanticsProperties.Disabled))
                assertTrue(nodes().any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isNotEmpty() },
                    "Every illustration needs a text alternative")
                nodes().filter { it.config.contains(SemanticsProperties.Text) }.forEach {
                    assertTrue(it.boundsInRoot.left >= 0 && it.boundsInRoot.right <= width,
                        "Walkthrough text must fit a $width dp screen, including large fonts")
                }
            }
            try {
                repeat(10) { frame() }
                verifyPage(0)
                click("Previous")
                verifyPage(0)
                for (index in 1..3) {
                    click("Next")
                    verifyPage(index)
                    if (index == 1) assertTrue(nodes().any { "Remove the average" in texts(it) })
                }
                click("Next")
                verifyPage(3)
                for (index in 2 downTo 0) {
                    click("Previous")
                    verifyPage(index)
                }
            } finally { scene.close() }
        }
    }
}
