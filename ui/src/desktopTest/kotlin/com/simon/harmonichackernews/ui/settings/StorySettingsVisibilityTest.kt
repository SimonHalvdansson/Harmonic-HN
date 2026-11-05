package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.CategoryTagLocation
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.*
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.*

class StorySettingsVisibilityTest {
    @Test fun visibilityControlsFadeAndRelayoutInBothDirectionsAcrossEveryImageAndTagLayout() = SwingUtilities.invokeAndWait {
        val app = DesktopHarmonicAppBootstrap.inMemory("StorySettingsVisibility")
        val model = SettingsStoryPreviewModel.copy(categoryLabels = listOf("AI", "Programming"), categoryIds = listOf("ai", "software"))
        val baseStyle = StoryDisplaySettings.from(app.app.userSettings.story)
            .toStoryRowStyle(StoryRowStyleContext(53, 18, false)).copy(showPreviewText = true,
                compact = false, showPoints = true, showFavicon = true, showCommentCount = true,
                showIndex = true, includeTopLevelDomain = true, compactPoints = false)
        var target by mutableStateOf(StorySettingsContent(model, baseStyle))
        lateinit var visibility: StorySettingsVisibility
        val scene = ImageComposeScene(420, 1000) {
            MaterialTheme { LookaheadScope {
                visibility = rememberStorySettingsVisibility(target.model, target.style)
                val motion = remember(this) { StoryRowPlacementAnimation(this, visibility) }
                StoryRow(visibility.rendered.model, visibility.rendered.style, animateChanges = false,
                    placementAnimation = motion, modifier = Modifier.testTag("visibility-card"))
            } }
        }
        var time = 0L
        fun frame(capture: Boolean = false): Int {
            var hash = 0
            scene.render(time.also { time += 16_666_667L }).use { image ->
                if (capture) image.encodeToData()!!.use { hash = it.bytes.contentHashCode() }
            }
            return hash
        }
        fun settle() = repeat(60) { frame() }
        fun nodes(): List<SemanticsNode> {
            fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
            return scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }
        }
        fun cardHeight(): Int {
            return nodes().single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "visibility-card"
            }.boundsInRoot.height.toInt()
        }
        fun change(name: String, next: StorySettingsContent) {
            val previous = visibility.rendered
            val before = previous.parts()
            val after = next.parts()
            val nativeMetadata = listOf("points", "thumbnails", "compact-points", "domain").any {
                name.endsWith("-$it-hide") || name.endsWith("-$it-show")
            }
            val changed = before.keys.filter { !nativeMetadata && before[it] != after[it] }
            val exiting = changed.filter { before[it] != null }
            val entering = changed.filter { after[it] != null }
            val startHeight = cardHeight()
            val startPixels = frame(true)
            target = next
            val samples = mutableListOf<Map<String, Float>>()
            val heights = mutableSetOf<Int>()
            repeat(60) { index ->
                val pixels = frame(index == 4)
                samples += changed.associateWith(visibility::alpha)
                heights += cardHeight()
                if (index == 4 && exiting.isNotEmpty()) {
                    assertEquals(previous, visibility.rendered, "$name: retain outgoing content while it fades")
                    assertEquals(startHeight, cardHeight(), "$name: don't collapse before fading out")
                    assertNotEquals(startPixels, pixels, "$name: the rendered pixels must actually fade")
                }
                if (index == 12 && exiting.isEmpty() && entering.isNotEmpty()) {
                    assertTrue(entering.all { visibility.alpha(it) > 0f }, "$name: fade in during movement, not after a full layout tween")
                }
                if (index == 4 && nativeMetadata) {
                    assertEquals(next, visibility.rendered, "$name: native metadata animation starts immediately")
                }
                for (key in entering.filter { it == "summary" ||
                    (it == "preview-image" && next.style.previewImageMode == StoryPreviewMode.LARGE) }) {
                    if (visibility.alpha(key) < .05f) continue
                    val all = nodes()
                    val content = all.single { node ->
                        if (key == "summary") node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == model.previewText } == true
                        else node.config.getOrNull(SemanticsProperties.TestTag) == "story-motion-preview-image"
                    }.boundsInRoot
                    val following = all.firstOrNull { node ->
                        if (key == "summary") node.config.getOrNull(SemanticsProperties.TestTag) == "story-motion-metadata"
                        else node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == model.title } == true
                    }?.boundsInRoot ?: continue
                    assertTrue(content.top + content.height * visibility.revealedHeight(key) <= following.top + 2f,
                        "$name frame $index: early $key reveal must not draw across the moving content below")
                }
                System.getenv("HARMONIC_VISIBILITY_FRAMES")?.takeIf { name.contains("LARGE-TITLE-summary") }?.let { root ->
                    val dir = File(root, name).apply { mkdirs() }
                    scene.render(time).use { image -> image.encodeToData()!!.use {
                        File(dir, "frame-${index.toString().padStart(3, '0')}.png").writeBytes(it.bytes)
                    } }
                }
            }
            assertEquals(next, visibility.rendered, "$name: reach the selected setting")
            after.filterValues { it != null }.keys.forEach {
                assertEquals(1f, visibility.revealedHeight(it), "$name: reveal must finish")
            }
            for (key in changed) {
                assertTrue(samples.count { it.getValue(key) in .01f.. .99f } >= 3, "$name: $key must fade through intermediate opacity")
                assertEquals(if (after[key] == null) 0f else 1f, visibility.alpha(key), "$name: $key final opacity")
            }
            if (kotlin.math.abs(cardHeight() - startHeight) > 5) assertTrue(heights.size >= 5, "$name: height must animate")
        }
        val edits: List<Pair<String, (StorySettingsContent) -> StorySettingsContent>> = listOf(
            "summary" to { it.copy(style = it.style.copy(showPreviewText = false)) },
            "categories" to { it.copy(model = it.model.copy(categoryLabels = emptyList(), categoryIds = emptyList())) },
            "compact" to { it.copy(style = it.style.copy(compact = true)) },
            "points" to { it.copy(style = it.style.copy(showPoints = false)) },
            "thumbnails" to { it.copy(style = it.style.copy(showFavicon = false)) },
            "domain" to { it.copy(style = it.style.copy(includeTopLevelDomain = false)) },
            "compact-points" to { it.copy(style = it.style.copy(compactPoints = true)) },
            "comment-count" to { it.copy(style = it.style.copy(showCommentCount = false)) },
            "indices" to { it.copy(style = it.style.copy(showIndex = false)) },
        )
        try {
            for (mode in StoryPreviewMode.entries) for (location in CategoryTagLocation.entries) {
                val baseline = StorySettingsContent(model, baseStyle.copy(previewImageMode = mode, categoryTagLocation = location))
                target = baseline; settle()
                for ((name, edit) in edits) {
                    change("$mode-$location-$name-hide", edit(baseline))
                    change("$mode-$location-$name-show", baseline)
                }
                val geometryEdits = listOf(
                    baseline.style.copy(commentsOnLeft = true),
                    baseline.style.copy(borderlessLargeImage = !baseline.style.borderlessLargeImage),
                    baseline.style.copy(textSize = baseline.style.textSize + 4f),
                    baseline.style.copy(tintCard = !baseline.style.tintCard),
                    baseline.style.copy(categoryTagLocation = if (location == CategoryTagLocation.TITLE) CategoryTagLocation.METADATA else CategoryTagLocation.TITLE),
                ) + DisplayStyle.entries.map { baseline.style.copy(displayStyle = it) }
                for ((index, style) in geometryEdits.withIndex()) {
                    change("$mode-$location-geometry-$index", baseline.copy(style = style))
                    change("$mode-$location-geometry-$index-return", baseline)
                }
                for (otherMode in StoryPreviewMode.entries.filter { it != mode }) {
                    change("$mode-$location-image-$otherMode", baseline.copy(style = baseline.style.copy(previewImageMode = otherMode)))
                    change("$mode-$location-image-return", baseline)
                }
                // Reverse during both fade-out and expansion, and combine controls before settling.
                for (interruptAt in listOf(4, 25)) {
                    target = baseline.copy(style = baseline.style.copy(compact = true, showPreviewText = false))
                    repeat(interruptAt) { frame() }
                    target = baseline
                    if (interruptAt == 4) {
                        // Allow the cancellation frame, then require increasing opacity rather
                        // than a plateau while an unnecessary layout delay runs.
                        repeat(2) { frame() }
                        val reversingAlpha = visibility.alpha("summary")
                        repeat(4) { frame() }
                        assertTrue(visibility.alpha("summary") > reversingAlpha, "Reversing a fade should recover immediately")
                    }
                    settle()
                    assertEquals(baseline, visibility.rendered)
                    baseline.parts().filterValues { it != null }.keys.forEach { assertEquals(1f, visibility.alpha(it), "Reversal must not leave $it hidden") }
                }
            }
        } finally { scene.close(); app.close() }
    }
}
