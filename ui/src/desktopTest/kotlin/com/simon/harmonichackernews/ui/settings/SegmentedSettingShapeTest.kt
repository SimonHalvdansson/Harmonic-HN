package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import javax.swing.SwingUtilities

class SegmentedSettingShapeTest {
    @Test
    fun normalOptionsKeepTheOriginalFortyDpHeightWhileLargeTextCanGrow() = SwingUtilities.invokeAndWait {
        for (scale in listOf(1f, 2f)) {
            val scene = ImageComposeScene(360, 240, Density(1f, scale)) {
                MaterialTheme {
                    SegmentedSetting(options = listOf(0 to "Under title", 1 to "Metadata"), selected = 0, onSelected = {})
                }
            }
            try {
                repeat(12) { scene.render(it * 16_666_667L).close() }
                fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
                val nodes = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                val options = nodes.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                assertEquals(2, options.size)
                options.forEach {
                    if (scale == 1f) assertEquals(40f, it.boundsInRoot.height, .1f,
                        "Normal button groups must retain their original visual height")
                    else assertTrue(it.boundsInRoot.height >= 40f)
                }
                nodes.forEach {
                    val layouts = mutableListOf<TextLayoutResult>()
                    it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                    layouts.forEach { result -> assertTextContentFits(result, "${result.layoutInput.text.text} at ${scale}x") }
                }
            } finally { scene.close() }
        }
    }
    @BeforeTest
    fun initializeDesktopGraphics() {
        ImageComposeScene(1, 1).close()
    }

    @Test
    fun springOvershootDoesNotShrinkTheFixedOuterCorners() {
        for (first in listOf(true, false)) {
            for (innerRadius in listOf(20f, 22f, 20.5f, 20f)) {
                val shape = segmentedSettingShape(first, !first, 20.dp, innerRadius.dp, 40.dp)
                val outline = shape.createOutline(Size(200f, 40f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
                assertEquals(20f, outline.roundRect.topLeftCornerRadius.x)
                assertEquals(20f, outline.roundRect.topRightCornerRadius.x)
            }
        }
    }

    @Test
    fun normalPressAndSelectionMorphsKeepTheirCorners() {
        for (innerRadius in listOf(4f, 8f, 15f)) {
            val shape = segmentedSettingShape(true, false, 20.dp, innerRadius.dp, 40.dp)
            val outline = shape.createOutline(Size(200f, 40f), LayoutDirection.Ltr, Density(1f)) as Outline.Rounded
            assertEquals(20f, outline.roundRect.topLeftCornerRadius.x)
            assertEquals(innerRadius, outline.roundRect.topRightCornerRadius.x)
        }
    }
}
