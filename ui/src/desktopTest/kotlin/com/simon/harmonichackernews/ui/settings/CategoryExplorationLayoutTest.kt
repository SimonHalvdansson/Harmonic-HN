package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryExplorationLayoutTest {
    @Test
    fun loadingTheMapDoesNotMoveTheLegendFromItsFirstFramePosition() = SwingUtilities.invokeAndWait {
        val data = mutableStateOf<CategoryExplorationData?>(null)
        val scene = ImageComposeScene(400, 750, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Column {
                    CategoryExplorationPanel(data.value, false, "umap", List(14) { Color.Blue }, null, null, {}, {}, {})
                    Text("Category legend", Modifier.testTag("legend"))
                }
            }
        }
        var time = 0L
        fun frame() { time += 16_000_000L; scene.render(time).close() }
        fun legendTop() = nodes(scene).first { it.config.getOrNull(SemanticsProperties.TestTag) == "legend" }.boundsInRoot.top
        try {
            frame()
            val initial = legendTop()
            repeat(8) { frame(); assertEquals(initial, legendTop()) }
            val points = ArticleCategories.defaults.mapIndexed { i, category -> CategoryMapPoint(i, "Example $i", category.id, emptyList()) }
            val positions = points.indices.map { listOf(0.1f + it * 0.05f, 0.2f + it * 0.04f) }
            data.value = CategoryExplorationData(2, points,
                listOf("umap", "tsne", "pca").map { CategoryProjection(it, positions) }, listOf(0f, 1f))
            repeat(35) { frame(); assertEquals(initial, legendTop(), "Loading must not trigger a height animation above the legend") }
        } finally { scene.close() }
    }

    @Test
    fun exampleChartIncludes75AndHidesEveryCategoryBelowIt() = SwingUtilities.invokeAndWait {
        val point = mutableStateOf(CategoryMapPoint(1, "Example", "ai", listOf(
            CategoryMapMatch("ai", 0.80f), CategoryMapMatch("space", 0.75f), CategoryMapMatch("cars", 0.749f))))
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) { CategoryExampleMatches(point.value, List(14) { Color.Blue }) }
        }
        var time = 0L
        fun frame() { time += 16_000_000L; scene.render(time).close() }
        fun descriptions() = nodes(scene).flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
        fun texts() = nodes(scene).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
        try {
            repeat(10) { frame() }
            assertTrue(descriptions().any { it.startsWith("AI, similarity 80") })
            assertTrue(descriptions().any { it.startsWith("Space, similarity 75") })
            assertTrue(descriptions().none { it.startsWith("Cars,") })
            assertTrue("75" in texts() && "100" in texts(), "The chart must state its truncated scale")
            point.value = point.value.copy(matches = point.value.matches.map { it.copy(similarity = 0.74f) })
            repeat(10) { frame() }
            assertTrue(descriptions().none { it.contains("similarity") })
            assertTrue("No matches at 75 or above" in texts())
        } finally { scene.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
    }
}
