package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.semantics.*
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.CategoryTagLocation
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.*
import java.io.File
import javax.swing.SwingUtilities
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.abs
import kotlin.test.*

class StorySettingsMotionTest {
    @Test fun textAndTagsSharePlacementTimingAndImagesStayContinuous() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("StorySettingsMotion")
        val model = SettingsStoryPreviewModel.copy(categoryLabels = listOf("AI", "Programming"), categoryIds = listOf("ai", "software"))
        var style by mutableStateOf(StoryDisplaySettings.from(app.app.userSettings.story)
            .copy(previewImageMode = StoryPreviewMode.SMALL, showPreviewText = true)
            .toStoryRowStyle(StoryRowStyleContext(53, 18, false)))
        val scene = ImageComposeScene(420, 900) {
            MaterialTheme {
                LookaheadScope {
                    val motion = remember(this) { StoryRowPlacementAnimation(this) }
                    StoryRow(model, style, animateChanges = false, placementAnimation = motion)
                }
            }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_666_667L }).close() }
        fun nodes(): List<SemanticsNode> {
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        fun bounds(key: String): Rect = nodes().single { node ->
            if (key == "title" || key == "summary") node.config.getOrNull(SemanticsProperties.Text)?.any {
                it.text == if (key == "title") model.title else model.previewText
            } == true else node.config.getOrNull(SemanticsProperties.TestTag) == "story-motion-$key"
        }.boundsInRoot
        val textKeys = listOf("title", "summary", "category-AI", "category-Programming", "metadata")
        fun transition(name: String, target: StoryRowStyle, keys: List<String> = textKeys) {
            val start = keys.associateWith(::bounds)
            val drawnLabelWidths = mutableSetOf<Int>()
            style = target
            val frames = (0 until 32).map { index ->
                frame()
                if (name == "text-size") {
                    drawnLabelWidths += nodes().single { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Programming" } == true
                    }.boundsInRoot.width.toInt()
                }
                System.getenv("HARMONIC_SETTINGS_MOTION_FRAMES")?.let { folder ->
                    val directory = File(folder, name).apply { mkdirs() }
                    scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(directory, "frame-${index.toString().padStart(3, '0')}.png").writeBytes(data.bytes)
                    } }
                }
                keys.associateWith(::bounds)
            }
            val end = frames.last()
            if (name == "text-size") {
                assertTrue(drawnLabelWidths.size >= 8,
                    "Tag text must grow through intermediate sizes along with the other text")
            }
            for (key in keys) {
                fun position(bounds: Rect) = if (key == "comments") bounds.center else bounds.topLeft
                val delta = position(end.getValue(key)) - position(start.getValue(key))
                if (delta.getDistance() < 5f) continue
                val positions = frames.map { position(it.getValue(key)) }
                assertTrue(positions.distinct().size >= 8, "$name: $key must move through intermediate positions")
                // A single easing curve must drive all elements, including the summary.
                val atMiddle = positions[9] - position(start.getValue(key))
                val progress = if (abs(delta.x) > abs(delta.y)) atMiddle.x / delta.x else atMiddle.y / delta.y
                val reference = textKeys.firstOrNull { other -> other in keys &&
                    (end.getValue(other).topLeft - start.getValue(other).topLeft).getDistance() > 5f }
                if (reference != null) {
                    val referenceDelta = end.getValue(reference).topLeft - start.getValue(reference).topLeft
                    val referenceAtMiddle = frames[9].getValue(reference).topLeft - start.getValue(reference).topLeft
                    val expected = if (abs(referenceDelta.x) > abs(referenceDelta.y)) referenceAtMiddle.x / referenceDelta.x else referenceAtMiddle.y / referenceDelta.y
                    assertEquals(expected, progress, .13f, "$name: $key must stay synchronized with $reference")
                    if (key == "preview-image") {
                        val widthChange = end.getValue(key).width - start.getValue(key).width
                        if (abs(widthChange) > 5f) {
                            val sizeProgress = (frames[9].getValue(key).width - start.getValue(key).width) / widthChange
                            assertEquals(progress, sizeProgress, .035f,
                                "$name: image resizing must not lag behind its movement")
                        }
                    }
                }
            }
        }
        try {
            repeat(30) { frame() }
            transition("comments-left", style.copy(commentsOnLeft = true))
            transition("indices-off", style.copy(showIndex = false))
            transition("category-metadata", style.copy(categoryTagLocation = CategoryTagLocation.METADATA))
            transition("category-title", style.copy(categoryTagLocation = CategoryTagLocation.TITLE))
            val modes = listOf(StoryPreviewMode.MEDIUM, StoryPreviewMode.SMALL, StoryPreviewMode.LARGE,
                StoryPreviewMode.SMALL, StoryPreviewMode.MEDIUM, StoryPreviewMode.LARGE, StoryPreviewMode.MEDIUM)
            for (location in CategoryTagLocation.entries) {
                style = style.copy(categoryTagLocation = location)
                repeat(30) { frame() }
                for (mode in modes) transition("${location.name}-${style.previewImageMode}-$mode", style.copy(previewImageMode = mode),
                    textKeys + listOf("preview-image", "comments"))
            }
            val initialChip = bounds("category-Programming")
            transition("text-size", style.copy(textSize = style.textSize + 5f), textKeys + "points")
            assertTrue(bounds("category-Programming").width > initialChip.width + 10f, "Category labels must scale with story text")
        } finally { scene.close(); app.close() }
    }
}
