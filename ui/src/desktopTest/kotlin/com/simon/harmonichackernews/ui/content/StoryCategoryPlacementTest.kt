package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LookaheadScope
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.*
import com.simon.harmonichackernews.ui.theme.*
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
import javax.swing.SwingUtilities
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

class StoryCategoryPlacementTest {
    @Test fun inlineMetadataCategoriesKeepTheirSizeAndShareTheTextBaselineWithoutExtraSpacing() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("InlineCategoryHeight")
        try {
            for (mode in listOf(StoryPreviewMode.OFF, StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM))
                for (settingsPreview in listOf(false, true)) {
                    var style by mutableStateOf(StoryDisplaySettings.from(app.app.userSettings.story).copy(
                        previewImageMode = mode, categoryTagLocation = CategoryTagLocation.TITLE,
                        showPreviewText = false,
                    ).toStoryRowStyle(StoryRowStyleContext(53, 18, false)))
                    val model = SettingsStoryPreviewModel.copy(title = "A useful tool",
                        categoryLabels = listOf("AI"), categoryIds = listOf("ai"))
                    var capture: StoryPreviewSourceGeometry? = null
                    val scene = ImageComposeScene(800, 400) {
                        MaterialTheme {
                            LookaheadScope {
                                val animation = remember { StoryRowPlacementAnimation(this) }
                                StoryRow(model, style, listItem = true, animateChanges = false,
                                    placementAnimation = animation.takeIf { settingsPreview },
                                    capturePreviewSourceGeometry = true, onPreviewSourceGeometryChanged = { capture = it })
                            }
                        }
                    }
                    try {
                        fun textNode(text: String) = nodes(scene).single {
                            it.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == text
                        }
                        fun textLayout(node: SemanticsNode): TextLayoutResult {
                            val results = mutableListOf<TextLayoutResult>()
                            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
                            return results.single()
                        }
                        repeat(36) { scene.render(it * 16_666_667L).close() }
                        val originalTag = textLayout(textNode("AI"))
                        style = style.copy(categoryTagLocation = CategoryTagLocation.METADATA)
                        repeat(36) { scene.render((it + 36) * 16_666_667L).close() }
                        val tagged = assertNotNull(capture)
                        val tag = textNode("AI")
                        val tagLayout = textLayout(tag)
                        val meta = nodes(scene).single {
                            it.config.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text?.contains("science.org") == true
                        }
                        assertEquals(originalTag.size, tagLayout.size, "Tag text must retain its regular size")
                        assertEquals(originalTag.layoutInput.style, tagLayout.layoutInput.style)
                        assertEquals(meta.boundsInRoot.top + textLayout(meta).firstBaseline,
                            tag.boundsInRoot.top + tagLayout.firstBaseline, 1f,
                            "$mode settings=$settingsPreview: category and metadata text must share a baseline")
                        assertTrue(assertNotNull(tagged.meta).top - assertNotNull(tagged.title).bottom <= 3f,
                            "Inline metadata must not retain the title block's bottom margin")
                    } finally { scene.close() }
                }
        } finally { app.close() }
    }

    @Test fun previewCaptureIncludesEveryCategoryInBothPlacementsAndCompactRows() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("CategoryPreviewCapture")
        try {
            for (location in CategoryTagLocation.entries) for (compact in listOf(false, true)) {
                val style = StoryDisplaySettings.from(app.app.userSettings.story).copy(
                    previewImageMode = StoryPreviewMode.OFF, showPreviewText = false,
                    categoryTagLocation = location, compactView = compact,
                ).toStoryRowStyle(StoryRowStyleContext(53, 18, false))
                val labels = listOf("Programming", "Science", "World news")
                val model = SettingsStoryPreviewModel.copy(categoryLabels = labels,
                    authorName = "tester", authorTag = "Maintainer", authorTagColor = UserTagColor.Purple)
                var capture: StoryPreviewSourceGeometry? = null
                val scene = ImageComposeScene(300, 700) {
                    MaterialTheme {
                        StoryRow(model, style, listItem = true, animateChanges = false,
                            capturePreviewSourceGeometry = true,
                            onPreviewSourceGeometryChanged = { capture = it })
                    }
                }
                try {
                    repeat(12) { scene.render(it * 16_666_667L).close() }
                    val geometry = assertNotNull(capture)
                    val authorBounds = nodes(scene).single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "story-author-tag"
                    }.boundsInRoot
                    val authorCapture = assertNotNull(geometry.author)
                    assertNotNull(geometry.authorLayer)
                    assertTrue(authorCapture.contains(authorBounds.center),
                        "The author label must have its own captured transition layer")
                    val group = assertNotNull(if (location == CategoryTagLocation.TITLE) geometry.title else geometry.meta)
                    assertNotNull(if (location == CategoryTagLocation.TITLE) geometry.titleLayer else geometry.metaLayer)
                    for (label in labels) {
                        val chipText = nodes(scene).single {
                            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
                        }.boundsInRoot
                        assertTrue(group.left <= chipText.left && group.top <= chipText.top &&
                            group.right >= chipText.right && group.bottom >= chipText.bottom,
                            "$location compact=$compact must capture $label in the moving group: $group / $chipText")
                    }
                } finally { scene.close() }
            }
        } finally { app.close() }
    }

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
                var linkClicks = 0
                var commentClicks = 0
                val scene = ImageComposeScene(width, 400) {
                    val palette = HarmonicThemeCatalog.scheme("dynamic", false)
                    HarmonicTheme(palette.colorScheme, false) {
                        StoryRow(model, style, listItem = true, animateChanges = false,
                            onLinkClick = { linkClicks++ }, onCommentClick = { commentClicks++ })
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
                    val link = nodes.single { it.config.getOrNull(SemanticsProperties.TestTag) == "story-link-area" }
                    val title = nodes.single { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == model.title } == true }
                    assertTrue(link.boundsInRoot.contains(details.center) && link.boundsInRoot.contains(title.boundsInRoot.center),
                        "Title and metadata must share one link interaction surface")
                    fun tap(point: Offset) {
                        scene.sendPointerEvent(PointerEventType.Press, point, type = PointerType.Touch)
                        scene.sendPointerEvent(PointerEventType.Release, point, type = PointerType.Touch)
                    }
                    tap(title.boundsInRoot.center)
                    tap(details.center)
                    assertEquals(2, linkClicks)
                    val comments = nodes.single { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)
                        ?.any { it.endsWith("${model.commentCount} comments") } == true }
                    tap(comments.boundsInRoot.center)
                    assertEquals(1, commentClicks, "Comments must retain their separate touch target")
                    assertEquals(2, linkClicks, "Comment taps must not open the story link")
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
