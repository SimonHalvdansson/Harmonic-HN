package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.flow.emptyFlow
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

class WelcomePreviewColorTest {
    @Test
    fun presetChangesFadeSurfacesWithoutPassingThroughBlack() = SwingUtilities.invokeAndWait {
        val settings = AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot()
        for (dark in listOf(false, true)) {
            val bootstrap = DesktopHarmonicAppBootstrap.inMemory("WelcomePreviewColorTest")
            val scene = ImageComposeScene(400, 1000, Density(1f)) {
                val theme = HarmonicThemeCatalog.scheme("dynamic", dark)
                HarmonicTheme(theme.colorScheme, theme.dark) {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                        WelcomeScreenContent(
                            styleChooser = false,
                            initialExpressive = true,
                            storyPreferences = settings.story,
                            commentPreferences = settings.comments,
                            onApplyPreset = {},
                            filterButtonColors = harmonicFilterButtonColors(),
                            contentWindowInsets = WindowInsets(0),
                            launcherIcon = {},
                            // Keep the decorative background still and all entrance fades complete.
                            entranceTime = mutableStateOf(10_000.0),
                        )
                    }
                }
            }
            try {
                var time = 0L
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                fun sample(): List<Color> = scene.render(time.also { time += 16_666_667L }).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    listOf("welcome-story-preview", "welcome-comment-preview").map { tag ->
                        val bounds = allNodes().single {
                            it.config.getOrNull(SemanticsProperties.TestTag) == tag
                        }.boundsInRoot
                        // Inside the surface, in the empty gutter to the left of the text.
                        pixels[(bounds.left + 20).toInt(), bounds.center.y.toInt()]
                    }
                }
                fun choose(label: String) {
                    val center = allNodes().single {
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
                    }.boundsInRoot.center
                    scene.sendPointerEvent(PointerEventType.Press, center)
                    scene.sendPointerEvent(PointerEventType.Release, center)
                }
                repeat(35) { sample() }
                val expressive = sample()
                choose("Simple")
                val outgoing = List(20) { sample() }
                val simple = sample()
                choose("Expressive")
                val incoming = List(20) { sample() }
                for ((frameIndex, frame) in (outgoing + incoming).withIndex()) {
                    for (row in frame.indices) {
                        fun channels(color: Color) = listOf(color.red, color.green, color.blue)
                        val from = channels(expressive[row])
                        val to = channels(simple[row])
                        for ((channel, value) in channels(frame[row]).withIndex()) {
                            assertTrue(value >= minOf(from[channel], to[channel]) - .04f,
                                "Surface must not darken below either endpoint (dark=$dark, row=$row, frame=$frameIndex, channel=$channel, value=$value, endpoints=${from[channel]}, ${to[channel]})")
                        }
                    }
                }
            } finally {
                scene.close()
                bootstrap.close()
            }
        }
    }
}
