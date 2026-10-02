package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.content.CommentRow
import com.simon.harmonichackernews.ui.content.CommentRowStyle
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Exercises the actual row capture and dialog compositor across a retained layout resize. */
class CommentActionRotationTest {
    @Test
    fun openingAfterRotationAndReturningAfterAnotherRotationUseTheCurrentRow() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("CommentRotationTest")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        bootstrap.scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(
            bootstrap.app, bootstrap.scene,
            checkNotNull(bootstrap.scene.navigation.state.value.storyRequest), scope,
        )
        val controller = binding.controller
        val settings = CommentDisplaySettings.from(
            bootstrap.app.userSettings.comments, false, false, false, false,
        ).copy(displayStyle = DisplayStyle.OUTLINED, userAvatarsEnabled = false)
        val comment = PortableCommentItem(
            CommentSnapshot(201, author = "rotation_test", text = "A comment should return to its current position after the screen rotates.",
                expandedAnchorText = "A comment should return to its current position after the screen rotates."),
            CommentPresentationSnapshot(expanded = true),
        )
        val landscape = mutableStateOf(false)
        var rowBounds = Rect.Zero
        val scene = ImageComposeScene(900, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    Box(Modifier.size(if (landscape.value) 840.dp else 420.dp, if (landscape.value) 420.dp else 840.dp)
                        .background(HarmonicTheme.colors.background)) {
                        Box(Modifier.fillMaxSize().padding(top = if (landscape.value) 60.dp else 220.dp)) {
                            CommentRow(
                                comment = comment,
                                style = CommentRowStyle(DisplayStyle.OUTLINED, 16f, false, false, "none", false, settings.font, animateChanges = false),
                                storyAuthor = null, accountUser = null, userTag = null,
                                subtreeReplyCount = 0, collapseParent = false, showTopLevelIndicator = false,
                                modifier = Modifier.onGloballyPositioned { rowBounds = it.boundsInWindow() },
                                captureActionSource = controller.isCommentActionOverlayShowing(),
                                suppressActionSource = controller.isCommentActionOverlayShowing() && !controller.shouldKeepCommentActionSourceVisible(comment.id),
                                showActionsOnClick = true,
                                onToggleExpanded = { controller.showCommentActions(comment, it) },
                                onShowActions = {},
                                onActionSourceGeometryChanged = { controller.updateCommentActionSourceGeometry(comment.id, it) },
                                onLinkLongClick = { _, _, _ -> }, onReferenceLongClick = { _, _, _ -> },
                            )
                        }
                        CommentActionOverlay(controller, settings, false, true, TextStyle.Default, {})
                    }
                }
            }
        }
        var time = 0L
        var captureOpening = true
        val evidence = System.getenv("HARMONIC_ROTATION_EVIDENCE")?.let(::File)?.apply { mkdirs() }
        fun frame(name: String? = null): PixelMap {
            time += 16_000_000
            return scene.render(time).use { image ->
                val sourceCovered = controller.isCommentActionOverlayShowing() &&
                    !controller.shouldKeepCommentActionSourceVisible(comment.id)
                val captureName = name ?: when {
                    sourceCovered && controller.commentActionDismissRequest != 0 -> "portrait-exit"
                    sourceCovered && captureOpening -> "landscape-entry".also { captureOpening = false }
                    else -> null
                }
                if (captureName != null && evidence != null) image.encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(evidence, "$captureName.png").writeBytes(it.bytes)
                }
                image.toComposeImageBitmap().toPixelMap()
            }
        }
        fun rowError(a: PixelMap, b: PixelMap): Float {
            var error = 0f
            var samples = 0
            for (y in rowBounds.top.toInt() until rowBounds.bottom.toInt()) {
                for (x in rowBounds.left.toInt() until rowBounds.right.toInt()) {
                    val first = a[x, y]
                    val second = b[x, y]
                    error += kotlin.math.abs(first.red - second.red) +
                        kotlin.math.abs(first.green - second.green) + kotlin.math.abs(first.blue - second.blue)
                    samples++
                }
            }
            return error / samples
        }
        fun open() {
            val resting = frame()
            scene.sendPointerEvent(PointerEventType.Press, rowBounds.center, timeMillis = time / 1_000_000)
            scene.sendPointerEvent(PointerEventType.Release, rowBounds.center, timeMillis = time / 1_000_000 + 1)
            var firstCovered: PixelMap? = null
            repeat(45) {
                val pixels = frame()
                if (firstCovered == null && controller.isCommentActionOverlayShowing() &&
                    !controller.shouldKeepCommentActionSourceVisible(comment.id)) {
                    firstCovered = pixels
                }
            }
            assertNotNull(controller.commentActionOverlay?.sourceGeometry, "The real row must provide a shared transition capture")
            val error = rowError(resting, assertNotNull(firstCovered))
            assertTrue(error < 0.08f, "The first shared entry must cover the rotated row, mean RGB error: $error")
        }
        try {
            repeat(10) { frame() }
            landscape.value = true
            repeat(10) { frame() }
            frame("landscape-row")
            open()
            assertTrue(checkNotNull(controller.commentActionOverlay?.sourceBounds).width > 700f,
                "Opening after rotation must capture the landscape row")
            frame("landscape-dialog")
            landscape.value = false
            repeat(20) { frame() }
            frame("portrait-dialog-after-rotation")
            controller.requestDismissCommentActions()
            var lastCovered: PixelMap? = null
            repeat(45) {
                val image = frame()
                if (controller.isCommentActionOverlayShowing() && !controller.shouldKeepCommentActionSourceVisible(comment.id)) {
                    lastCovered = image
                }
            }
            val resting = frame("portrait-row-restored")
            val returning = assertNotNull(lastCovered)
            val error = rowError(resting, returning)
            assertTrue(error < 0.08f,
                "The final shared exit must cover the current portrait row, mean RGB error: $error")
            assertTrue(!controller.isCommentActionOverlayShowing())
        } finally {
            scene.close()
            binding.close()
            scope.cancel()
            bootstrap.close()
        }
    }
}
