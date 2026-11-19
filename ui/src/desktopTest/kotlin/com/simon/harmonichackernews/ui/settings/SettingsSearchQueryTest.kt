package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import com.simon.harmonichackernews.settings.SettingsSearchEntry
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsSearchQueryTest {
    @Test fun deletingAnUnmatchedFirstQueryRestoresSearch() = verify(clearButton = false)
    @Test fun clearingAnUnmatchedFirstQueryKeepsLaterEditsWorking() = verify(clearButton = true)

    private fun verify(clearButton: Boolean) = SwingUtilities.invokeAndWait {
        val navigation = SettingsNavigationStore(initialSection = SettingsSection.Search)
        val entry = SettingsSearchEntry("font", "font", "Font", listOf("Appearance"))
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val scene = ImageComposeScene(400, 700) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsSearchScreen(navigation, listOf(entry), {})
            }
        }
        var time = 0L
        fun frames() = repeat(40) { scene.render(time.also { time += 16_666_667L }).close() }
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        fun nodes() = scene.semanticsOwners.flatMap { walk(it.rootSemanticsNode) }
        fun edit(query: String) {
            val field = nodes().single { it.config.getOrNull(SemanticsActions.SetText) != null }
            assertTrue(field.config[SemanticsActions.SetText].action!!.invoke(AnnotatedString(query)))
            frames()
        }
        fun hasText(text: String) = nodes().any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true
        }
        try {
            frames()
            // No recent results means the lazy list has never been laid out.
            edit("zzzzzz")
            assertEquals("zzzzzz", navigation.state.value.searchQuery)
            assertTrue(hasText("No settings found"))
            if (clearButton) {
                val clear = nodes().single {
                    it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Clear search") &&
                        it.config.getOrNull(SemanticsActions.OnClick) != null
                }
                assertTrue(clear.config[SemanticsActions.OnClick].action!!.invoke())
                frames()
            } else {
                edit("")
            }
            assertEquals("", navigation.state.value.searchQuery)
            assertTrue(!hasText("No settings found"))
            edit("font")
            assertEquals("font", navigation.state.value.searchQuery)
            assertTrue(hasText("Font"))
            edit("zzzzzz")
            edit("font")
            assertEquals("font", navigation.state.value.searchQuery)
            assertTrue(hasText("Font"))
        } finally {
            scene.close()
        }
    }
}
