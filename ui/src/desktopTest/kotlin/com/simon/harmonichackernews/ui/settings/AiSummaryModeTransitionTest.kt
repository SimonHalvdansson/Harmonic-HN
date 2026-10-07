package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.settings.AiSummaryMode
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AiSummaryModeTransitionTest {
    @Test
    fun slideKeepsLayoutStableAndPaintsSmoothlyToItsFinalPosition() = SwingUtilities.invokeAndWait {
        verifySlide(Density(1f))
    }

    @Test
    fun slideRemainsStableAtRetinaDensity() = SwingUtilities.invokeAndWait {
        verifySlide(Density(2f))
    }

    private fun verifySlide(density: Density) {
        val mode = mutableStateOf(AiSummaryMode.CLOUD)
        var layoutX = 0f
        var markerY = 0f
        var height = 0
        var transitionWidth = 0
        var contentWidth = 0
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("AnimationTest")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        val navigation = SettingsNavigationStore(SettingsSection.AiSummary, twoPane = true)
        // Keep the real adaptive scaffold: an isolated AnimatedContent misses the
        // lookahead/visible-width mismatch responsible for the last-frame jump.
        val directive = PaneScaffoldDirective(2, 24.dp, 1, 0.dp, 360.dp, emptyList())
        val scene = ImageComposeScene((1200 * density.density).roundToInt(), (750 * density.density).roundToInt(), density) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies, LocalLifecycleOwner provides lifecycleOwner) {
                    SettingsNavigationShell(
                        navigation, directive, false, 24.dp, {}, {},
                        renderList = { _, _, _, _ -> },
                        renderDetail = { _, _, _, _ ->
                            SettingsPage("AI summarization", false, {}) {
                                item {
                                    SettingsCategory("Model") {
                                        SegmentedSetting(
                                            options = listOf(AiSummaryMode.LOCAL to "Local", AiSummaryMode.CLOUD to "Cloud"),
                                            selected = mode.value,
                                            onSelected = {},
                                        )
                                        AiSummaryModeTransition(mode.value, Modifier.onGloballyPositioned {
                                            height = it.size.height
                                            transitionWidth = it.size.width
                                        }) { current ->
                                            Box(Modifier.fillMaxWidth().animateContentSize()
                                                .onGloballyPositioned {
                                                    if (current == mode.value) contentWidth = it.size.width
                                                }
                                                .height(if (current == AiSummaryMode.LOCAL) 900.dp else 180.dp)) {
                                                Box(Modifier.padding(start = 120.dp).size(12.dp)
                                                    .onGloballyPositioned {
                                                        if (current == mode.value) {
                                                            layoutX = it.positionInRoot().x
                                                            markerY = it.positionInRoot().y + 6 * density.density
                                                        }
                                                    }
                                                    .background(if (current == AiSummaryMode.LOCAL) Color.Blue else Color.Red))
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(400.dp)) }
                            }
                        },
                    )
                }
            }
        }
        try {
            var time = 0L
            fun frame(): Sample {
                time += 16_000_000
                return scene.render(time).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    val row = markerY.roundToInt().coerceIn(0, pixels.height - 1)
                    val paintedX = (0 until pixels.width).firstOrNull { x ->
                        val pixel = pixels[x, row]
                        if (mode.value == AiSummaryMode.LOCAL) {
                            pixel.blue > 0.8f && pixel.red < 0.8f && pixel.green < 0.8f
                        } else {
                            pixel.red > 0.8f && pixel.blue < 0.8f && pixel.green < 0.8f
                        }
                    }
                    Sample(layoutX, paintedX, height, transitionWidth, contentWidth)
                }
            }
            repeat(10) { frame() }
            for (target in listOf(AiSummaryMode.LOCAL, AiSummaryMode.CLOUD, AiSummaryMode.LOCAL)) {
                mode.value = target
                val frames = List(40) { frame() }
                val settled = frames.last()
                assertEquals(settled.layoutX.roundToInt(), settled.paintedX)
                assertTrue(frames.all { it.layoutX == settled.layoutX }, "The horizontal slide must not move layout coordinates: $frames")
                assertTrue(
                    frames.all { it.transitionWidth == settled.transitionWidth && it.contentWidth == settled.transitionWidth },
                    "Both modes must keep the full available width, including nested size animations: $frames",
                )
                val painted = frames.mapNotNull { it.paintedX }
                assertTrue(painted.distinct().size > 2, "The content must still visibly slide: $frames")
                assertTrue(painted.zipWithNext().all { (a, b) -> if (target == AiSummaryMode.LOCAL) b >= a else b <= a }, "The slide must approach its final position without reversing or snapping back: $painted")
                assertEquals(List(20) { settled.paintedX }, frames.takeLast(20).map { it.paintedX })
                val expectedHeight = if (target == AiSummaryMode.LOCAL) 900 else 180
                assertEquals((expectedHeight * density.density).roundToInt(), settled.height)
                assertTrue(frames.map { it.height }.distinct().size > 2, "The container must still animate its height")
            }
        } finally {
            scene.close()
            bootstrap.close()
        }
    }

    private data class Sample(
        val layoutX: Float,
        val paintedX: Int?,
        val height: Int,
        val transitionWidth: Int,
        val contentWidth: Int,
    )
}
