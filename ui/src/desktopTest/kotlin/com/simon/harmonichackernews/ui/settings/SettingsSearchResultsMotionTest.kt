package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.settings.SettingsSearchEntry
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchResultsMotionTest {
    @Test
    fun filteringKeepsRowsAndAnimatesTheirPosition() = SwingUtilities.invokeAndWait {
        val first = SettingsSearchEntry("first", "appearance", "Font", listOf("Appearance"))
        val second = SettingsSearchEntry("second", "appearance", "Font size", listOf("Appearance"))
        val entries = mutableStateOf(listOf(first, second))
        val queryEmpty = mutableStateOf(false)
        val listState = LazyListState()
        var selected: SettingsSearchEntry? = null
        val palette = HarmonicThemeCatalog.scheme("violet", false)
        val scene = ImageComposeScene(400, 700) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsSearchResults(queryEmpty.value, entries.value, listState) { selected = it }
            }
        }
        var time = 0L
        fun frames(count: Int = 30) = repeat(count) {
            scene.render(time.also { time += 16_666_667L }).close()
        }
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        fun nodes() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        fun row() = nodes().single {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == second.title } == true &&
                it.config.getOrNull(SemanticsActions.OnClick) != null
        }
        try {
            frames()
            val original = row()
            val startY = original.boundsInRoot.top
            entries.value = listOf(second)
            frames(6)
            val middleY = row().boundsInRoot.top
            assertEquals(original.id, row().id, "Filtering must retain the matching row")
            frames()
            val endY = row().boundsInRoot.top
            assertTrue(middleY > endY && middleY < startY, "The row must move smoothly: $startY → $middleY → $endY")

            // Interrupt empty-state fades while typing, then clear to recent results.
            repeat(3) {
                entries.value = emptyList()
                frames(4)
                queryEmpty.value = true
                frames(4)
                entries.value = listOf(second)
                frames(4)
                queryEmpty.value = false
            }
            frames()
            assertTrue(nodes().none {
                it.config.getOrNull(SemanticsProperties.TestTag) == "settings-search-list-empty"
            }, "Interrupted fades must not leave an empty-state layer behind")
            assertTrue(row().config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(second, selected)
        } finally {
            scene.close()
        }
    }
}
