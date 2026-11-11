package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
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
    @Test fun pointsPreviewFadesWithoutClippingAndCanReverseWithoutReplacingComments() = SwingUtilities.invokeAndWait {
        val settings = mutableStateOf(CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        ))
        val scene = ImageComposeScene(420, 1100, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) { HeaderDisplayPreview(settings.value, true, {}) }
        }
        try {
            var time = 0L
            fun frames(count: Int) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            fun nodes() = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
            fun textNodes(text: String) = nodes().filter {
                it.config.getOrNull(SemanticsProperties.Text)?.any { value -> value.text == text } == true
            }
            for (details in CommentsHeaderMetaInfo.entries) for (imageVisible in listOf(false, true)) {
                settings.value = settings.value.copy(headerMetaInfo = details,
                    showHeaderPreviewImage = imageVisible, showHeaderPoints = true)
                frames(40)
                fun extent(): Float {
                    val bounds = nodes().single {
                        it.config.getOrNull(SemanticsProperties.TestTag) == "header-settings-visibility"
                    }.boundsInRoot
                    return if (details == CommentsHeaderMetaInfo.ON_IMAGE && !imageVisible) bounds.height else bounds.width
                }
                val fullExtent = extent()
                val commentId = textNodes(SettingsStoryPreviewModel.commentCount.toString()).single().id
                val pointsId = textNodes(SettingsStoryPreviewModel.points.toString()).single().id
                val pointsWidth = textNodes(SettingsStoryPreviewModel.points.toString()).single().boundsInRoot.width
                settings.value = settings.value.copy(showHeaderPoints = false)
                frames(6)
                if (details == CommentsHeaderMetaInfo.ON_IMAGE && imageVisible) {
                    assertEquals(fullExtent, extent(), "Integrated badges should fade at full size")
                } else {
                    assertTrue(extent() > 0f && extent() < fullExtent, "Points space should shrink gradually: $details")
                }
                if (details != CommentsHeaderMetaInfo.SMALL) {
                    assertEquals(pointsWidth, textNodes(SettingsStoryPreviewModel.points.toString()).single().boundsInRoot.width,
                        "The complete score must remain visible throughout the fade: $details")
                }
                assertEquals(commentId, textNodes(SettingsStoryPreviewModel.commentCount.toString()).single().id)
                settings.value = settings.value.copy(showHeaderPoints = true)
                frames(30)
                assertEquals(fullExtent, extent())
                assertEquals(pointsId, textNodes(SettingsStoryPreviewModel.points.toString()).single().id,
                    "Reversing the toggle should retain the fading score")
                settings.value = settings.value.copy(showHeaderPoints = false)
                frames(30)
                assertTrue(textNodes(SettingsStoryPreviewModel.points.toString()).isEmpty())
                assertEquals(commentId, textNodes(SettingsStoryPreviewModel.commentCount.toString()).single().id)
            }
        } finally { scene.close() }
    }

    @Test fun pointsToggleWorksAcrossHeaderLayoutsAndKeepsCommentsAndVoting() = SwingUtilities.invokeAndWait {
        val settings = mutableStateOf(CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        ))
        val sample = mutableStateOf(HeaderPreviewSample.LINK_WITH_IMAGE)
        val scene = ImageComposeScene(420, 1100, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) { HeaderDisplayPreview(settings.value, true, {}, sample.value) }
        }
        try {
            var time = 0L
            fun settle() { repeat(30) { scene.render(time.also { time += 16_666_667L }).close() } }
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            for (signedIn in listOf(false, true)) for (details in CommentsHeaderMetaInfo.entries) {
                for (layout in CommentsHeaderLayout.entries) for (imageVisible in listOf(false, true)) {
                    for (post in listOf(HeaderPreviewSample.LINK_WITH_IMAGE, HeaderPreviewSample.TEXT_POST)) {
                        sample.value = post
                        for (points in listOf(true, false, true)) {
                            settings.value = settings.value.copy(hasAccountDetails = signedIn, headerMetaInfo = details,
                                headerLayout = layout, showHeaderPreviewImage = imageVisible, showHeaderPoints = points)
                            settle()
                            val nodes = scene.semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
                            val texts = nodes.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                            val context = "$details / $layout / image=$imageVisible / $post / signedIn=$signedIn"
                            assertEquals(points, SettingsStoryPreviewModel.points.toString() in texts, context)
                            assertTrue(SettingsStoryPreviewModel.commentCount.toString() in texts, "Comments must stay visible: $context")
                            if (signedIn && details == CommentsHeaderMetaInfo.BUTTONS) {
                                assertTrue(nodes.any {
                                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Upvote") == true &&
                                        it.config.contains(SemanticsActions.OnClick)
                                }, "Hiding the score must preserve the voting action: $context")
                            }
                        }
                    }
                }
            }
        } finally { scene.close() }
    }

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
            fun verifyMetrics(stacked: Boolean, onImage: Boolean = false) {
                val points = metric("${SettingsStoryPreviewModel.points} points")
                val comments = metric("${SettingsStoryPreviewModel.commentCount} comments")
                if (stacked) {
                    if (onImage) assertEquals(points.right, comments.right)
                    else assertEquals(points.left, comments.left)
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
                verifyMetrics(stacked = layout == CommentsHeaderLayout.BALANCED, onImage = true)
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
