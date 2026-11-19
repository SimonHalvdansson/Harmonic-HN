package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.settings.DataSettingsSnapshot
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadingHistoryExportRowTest {
    @Test
    fun exportIsEnabledOnlyWithHistoryAndDispatchesTheHistoryAction() = SwingUtilities.invokeAndWait {
        for (count in listOf(0, 2)) {
            var action: DataSettingsAction? = null
            val scene = ImageComposeScene(500, 1800, Density(1f)) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    DataSettingsScreen(
                        DataSettingsSnapshot(true, 0, false, count, 0, 0, true),
                        showNavigation = true, onBack = {}, onBookmarksEnabledChanged = {},
                        onShowChangelogChanged = {}, onAction = { action = it },
                    )
                }
            }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            try {
                repeat(10) { scene.render((it + 1) * 16_000_000L).close() }
                val row = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }.first {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Export reading history" } == true
                }
                val click = assertNotNull(row.config.getOrNull(SemanticsActions.OnClick)?.action)
                if (count == 0) {
                    assertNotNull(row.config.getOrNull(SemanticsProperties.Disabled))
                    // Disabled rows remain discoverable without allowing activation.
                    assertNull(action)
                } else {
                    assertNull(row.config.getOrNull(SemanticsProperties.Disabled))
                    click()
                    assertEquals(DataSettingsAction.ExportReadingHistory, action)
                }
            } finally { scene.close() }
        }
    }
}
