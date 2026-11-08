package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        var availability by mutableStateOf(StoryCategoryAvailability(false, "Download to enable"))
        var categoryNavigationCount = 0
        val scene = ImageComposeScene(420, 1000) {
            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
            HarmonicTheme(palette.colorScheme, false) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app.app, app.scene)) {
                    StoriesSettingsRoute(repository = app.app.settings, categorySettings = app.app.articleCategorySettings,
                        previewModel = SettingsStoryPreviewModel, faviconIcon = ColorPainter(Color.Gray),
                        showNavigation = true, onBack = {}, onSpacingRequested = {}, onPlatformEffect = {}, onManageFrontpages = {},
                        onPaletteTintRequested = {}, faviconDialog = { _, _, _, _ -> },
                        categoryAvailability = availability,
                        onArticleCategoriesRequested = { categoryNavigationCount++ })
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
        fun categorySwitch() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single { n ->
            n.config.getOrNull(SemanticsProperties.ToggleableState) != null && walk(n).any {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Article category tags") == true
            }
        }
        try {
            frames()
            assertNotNull(options("Under title").config.getOrNull(SemanticsProperties.Disabled))
            assertNotNull(categorySwitch().config.getOrNull(SemanticsProperties.Disabled))
            val navigationRow = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single { n ->
                n.config.getOrNull(SemanticsActions.OnClick) != null && walk(n).any {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Article category tags" } == true
                }
            }
            assertNull(navigationRow.config.getOrNull(SemanticsProperties.Disabled))
            navigationRow.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, categoryNavigationCount, "Model setup must remain reachable while the switch is disabled")
            availability = StoryCategoryAvailability()
            frames()
            categorySwitch().config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            assertTrue(app.app.articleCategorySettings.snapshot().enabled)
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
            fun previewNode() = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "stories-settings-preview"
            }
            val initialHeight = previewNode().boundsInRoot.height.toInt()
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
                val preview = previewNode()
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
            assertTrue(positions.size >= 8, "$mode: The chips themselves must travel, not only the card height: $positions")
            frames()
            val finalHeight = previewNode().boundsInRoot.height.toInt()
            // Font metrics and wrapping can leave both placements at the same height or only
            // one pixel apart. Require an intermediate height only when one is representable.
            val smallerHeight = minOf(initialHeight, finalHeight)
            val largerHeight = maxOf(initialHeight, finalHeight)
            if (largerHeight - smallerHeight > 1) {
                assertTrue(heights.any { it > smallerHeight && it < largerHeight },
                    "$mode: Category placement must animate between $initialHeight and $finalHeight: $heights")
            }
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
            // Recompose at the same animation time: normal travel during the next frame can be
            // more than 20px on the long metadata path, so it isn't a discontinuity check.
            scene.render(time - 16_666_667L).close()
            assertTrue((chipPosition() - interruptedPosition).getDistance() < 2f,
                "$mode: A rapid reversal must not teleport the chip: $interruptedPosition -> ${chipPosition()}")
            frames()
            assertEquals(titleLocation, chipPosition())
            options("Metadata").config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke()
            frames()
            categorySwitch().config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            assertNotNull(options("Metadata").config.getOrNull(SemanticsProperties.Disabled))
            assertEquals(CategoryTagLocation.METADATA, app.app.settings.snapshot().story.categoryTagLocation)
            DebugStoryCategoryPreview.enabled = true
            frames()
            assertNull(options("Under title").config.getOrNull(SemanticsProperties.Disabled))
            assertFalse(app.app.articleCategorySettings.snapshot().enabled, "Mock preview must not enable live inference")
            categorySwitch().config[SemanticsActions.OnClick].action!!.invoke()
            frames()
            assertNotNull(options("Under title").config.getOrNull(SemanticsProperties.Disabled))
        } finally { DebugStoryCategoryPreview.enabled = false; DebugStoryCategoryPreview.showTags = true; scene.close(); app.close() }
    }
}
