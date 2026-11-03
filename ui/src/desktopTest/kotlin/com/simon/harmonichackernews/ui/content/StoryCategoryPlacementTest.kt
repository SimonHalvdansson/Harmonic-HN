package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.*
import com.simon.harmonichackernews.ui.theme.*
import javax.swing.SwingUtilities
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class StoryCategoryPlacementTest {
    @Test fun metadataFollowsTheWholeImageRowAndTaggedAuthorsWrapWithoutClipping() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("CategoryPlacementTest")
        try {
            for (width in listOf(280, 420)) for (scale in listOf(1f, 2f))
                for (mode in listOf(StoryPreviewMode.OFF, StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM, StoryPreviewMode.LARGE))
                    for (location in CategoryTagLocation.entries) {
                        val style = StoryDisplaySettings.from(app.app.userSettings.story).copy(previewImageMode = mode,
                            categoryTagLocation = location, showPreviewText = false).toStoryRowStyle(StoryRowStyleContext(53, 18, false))
                        val model = SettingsStoryPreviewModel.copy(title = "A useful new tool", categoryLabels = listOf("AI", "Science"), categoryIds = listOf("ai", "science"),
                            authorName = "TaggedAuthor", authorTag = "Maintainer and long-time contributor", authorTagColor = UserTagColor.Purple)
                        val scene = ImageComposeScene(width, 1000, Density(1f, scale)) {
                            val palette = HarmonicThemeCatalog.scheme("dynamic", false)
                            HarmonicTheme(palette.colorScheme, false) {
                                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app.app, app.scene)) {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                                        StoryRow(model, style, listItem = true, animateChanges = false)
                                    }
                                }
                            }
                        }
                        var time = 0L
                        try {
                            repeat(8) { scene.render(time.also { time += 16_666_667 }).close() }
                            val nodes = nodes(scene)
                            val author = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "story-author-tag" }
                            val layouts = mutableListOf<TextLayoutResult>()
                            author.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts)
                            assertFalse(layouts.single().didOverflowWidth || layouts.single().didOverflowHeight)
                            assertTrue(author.boundsInRoot.bottom <= 1000)
                            if (location == CategoryTagLocation.METADATA) {
                                val details = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "story-details" }
                                val image = nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) ==
                                    when(mode) { StoryPreviewMode.SMALL -> "story-small-preview"; StoryPreviewMode.MEDIUM -> "story-medium-preview"; else -> "none" } }
                                if (mode == StoryPreviewMode.SMALL || mode == StoryPreviewMode.MEDIUM) {
                                    assertNotNull(image)
                                    assertTrue(image.boundsInRoot.bottom <= details.boundsInRoot.top + 1f,
                                        "${mode} image must end above the complete metadata area")
                                }
                                assertTrue(author.boundsInRoot.top >= details.boundsInRoot.top)
                            } else assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.TestTag) == "story-details" })
                            assertTrue(nodes.none { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("category-label-") == true })
                            if (width == 420 && scale == 1f && mode == StoryPreviewMode.MEDIUM) {
                                System.getenv("HARMONIC_STORY_DETAILS_EVIDENCE")?.let { folder ->
                                    File(folder).mkdirs()
                                    scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                                        File(folder, "story-${location.storedValue}.png").writeBytes(data.bytes)
                                    } }
                                }
                            }
                        } finally { scene.close() }
                    }
        } finally { app.close() }
    }
    @Test fun metadataUsesTheTextColumnWhenItFitsAndMovesBelowWhenItDoesNot() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("AdaptiveCategoryPlacement")
        try {
            for (width in listOf(360, 760)) for (mode in listOf(StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM)) {
                val style = StoryDisplaySettings.from(app.app.userSettings.story).copy(previewImageMode = mode,
                    categoryTagLocation = CategoryTagLocation.METADATA, showPreviewText = false)
                    .toStoryRowStyle(StoryRowStyleContext(53, 18, false))
                val model = SettingsStoryPreviewModel.copy(title = "Astra can read and write weights to improve Qwen",
                    domain = "rikiyat.github.io", domainWithoutTopLevel = "rikiyat.github", categoryLabels = listOf("AI"), categoryIds = listOf("ai"))
                val scene = ImageComposeScene(width, 400) {
                    val palette = HarmonicThemeCatalog.scheme("dynamic", false)
                    HarmonicTheme(palette.colorScheme, false) {
                        StoryRow(model, style, listItem = true, animateChanges = false)
                    }
                }
                try {
                    repeat(20) { scene.render(it * 16_666_667L).close() }
                    val nodes = nodes(scene)
                    val details = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "story-details" }.boundsInRoot
                    val image = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) ==
                        if (mode == StoryPreviewMode.SMALL) "story-small-preview" else "story-medium-preview" }.boundsInRoot
                    if (width == 760) {
                        assertTrue(details.right <= image.left + 1f, "Fitting metadata must remain beside the image: $details / $image")
                        assertTrue(details.top < image.bottom, "Fitting metadata must not be pushed below the image")
                    } else assertTrue(details.top >= image.bottom - 1f, "Crowded metadata needs the full width")
                    System.getenv("HARMONIC_STORY_DETAILS_EVIDENCE")?.let { folder ->
                        File(folder).mkdirs()
                        scene.render(500_000_000L).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                            File(folder, "adaptive-${mode.name.lowercase()}-$width.png").writeBytes(data.bytes)
                        } }
                    }
                } finally { scene.close() }
            }
        } finally { app.close() }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> {
        fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
    }
}
