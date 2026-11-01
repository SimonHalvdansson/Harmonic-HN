package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.categorization.ArticleCategories
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategorySimilaritySettingTest {
    @Test
    fun accessibleStepsUseWholePointsWhileResetFramesStaySmooth() = SwingUtilities.invokeAndWait {
        val cutoff = mutableFloatStateOf(ArticleCategories.DEFAULT_CUTOFF)
        val scene = ImageComposeScene(400, 300, Density(1f)) {
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
            assertEquals(ArticleCategories.MIN_CUTOFF..ArticleCategories.MAX_CUTOFF, range.range)
            assertEquals(34, range.steps)
            assertTrue(slider().config[SemanticsActions.SetProgress].action!!.invoke(0.25f))
            frames()
            assertTrue(abs(cutoff.floatValue - 0.25f) < 0.0001f)
            // A sampled reset-animation frame must not snap to the nearest whole point.
            cutoff.floatValue = 0.245f
            frames()
            assertTrue(abs(slider().config[SemanticsProperties.ProgressBarRangeInfo].current - 0.245f) < 0.0001f)
        } finally { scene.close() }
    }

    @Test
    fun bundledCurveAppearsWithoutBrowsingOrExplanatoryText() = SwingUtilities.invokeAndWait {
        val evidence = System.getenv("HARMONIC_CATEGORY_EVIDENCE_DIR")?.let { File(it).apply { mkdirs() } }
        val scene = ImageComposeScene(400, 160, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CategorySimilaritySetting(ArticleCategories.DEFAULT_CUTOFF, {}, {}, true)
            }
        }
        var time = 0L
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        try {
            repeat(5) { time += 16_000_000L; scene.render(time).close() }
            val texts = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.flatMap {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { value -> value.text }
            }
            assertEquals(setOf("Similarity cutoff", "24 / 100", "10", "45"), texts.toSet())
            assertTrue(CategoryCoverageReference.curve.isNotEmpty())
            if (evidence != null) scene.render(time).use { image ->
                image.encodeToData()!!.use { File(evidence, "category-coverage-bundled.png").writeBytes(it.bytes) }
            }
        } finally { scene.close() }
    }
}
