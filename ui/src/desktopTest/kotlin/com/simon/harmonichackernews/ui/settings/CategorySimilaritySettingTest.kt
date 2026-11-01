package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategorySimilaritySettingTest {
    @Test
    fun accessibleStepsUseWholePointsWhileResetFramesStaySmooth() = SwingUtilities.invokeAndWait {
        val cutoff = mutableFloatStateOf(0.78f)
        val scene = ImageComposeScene(400, 200, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CategorySimilaritySetting(cutoff.floatValue,
                    onValueChange = { cutoff.floatValue = (it * 100).roundToInt() / 100f },
                    onReset = {}, resetEnabled = true)
            }
        }
        var time = 0L
        fun frames() { repeat(5) { time += 16_000_000L; scene.render(time).close() } }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun slider() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.first {
            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null
        }
        try {
            frames()
            val range = slider().config[SemanticsProperties.ProgressBarRangeInfo]
            assertEquals(0.75f..0.90f, range.range)
            assertEquals(14, range.steps)
            assertTrue(slider().config[SemanticsActions.SetProgress].action!!.invoke(0.79f))
            frames()
            assertTrue(abs(cutoff.floatValue - 0.79f) < 0.0001f)
            // A sampled reset-animation frame must not snap to the nearest whole point.
            cutoff.floatValue = 0.785f
            frames()
            assertTrue(abs(slider().config[SemanticsProperties.ProgressBarRangeInfo].current - 0.785f) < 0.0001f)
        } finally { scene.close() }
    }
}
