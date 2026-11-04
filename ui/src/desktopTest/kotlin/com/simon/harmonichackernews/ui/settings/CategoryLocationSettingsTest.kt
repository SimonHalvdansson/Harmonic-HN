package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.*
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.settings.CategoryTagLocation
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.*
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.theme.*
import javax.swing.SwingUtilities
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class CategoryLocationSettingsTest {
    @Test fun locationIsEnabledOnlyWhileCategorizationIsOnAndRetainsItsChoice() = verifyPlacement(StoryPreviewMode.MEDIUM)

    @Test fun placementMovesChipsWithSmallLargeAndNoPreviewImages() {
        for (mode in listOf(StoryPreviewMode.SMALL, StoryPreviewMode.LARGE, StoryPreviewMode.OFF)) verifyPlacement(mode)
    }

    private fun verifyPlacement(mode: StoryPreviewMode) = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("CategoryLocationSettingsTest")
        StoriesSettingsPresenter(app.app.settings).setPreviewImageMode(mode)
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
            fun chipPosition() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Programming" } == true
            }.boundsInRoot.topLeft
            val titleLocation = chipPosition()
            fun titleLineCount(): Int {
                val title = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == SettingsStoryPreviewModel.title } == true
                }
                val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                title.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
                return layouts.single().lineCount
            }
            val initialLines = titleLineCount()
            options("Metadata").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            val heights = mutableSetOf<Int>()
            val positions = mutableSetOf<androidx.compose.ui.geometry.Offset>()
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
                positions += chipPosition()
                assertTrue(titleLineCount() <= initialLines + 1, "Moving a thumbnail must not squeeze the title between duplicate images")
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
            assertTrue(positions.size >= 8, "The chips themselves must travel, not only the card height: $positions")
            frames()
            val metadataLocation = chipPosition()
            assertTrue((metadataLocation - titleLocation).getDistance() > 20f)
            assertEquals(CategoryTagLocation.METADATA, app.app.settings.snapshot().story.categoryTagLocation)
            System.getenv("HARMONIC_STORY_DETAILS_EVIDENCE")?.let { folder ->
                File(folder).mkdirs()
                scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    File(folder, "settings-category-location.png").writeBytes(data.bytes)
                } }
            }
            options("Under title").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            positions.clear()
            repeat(35) { scene.render(time.also { time += 16_666_667L }).close(); positions += chipPosition() }
            assertTrue(positions.size >= 8, "Returning chips must animate too")
            assertEquals(titleLocation, chipPosition())
            // Reverse while a placement animation is still running; the retained chip must survive.
            options("Metadata").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            repeat(6) { scene.render(time.also { time += 16_666_667L }).close() }
            val interruptedPosition = chipPosition()
            options("Under title").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            scene.render(time.also { time += 16_666_667L }).close()
            assertTrue((chipPosition() - interruptedPosition).getDistance() < 20f, "A rapid reversal must not teleport the chip")
            frames()
            assertEquals(titleLocation, chipPosition())
            options("Metadata").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            frames()
            app.app.articleCategorySettings.setEnabled(false)
            frames()
            assertNotNull(options("Metadata").config.getOrNull(SemanticsProperties.Disabled))
            assertEquals(CategoryTagLocation.METADATA, app.app.settings.snapshot().story.categoryTagLocation)
            DebugStoryCategoryPreview.enabled = true
            frames()
            assertNull(options("Under title").config.getOrNull(SemanticsProperties.Disabled))
            assertFalse(app.app.articleCategorySettings.snapshot().enabled, "Mock preview must not enable live inference")
        } finally { DebugStoryCategoryPreview.enabled = false; scene.close(); app.close() }
    }
}
