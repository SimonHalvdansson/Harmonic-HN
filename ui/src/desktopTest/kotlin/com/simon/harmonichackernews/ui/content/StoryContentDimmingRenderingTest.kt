package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.simon.harmonichackernews.settings.CategoryTagLocation
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoryContentDimmingRenderingTest {
    @Test fun contentFadesOnceWithExtraCategoryDimmingWhileTheCardSurfaceStaysOpaque() = SwingUtilities.invokeAndWait {
        val palette = HarmonicThemeCatalog.scheme("classic", dark = true)
        val paper = palette.colorScheme.pageBackground
        for (location in CategoryTagLocation.entries) for (mode in listOf(StoryPreviewMode.OFF, StoryPreviewMode.SMALL, StoryPreviewMode.LARGE)) {
            val clicked = mutableStateOf(false)
            var geometry: StoryPreviewSourceGeometry? = null
            val scene = ImageComposeScene(500, 800) {
                val image = remember { ImageBitmap(8, 8).also {
                    Canvas(it).drawRect(Rect(0f, 0f, 8f, 8f), Paint().apply { color = Color.Cyan })
                } }
                HarmonicTheme(palette.colorScheme, true) {
                    Box(Modifier.fillMaxSize().background(paper)) {
                        StoryRow(SettingsStoryPreviewModel.copy(title = "Sample title", previewText = "Sample summary",
                            previewImageFallback = null, previewImageBitmap = image,
                            categoryLabels = listOf("Science"), authorName = "sample", authorTag = "Researcher",
                            authorTagColor = UserTagColor.Purple),
                            StoryRowStyle(previewImageMode = mode, borderlessLargeImage = false,
                                compact = false, showPreviewText = true, showFavicon = true, showPoints = true,
                                compactPoints = false, includeTopLevelDomain = true, showCommentCount = true,
                                showIndex = true, commentsOnLeft = false, tintCard = false, displayStyle = DisplayStyle.STANDARD,
                                useHotnessIcon = false, preferredFont = "devicedefault", textSize = 16f,
                                dimmed = clicked.value, categoryTagLocation = location),
                            listItem = true, animateChanges = false, capturePreviewSourceGeometry = true,
                            onPreviewSourceGeometryChanged = { geometry = it })
                    }
                }
            }
            var time = 0L
            fun frame() = scene.render(time.also { time += 16_666_667L })
            fun capture(): PixelMap {
                repeat(12) { frame().close() }
                return frame().use { it.toComposeImageBitmap().toPixelMap() }
            }
            try {
                val before = capture()
                val bounds = assertNotNull(geometry)
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                fun textBounds(text: String) = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }.single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text.contains(text) } == true
                }.boundsInRoot
                val title = textBounds("Sample title")
                val category = textBounds("Science")
                val metadata = if (location == CategoryTagLocation.METADATA) textBounds("science.org") else bounds.meta
                clicked.value = true
                val after = capture()
                val container = bounds.container
                val sampleX = (container.left + 8).toInt()
                val sampleY = (container.top + 8).toInt()
                val background = before[sampleX, sampleY]
                assertEquals(background, after[sampleX, sampleY], "Card background must retain its color")
                val sections = listOf("index" to bounds.index, "title" to title, "category tag" to category,
                    "summary" to bounds.summary, "metadata and site icon" to metadata,
                    "author tag" to bounds.author, "comment button and count" to bounds.comments,
                    "image" to bounds.image)
                val alpha = storyContentAlpha(true, true, false)
                for ((name, region) in sections) {
                    if (name == "image" && mode == StoryPreviewMode.OFF) continue
                    val sectionAlpha = if (name == "category tag") storyCategoryAlpha(alpha) else alpha
                    val rect = assertNotNull(region, "$location/$mode: $name must be captured")
                    var ink = 0
                    var matching = 0
                    for (y in rect.top.toInt() until rect.bottom.toInt())
                        for (x in rect.left.toInt() until rect.right.toInt()) {
                            val old = before[x, y]
                            val next = after[x, y]
                            if (abs(old.red - background.red) + abs(old.green - background.green) + abs(old.blue - background.blue) < 0.08f) continue
                            ink++
                            if (listOf(old.red to next.red, old.green to next.green, old.blue to next.blue)
                                .zip(listOf(background.red, background.green, background.blue)).all { (pair, bg) ->
                                    abs(pair.second - (pair.first * sectionAlpha + bg * (1f - sectionAlpha))) < 0.02f
                                }) matching++
                        }
                    assertTrue(ink > 5, "$location/$mode: $name must contain visible content")
                    assertTrue(matching >= ink * 0.95f,
                        "$location/$mode: $name must fade exactly once ($matching/$ink matching pixels)")
                }
            } finally { scene.close() }
        }
    }
}
