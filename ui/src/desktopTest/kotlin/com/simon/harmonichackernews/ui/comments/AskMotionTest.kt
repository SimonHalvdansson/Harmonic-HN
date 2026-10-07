package com.simon.harmonichackernews.ui.comments

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AskMotionTest {
    @Test
    fun reverseTransformAndCancelledBackRetainTheOriginalDialog() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("DiscussionMotionTest")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        bootstrap.scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(bootstrap.app, bootstrap.scene,
            checkNotNull(bootstrap.scene.navigation.state.value.storyRequest), scope)
        val controller = binding.controller
        val settings = CommentDisplaySettings.from(bootstrap.app.userSettings.comments, false, false, false, false)
        val body = "A familiar lighthouse looked much farther away than expected. The observer counted " +
            "the flashes, checked the chart, and wondered whether a temperature inversion could explain it."
        val comment = PortableCommentItem(
            CommentSnapshot(201, author = "observer", text = body, expandedAnchorText = body),
            CommentPresentationSnapshot(expanded = true),
        )
        val scene = ImageComposeScene(680, 920, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        CommentActionOverlay(controller, settings, false, true, TextStyle.Default, {})
                    }
                }
            }
        }
        val evidence = System.getenv("HARMONIC_DISCUSSION_EVIDENCE")?.let(::File)?.apply { mkdirs() }
        var time = 0L
        fun frame(name: String? = null) = scene.render(time.also { time += 16_000_000 }).use { image ->
            if (name != null && evidence != null) image.encodeToData(EncodedImageFormat.PNG)!!.use {
                File(evidence, "$name.png").writeBytes(it.bytes)
            }
            image.toComposeImageBitmap().toPixelMap()
        }
        try {
            controller.restoreCommentActions(comment)
            repeat(50) { frame() }
            val before = frame("dialog")
            controller.openCommentAsk()
            repeat(50) { frame() }
            frame("discussion")
            controller.updateCommentActionPredictiveBack(0.8f, 0, 400f)
            repeat(3) { frame() }
            frame("back-preview")
            controller.cancelCommentActionPredictiveBack()
            repeat(40) { frame() }
            assertTrue(controller.askOpen)
            controller.closeAsk()
            repeat(30) { index ->
                val pixels = frame("return-${index.toString().padStart(2, '0')}")
                if (index == 11) {
                    // This band straddles the old snapshot's bottom edge while still inside
                    // the moving surface. A second background/shadow creates a visible seam.
                    val expected = pixels[340, 575]
                    for (y in 550..575) for (x in 180..500) {
                        val pixel = pixels[x, y]
                        val difference = abs(pixel.red - expected.red) + abs(pixel.green - expected.green) +
                            abs(pixel.blue - expected.blue)
                        assertTrue(difference < 0.01f, "Only one surface may be visible at the returning bottom edge")
                    }
                }
            }
            val after = frame("restored")
            assertFalse(controller.askOpen)
            assertTrue(controller.commentActionOverlay != null)
            var totalDifference = 0.0
            for (y in 0 until 920) for (x in 0 until 680) {
                totalDifference += abs(before[x, y].red - after[x, y].red) +
                    abs(before[x, y].green - after[x, y].green) + abs(before[x, y].blue - after[x, y].blue)
            }
            assertTrue(totalDifference / (680 * 920) < 0.01, "Returning must restore the same dialog pixels")
        } finally {
            scene.close()
            binding.close()
            scope.cancel()
            bootstrap.close()
        }
    }
}
