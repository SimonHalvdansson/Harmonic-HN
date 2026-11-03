package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.*
import javax.swing.SwingUtilities
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class SettingsSearchAlignmentTest {
    @Test fun bothEmptyPanesCenterBelowTheSameSearchHeader() = SwingUtilities.invokeAndWait {
        for (height in listOf(500, 800)) {
            val navigation = SettingsNavigationStore(twoPane = true)
            val scene = ImageComposeScene(1000, height) {
                val palette = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(palette.colorScheme, false) {
                    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground)) {
                        Box(Modifier.weight(1f)) { SettingsSearchScreen(navigation, emptyList(), {}) }
                        VerticalDivider(Modifier.width(1.dp))
                        Box(Modifier.weight(1f)) { SettingsSearchDetailPlaceholder() }
                    }
                }
            }
            try {
                repeat(35) { scene.render(it * 16_666_667L).close() }
                fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
                val nodes = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
                fun bounds(tag: String) = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }.boundsInRoot
                assertEquals(bounds("settings-search-list-empty").center.y, bounds("settings-search-detail-empty").center.y, 1f)
                if (height == 800) System.getenv("HARMONIC_STORY_DETAILS_EVIDENCE")?.let { folder ->
                    File(folder).mkdirs()
                    scene.render(700_000_000L).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(folder, "settings-search-alignment.png").writeBytes(data.bytes)
                    } }
                }
            } finally { scene.close() }
        }
    }
}
