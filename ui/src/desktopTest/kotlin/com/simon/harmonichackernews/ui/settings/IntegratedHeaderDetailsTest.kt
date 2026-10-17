package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentsHeaderLayout
import com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.io.File
import javax.swing.SwingUtilities
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntegratedHeaderDetailsTest {
    @Test fun integratedMetricsStackWithoutAnImageAndAnimateIntoImageBadges() = SwingUtilities.invokeAndWait {
        val defaults = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        ).copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE, showUpButton = true)
        val evidence = System.getenv("HARMONIC_HEADER_EVIDENCE_DIR")?.let { File(it).apply { mkdirs() } }
        for (layout in CommentsHeaderLayout.entries) for (dark in listOf(false, true)) {
            val settings = mutableStateOf(defaults.copy(headerLayout = layout, showHeaderPreviewImage = false))
            val sample = mutableStateOf(HeaderPreviewSample.LINK_WITH_IMAGE)
            val scene = ImageComposeScene(360, 900, Density(1f, if (dark) 1.5f else 1f)) {
                val theme = HarmonicThemeCatalog.resolve(if (dark) "material_dark" else "material_light", dark)
                HarmonicTheme(theme.colorScheme, theme.dark) {
                    HeaderDisplayPreview(settings.value, true, {}, sample.value)
                }
            }
            var time = 0L
            fun frames(count: Int, capture: String? = null) {
                repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
                if (capture != null && evidence != null) {
                    scene.render(time.also { time += 16_666_667L }).use { image ->
                        image.encodeToData(EncodedImageFormat.PNG)!!.use {
                            File(evidence, "$capture-$layout-$dark.png").writeBytes(it.bytes)
                        }
                    }
                }
            }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            fun nodes() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
            fun tagged(tag: String) = nodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
            fun metric(description: String) = nodes().single {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(description) == true
            }.boundsInRoot
            fun verifyMetrics(stacked: Boolean) {
                val points = metric("${SettingsStoryPreviewModel.points} points")
                val comments = metric("${SettingsStoryPreviewModel.commentCount} comments")
                if (stacked) {
                    assertEquals(points.left, comments.left)
                    assertTrue(points.bottom <= comments.top, "Points and comments must be vertically stacked")
                } else {
                    assertEquals(points.top, comments.top)
                    assertTrue(points.right <= comments.left, "Image metrics must share a row")
                }
                assertTrue(nodes().any {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> "willow" in text.text } == true
                }, "Integrated details must retain the post author")
            }
            try {
                frames(2, "stacked")
                verifyMetrics(stacked = true)
                val railWidth = tagged("header-stacked-metrics").single().boundsInRoot.width
                settings.value = settings.value.copy(showHeaderPreviewImage = true)
                frames(2, "transition")
                val fadingWidth = tagged("header-stacked-metrics").single().boundsInRoot.width
                assertEquals(railWidth, fadingWidth,
                    "The outgoing rail must fade at full width while the image layout appears")
                frames(6)
                assertTrue(tagged("header-stacked-metrics").isEmpty(),
                    "The old rail must finish fading before the image occupies its space")
                assertTrue(tagged("header-image-metrics").isNotEmpty())
                frames(40, "image")
                assertTrue(tagged("header-stacked-metrics").isEmpty())
                verifyMetrics(stacked = false)
                settings.value = settings.value.copy(showHeaderPreviewImage = false)
                frames(40)
                assertTrue(tagged("header-image-metrics").isEmpty())
                verifyMetrics(stacked = true)
                sample.value = HeaderPreviewSample.TEXT_POST
                frames(40, "ask")
                verifyMetrics(stacked = true)
                assertTrue(nodes().none {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Site icon") == true
                }, "Text posts must not gain a site icon")
            } finally { scene.close() }
        }
    }
}
