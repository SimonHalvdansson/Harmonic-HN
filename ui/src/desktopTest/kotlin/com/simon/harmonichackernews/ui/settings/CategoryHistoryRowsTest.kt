package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.categorization.ArticleTrainingStory
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class CategoryHistoryRowsTest {
    @Test
    fun emptyHistoryIsCenteredWithinTheAvailableContent() = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CategoryHistoryEmptyState("No stories yet", "Open stories to choose examples here.")
            }
        }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        try {
            repeat(10) { scene.render((it + 1) * 16_000_000L).close() }
            val text = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.filter {
                it.config.getOrNull(SemanticsProperties.Text) != null
            }
            assertEquals(2, text.size)
            text.forEach { assertTrue(abs(it.boundsInRoot.center.x - 200f) < 1f) }
            val title = text.first { it.config[SemanticsProperties.Text].first().text == "No stories yet" }
            assertTrue(title.boundsInRoot.center.y in 90f..150f, "Empty state should be centered vertically")
        } finally { scene.close() }
    }

    @Test
    fun paginationIsTheLastScrollableItemRatherThanAFixedControl() = SwingUtilities.invokeAndWait {
        val scene = ImageComposeScene(400, 400, Density(1f)) {
            CategoryHistoryRows((1..30).map { ArticleTrainingStory(it, "Example $it", "https://example.com/$it") },
                emptySet(), emptySet(), true, footer = { Text("Load older stories", Modifier.height(48.dp)) },
                onSelection = { _, _ -> })
        }
        var time = 0L
        fun frame() { time += 16_000_000L; scene.render(time).close() }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun footer() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.firstOrNull {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Load older stories" } == true
        }
        try {
            repeat(10) { frame() }
            assertEquals(null, footer(), "Pagination must start below the loaded stories")
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f, 200f), scrollDelta = Offset(0f, 5000f))
            repeat(60) { frame() }
            val bounds = checkNotNull(footer()).boundsInRoot
            assertTrue(bounds.top >= 0f && bounds.bottom <= 400f)
        } finally { scene.close() }
    }

    @Test
    fun selectingFromTheCardEdgeWorksAndAlreadyAddedExamplesStayDisabled() = SwingUtilities.invokeAndWait {
        val selected = mutableStateOf<Set<Int>>(emptySet())
        val scene = ImageComposeScene(400, 400, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CategoryHistoryRows(listOf(
                    ArticleTrainingStory(1, "A useful example", "https://example.com/first"),
                    ArticleTrainingStory(2, "Already selected", "https://example.com/second"),
                ), selected.value, setOf(2), true) { id, checked ->
                    selected.value = if (checked) selected.value + id else selected.value - id
                }
            }
        }
        var time = 0L
        fun frame() { time += 16_000_000L; scene.render(time).close() }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun rows() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.filter {
            it.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox && it.config.getOrNull(SemanticsActions.OnClick) != null
        }
        fun click(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1)
            repeat(3) { frame() }
        }
        try {
            repeat(10) { frame() }
            val enabled = rows().first { it.config.getOrNull(SemanticsProperties.Disabled) == null }
            assertEquals(400f, enabled.boundsInRoot.width)
            click(Offset(enabled.boundsInRoot.left + 1f, enabled.boundsInRoot.center.y))
            assertEquals(setOf(1), selected.value, "The row must respond all the way to the card edge")
            val disabled = rows().first { it.config.getOrNull(SemanticsProperties.Disabled) != null }
            click(disabled.boundsInRoot.center)
            assertEquals(setOf(1), selected.value)
            assertTrue(rows().any { it.config.getOrNull(SemanticsProperties.ToggleableState) != null })
        } finally { scene.close() }
    }
}
