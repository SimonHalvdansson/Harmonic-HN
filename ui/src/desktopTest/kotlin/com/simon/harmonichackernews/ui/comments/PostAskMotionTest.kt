package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
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
import kotlin.test.*

class PostAskMotionTest {
    @Test
    fun summaryButtonMorphsIntoAskAndBackRestoresTheSummary() = SwingUtilities.invokeAndWait {
        for (dark in listOf(false, true)) {
            val bootstrap = DesktopHarmonicAppBootstrap.inMemory("PostAskMotionTest")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            bootstrap.scene.navigation.openStory(StoryRoute(42))
            val binding = CommentsFeatureBinding.create(bootstrap.app, bootstrap.scene,
                checkNotNull(bootstrap.scene.navigation.state.value.storyRequest), scope)
            val controller = binding.controller
            val settings = CommentDisplaySettings.from(bootstrap.app.userSettings.comments, false, false, false, false)
                .copy(showAdditionalSummaryInfo = false)
            val summary = "A new battery design stores more energy using common materials. " +
                "The prototype lasted longer in laboratory tests, but manufacturing costs and durability " +
                "at commercial scale remain uncertain."
            val story = StoryListItemSnapshot(StorySnapshot(42, title = "A new approach to battery storage"),
                StoryPresentationSnapshot(aiSummaryText = summary, summaryGeneratedSuccessfully = true,
                    aiSummarySourceText = "The original article, including the test methods and measurements."))
            val scene = ImageComposeScene(400, 800, Density(1f)) {
                val palette = HarmonicThemeCatalog.resolve(if (dark) "dark" else "material_light", dark)
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                        Box(Modifier.fillMaxSize().background(HarmonicTheme.colors.background)) {
                            StoryAiSummary(story, settings, {}, onAsk = controller::openPostAsk,
                                askVisible = controller.postAsk?.coveringSource == true)
                            CommentActionOverlay(controller, settings, false, false, TextStyle.Default, {})
                        }
                    }
                }
            }
            val evidence = System.getenv("HARMONIC_POST_ASK_EVIDENCE")?.let(::File)?.apply { mkdirs() }
            var time = 0L
            fun frame(name: String? = null) = scene.render(time.also { time += 16_000_000 }).use { image ->
                if (name != null && evidence != null) image.encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(evidence, "$name-${if (dark) "dark" else "light"}.png").writeBytes(it.bytes)
                }
                image.toComposeImageBitmap().toPixelMap()
            }
            fun click(x: Float, y: Float): androidx.compose.ui.graphics.PixelMap {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, y))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x, y))
                return frame()
            }
            try {
                repeat(35) { frame() }
                val before = frame("summary")
                val cardColor = before[22, 70]
                fun assertCardNeverDisappears(pixels: androidx.compose.ui.graphics.PixelMap) {
                    val actual = pixels[22, 70]
                    val difference = abs(actual.red - cardColor.red) + abs(actual.green - cardColor.green) +
                        abs(actual.blue - cardColor.blue)
                    assertTrue(difference < 0.02f, "The source card must remain painted on every handoff frame")
                }
                assertCardNeverDisappears(click(336f, 36f))
                assertTrue(controller.askOpen, "The summary's Ask button must open the shared screen")
                assertTrue(controller.isCommentActionOverlayShowing(), "System Back must target Ask")
                assertEquals(summary, controller.postAsk?.subject?.summary)
                repeat(12) { assertCardNeverDisappears(frame()) }
                frame("opening")
                repeat(55) { frame() }
                frame("ask")
                assertTrue(controller.askSurfaceVisible)
                controller.updateCommentActionPredictiveBack(0.8f, 0, 400f)
                repeat(3) { frame() }
                frame("back-preview")
                assertTrue(controller.isCommentActionPredictiveBackActive())
                controller.cancelCommentActionPredictiveBack()
                repeat(40) { frame() }
                assertTrue(controller.askOpen)
                // Shared Settings-style Up returns to the card after the reverse morph.
                click(40f, 32f)
                assertFalse(controller.askOpen)
                assertNotNull(controller.postAsk, "Retain the source through the reverse animation")
                repeat(35) { assertCardNeverDisappears(frame()) }
                val after = frame("restored")
                assertNull(controller.postAsk)
                assertFalse(controller.isCommentActionOverlayShowing())
                assertFalse(controller.askSurfaceVisible)
                var difference = 0.0
                for (y in 0 until 800) for (x in 0 until 400) {
                    difference += abs(before[x, y].red - after[x, y].red) +
                        abs(before[x, y].green - after[x, y].green) + abs(before[x, y].blue - after[x, y].blue)
                }
                assertTrue(difference / (400 * 800) < 0.01, "Returning must restore the same summary pixels")
            } finally {
                scene.close()
                binding.close()
                scope.cancel()
                bootstrap.close()
            }
        }
    }
}
