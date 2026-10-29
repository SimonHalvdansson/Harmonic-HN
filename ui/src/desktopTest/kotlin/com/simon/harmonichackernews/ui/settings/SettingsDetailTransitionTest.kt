package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsDetailTransitionTest {
    @Test
    fun nestedMotionReversesWhileCategoryChangesAndTheListKeepTheirOwnMotion() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsTransitionTest")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val lifecycleOwner = SettingsTestOwner()
        val navigation = SettingsNavigationStore(twoPane = true)
        var listY = 0
        var detailY = 0
        val scene = ImageComposeScene(1200, 750, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies, LocalLifecycleOwner provides lifecycleOwner, LocalViewModelStoreOwner provides lifecycleOwner) {
                    SettingsNavigationShell(
                        navigation, PaneScaffoldDirective(2, 24.dp, 1, 0.dp, 360.dp, emptyList()),
                        false, 24.dp, {}, {},
                        renderList = { _, _, _, _ ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(100.dp).onGloballyPositioned {
                                    listY = (it.positionInRoot().y + 50).roundToInt()
                                }.background(Color.Green))
                            }
                        },
                        renderDetail = { _, _, _, _ ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Box(Modifier.size(100.dp).onGloballyPositioned {
                                    detailY = (it.positionInRoot().y + 50).roundToInt()
                                }.background(Color.Red))
                            }
                        },
                    )
                }
            }
        }
        try {
            var time = 0L
            fun frame(): Pair<List<Int>, Int> {
                time += 16_000_000
                return scene.render(time).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    val listPixels = (0 until pixels.width).filter { x ->
                        val color = pixels[x, listY]
                        color.green - color.red > 0.1f
                    }
                    val detailWidth = (0 until pixels.width).count { x ->
                        val color = pixels[x, detailY]
                        color.red - color.green > 0.1f
                    }
                    listPixels to detailWidth
                }
            }
            repeat(40) { frame() }
            val stationary = frame()
            assertEquals(100, stationary.first.size)
            assertEquals(100, stationary.second)

            fun verifyMotion(outgoing: Int, incoming: Int, navigate: () -> Unit) {
                navigate()
                val frames = List(40) { frame() }
                assertTrue(frames.all { it.first == stationary.first }, "The list must stay stationary")
                val widths = frames.map { it.second }
                assertEquals(outgoing, widths[5].compareTo(100), "Outgoing widths: $widths")
                assertEquals(incoming, widths[12].compareTo(100), "Incoming widths: $widths")
                assertEquals(100, widths.last())
                assertFalse(scene.hasInvalidations(), "The transition must settle")
            }
            verifyMotion(1, -1) { navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true) }
            verifyMotion(-1, 1) { navigation.navigateBack() }
            verifyMotion(0, -1) { navigation.navigateTo(SettingsSection.Comments) }
            verifyMotion(1, -1) { navigation.navigateTo(SettingsSection.ThreadDepth, preserveCurrentDetail = true) }
            verifyMotion(0, -1) { navigation.navigateTo(SettingsSection.Comments) }
        } finally {
            scene.close()
            lifecycleOwner.viewModelStore.clear()
            bootstrap.close()
        }
    }
}
