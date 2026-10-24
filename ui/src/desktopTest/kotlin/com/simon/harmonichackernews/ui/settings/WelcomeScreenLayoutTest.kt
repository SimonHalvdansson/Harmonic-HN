package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.harmonic_app_icon
import org.jetbrains.compose.resources.painterResource
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WelcomeScreenLayoutTest {
    private data class WindowCase(
        val width: Int,
        val height: Int,
        val fontScale: Float = 1f,
        val rightInset: Int = 0,
        val topInset: Int = 0,
        val bottomInset: Int = 0,
        val density: Float = 1f,
    )

    @Test
    fun contentIsConstrainedAndActionRemainsReachableAcrossWindowSizes() = SwingUtilities.invokeAndWait {
        val settings = AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot()
        for ((width, height, fontScale, rightInset, topInset, bottomInset, density) in listOf(
            WindowCase(400, 900),
            WindowCase(840, 1000),
            WindowCase(1280, 900),
            WindowCase(740, 420),
            WindowCase(400, 800, 1.6f),
            WindowCase(466, 678, rightInset = 84, topInset = 17, bottomInset = 34),
            WindowCase(678, 466, rightInset = 84, topInset = 17, bottomInset = 34),
            WindowCase(1080, 2424, density = 2.625f),
        )) {
            val bootstrap = DesktopHarmonicAppBootstrap.inMemory("WelcomeLayoutTest")
            var applied: Boolean? = null
            val scene = ImageComposeScene(width, height, Density(density, fontScale)) {
                val theme = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(theme.colorScheme, theme.dark) {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                        WelcomeScreenContent(
                            styleChooser = false,
                            initialExpressive = true,
                            storyPreferences = settings.story,
                            commentPreferences = settings.comments,
                            onApplyPreset = { applied = it },
                            filterButtonColors = harmonicFilterButtonColors(),
                            contentWindowInsets = WindowInsets(top = topInset, right = rightInset, bottom = bottomInset),
                            launcherIcon = { Image(painterResource(Res.drawable.harmonic_app_icon), null, Modifier.size(64.dp).testTag("welcome-icon")) },
                        )
                    }
                }
            }
            try {
                var time = 0L
                fun settle() { repeat(30) { scene.render(time.also { time += 16_666_667L }).close() } }
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                fun tagged(tag: String) = allNodes().single {
                    it.config.getOrNull(SemanticsProperties.TestTag) == tag
                }
                fun capture(preset: String) {
                    System.getenv("HARMONIC_WELCOME_CAPTURE_DIR")?.let { directory ->
                        tagged("welcome-content").config[SemanticsActions.ScrollBy].action?.invoke(0f, -10000f)
                        settle()
                        scene.render(time).use { image ->
                            image.encodeToData(EncodedImageFormat.PNG)?.use { data ->
                                File(directory).mkdirs()
                                File(directory, "welcome-${width}x${height}-${fontScale}-$preset.png").writeBytes(data.bytes)
                            }
                        }
                    }
                }
                settle()
                val content = tagged("welcome-content").boundsInRoot
                val action = tagged("welcome-action").boundsInRoot
                val surface = tagged("welcome-surface").boundsInRoot
                assertEquals(0f, surface.left, 1f)
                assertEquals(0f, surface.top, 1f)
                assertEquals(width.toFloat(), surface.right, 1f)
                assertEquals(height.toFloat(), surface.bottom, 1f)
                assertTrue(content.width <= 560f * density, "Content must stay readable on wide windows")
                assertEquals(width / 2f, content.center.x, 1f)
                assertEquals((width - rightInset) / 2f, action.center.x, 1f)
                assertTrue(action.right <= width - rightInset)
                assertTrue(action.bottom <= height - bottomInset && action.top > height - bottomInset - 140f * density)
                val title = allNodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Welcome to Harmonic" } == true
                }.boundsInRoot
                assertTrue(tagged("welcome-artwork").boundsInRoot.bottom <= title.top + 8f)
                assertTrue(tagged("welcome-icon").boundsInRoot.bottom <= title.top)
                assertTrue(tagged("welcome-icon").boundsInRoot.width <= 120f * density, "Launcher artwork must scale once with screen density")
                assertTrue(title.top >= topInset)
                assertTrue(title.right <= width - rightInset - 24f * density)
                capture("expressive")
                // On short screens, scrolling the final content into view must not move the action.
                tagged("welcome-content").config[SemanticsActions.ScrollBy].action?.invoke(0f, 10000f)
                settle()
                assertEquals(action, tagged("welcome-action").boundsInRoot)
                val previewBounds = tagged("welcome-preview").boundsInRoot
                val presetBounds = tagged("welcome-presets").boundsInRoot
                val simple = allNodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Simple" } == true
                }.boundsInRoot.center
                scene.sendPointerEvent(PointerEventType.Press, simple)
                scene.sendPointerEvent(PointerEventType.Release, simple)
                settle()
                assertEquals(previewBounds, tagged("welcome-preview").boundsInRoot)
                assertEquals(presetBounds, tagged("welcome-presets").boundsInRoot)

                assertTrue(presetBounds.bottom < action.top, "Style presets must clear the floating action")
                scene.sendPointerEvent(PointerEventType.Press, action.center)
                scene.sendPointerEvent(PointerEventType.Release, action.center)
                settle()
                assertEquals(false, applied)
                capture("simple")
            } finally {
                scene.close()
                bootstrap.close()
            }
        }
    }
}
