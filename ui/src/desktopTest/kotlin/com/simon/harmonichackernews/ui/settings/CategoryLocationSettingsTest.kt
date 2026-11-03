package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.*
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.settings.CategoryTagLocation
import com.simon.harmonichackernews.ui.*
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.theme.*
import javax.swing.SwingUtilities
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class CategoryLocationSettingsTest {
    @Test fun locationIsEnabledOnlyWhileCategorizationIsOnAndRetainsItsChoice() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("CategoryLocationSettingsTest")
        val scene = ImageComposeScene(420, 1000) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
            HarmonicTheme(palette.colorScheme, false) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app.app, app.scene)) {
                    StoriesSettingsRoute(repository = app.app.settings, categorySettings = app.app.articleCategorySettings,
                        previewModel = SettingsStoryPreviewModel, faviconIcon = ColorPainter(Color.Gray),
                        showNavigation = true, onBack = {}, onPlatformEffect = {}, onManageFrontpages = {},
                        onPaletteTintRequested = {}, faviconDialog = { _, _, _, _ -> })
                }
            }
        }
        var time = 0L
        fun frames() = repeat(40) { scene.render(time.also { time += 16_666_667L }).close() }
        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        fun options(label: String) = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.filter { n ->
            n.config.getOrNull(SemanticsProperties.Selected) != null && walk(n).any { child ->
                child.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
            }
        }.single()
        try {
            frames()
            assertNotNull(options("Under title").config.getOrNull(SemanticsProperties.Disabled))
            app.app.articleCategorySettings.setEnabled(true)
            frames()
            assertNull(options("Metadata").config.getOrNull(SemanticsProperties.Disabled))
            options("Metadata").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            val heights = mutableSetOf<Int>()
            repeat(35) { index ->
                scene.render(time.also { time += 16_666_667L }).use { image ->
                    System.getenv("HARMONIC_CATEGORY_LOCATION_FRAMES")?.let { folder ->
                        File(folder).mkdirs()
                        image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(folder, "frame-${index.toString().padStart(3, '0')}.png").writeBytes(data.bytes)
                        }
                    }
                }
                val preview = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single {
                    it.config.getOrNull(SemanticsProperties.TestTag) == "stories-settings-preview"
                }
                heights += preview.boundsInRoot.height.toInt()
                assertEquals(1, walk(preview).count { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any {
                        it.text == SettingsStoryPreviewModel.title
                    } == true
                }, "Placement changes must retain one card instead of crossfading duplicate cards")
                assertEquals(1, walk(preview).count { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.contains("science.org") } == true
                }, "Moving metadata must not leave a second fading copy under the title")
            }
            assertTrue(heights.size >= 3, "Category placement must animate through intermediate preview heights: $heights")
            frames()
            assertEquals(CategoryTagLocation.METADATA, app.app.settings.snapshot().story.categoryTagLocation)
            System.getenv("HARMONIC_STORY_DETAILS_EVIDENCE")?.let { folder ->
                File(folder).mkdirs()
                scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File(folder, "settings-category-location.png").writeBytes(data.bytes)
                } }
            }
            app.app.articleCategorySettings.setEnabled(false)
            frames()
            assertNotNull(options("Metadata").config.getOrNull(SemanticsProperties.Disabled))
            assertEquals(CategoryTagLocation.METADATA, app.app.settings.snapshot().story.categoryTagLocation)
        } finally { scene.close(); app.close() }
    }
}
