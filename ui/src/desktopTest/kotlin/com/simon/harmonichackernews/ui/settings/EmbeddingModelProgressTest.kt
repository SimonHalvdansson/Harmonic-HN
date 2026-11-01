package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class EmbeddingModelProgressTest {
    @Test
    fun settingsCardHeightAnimatesBothWhenStartingAndCancellingDownload() = SwingUtilities.invokeAndWait {
        val downloading = mutableStateOf(false)
        val scene = ImageComposeScene(400, 300, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsCategory("Model") {
                    Column(Modifier.fillMaxWidth().testTag("model-card")) {
                        Text("EmbeddingGemma 2")
                        EmbeddingModelProgress(downloading.value, 10, 100)
                        Text("Loads in the background at launch, consuming ~191 MB RAM.")
                    }
                }
            }
        }
        var time = 0L
        fun frames(count: Int) { repeat(count) { time += 16_000_000L; scene.render(time).close() } }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun height() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }.first {
            it.config.getOrNull(SemanticsProperties.TestTag) == "model-card"
        }.boundsInRoot.height
        try {
            frames(10)
            val collapsed = height()
            downloading.value = true
            frames(6)
            val growing = height()
            frames(30)
            val expanded = height()
            assertTrue(growing > collapsed && growing < expanded, "$collapsed < $growing < $expanded")
            downloading.value = false
            frames(6)
            val shrinking = height()
            assertTrue(shrinking > collapsed && shrinking < expanded)
            frames(30)
            assertTrue(height() == collapsed)
        } finally { scene.close() }
    }
}
