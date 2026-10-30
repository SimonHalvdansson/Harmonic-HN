package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TruncatedSummaryRenderingTest {
    @Test
    fun partialSummaryRemainsReadableAndItsNoticeClearsAfterACompleteRetry() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) for (fontScale in listOf(1f, 1.5f)) {
            val truncated = mutableStateOf(true)
            val streaming = mutableStateOf(false)
            val preferences = AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments
            val settings = CommentDisplaySettings.from(preferences, false, false, false, true)
                .copy(font = "devicedefault")
            val scene = ImageComposeScene(360, 500, Density(1f, fontScale)) {
                val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "material_light", dark)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    StoryAiSummary(
                        StoryListItemSnapshot(StorySnapshot(42, title = "Test article"), StoryPresentationSnapshot(
                            aiSummaryText = "- The new battery uses common materials.\n- Early tests suggest",
                            aiSummaryTruncated = truncated.value,
                            summaryGeneratedSuccessfully = !truncated.value,
                        )), settings, {}, streaming = streaming.value, onAsk = {},
                    )
                }
            }
            var time = 0L
            fun frames() = repeat(30) { scene.render(time.also { time += 16_000_000L }).close() }
            fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            fun text() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }.flatMap {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty()
            }.joinToString(" ") { it.text }
            try {
                frames()
                assertTrue("Response cut short" in text())
                assertTrue("Early tests suggest" in text())
                System.getenv("HARMONIC_TRUNCATION_EVIDENCE")?.let { directory ->
                    val target = File(directory).apply { mkdirs() }
                    scene.render(time.also { time += 16_000_000L }).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use {
                            File(target, "truncated-summary-${if (dark) "dark" else "light"}-$fontScale.png").writeBytes(it.bytes)
                        }
                    }
                }
                streaming.value = true
                frames()
                assertFalse("Response cut short" in text())
                truncated.value = false
                streaming.value = false
                frames()
                assertFalse("Response cut short" in text())
                assertTrue("Ask" in text())
            } finally {
                scene.close()
            }
        }
    }
}
