package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsIdleRenderingTest {
    @Test fun stationaryTwoPaneSettingsStopsRequestingFrames() = verify(true, true)
    @Test fun stationarySettingsListStopsRequestingFrames() = verify(true, false)
    @Test fun stationarySettingsDetailStopsRequestingFrames() = verify(false, true)

    @Test fun interruptedTwoPaneCategoryChangesRemainRenderable() = verify(true, true, interrupted = true)

    private fun verify(realList: Boolean, realDetail: Boolean, interrupted: Boolean = false) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsIdleTest")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        val navigation = SettingsNavigationStore(SettingsSection.Appearance, twoPane = true)
        val directive = PaneScaffoldDirective(2, 24.dp, 1, 0.dp, 360.dp, emptyList())
        val scene = ImageComposeScene(1280, 800, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies, LocalLifecycleOwner provides lifecycleOwner) {
                    SettingsNavigationShell(
                        navigation, directive, false, 24.dp, {}, {},
                        renderList = { selected, selection, back, navigate -> if (realList) SettingsListScreen(selected, selection, false, false, back, navigate) else Text("List") },
                        renderDetail = { section, singlePane, back, navigate ->
                            if (realDetail) PortableSettingsDetail(section, bootstrap.app, bootstrap.scene, singlePane, back, navigate,
                                ColorPainter(Color.Red), "About", "Test", "Test", {}, {}, { _, _ -> }) else Text("Detail")
                        },
                    )
                }
            }
        }
        try {
            var time = 0L
            val sections = if (interrupted) List(5) {
                listOf(SettingsSection.Appearance, SettingsSection.Stories, SettingsSection.Comments,
                    SettingsSection.HeaderDisplay, SettingsSection.WebLinks)
            }.flatten() else listOf(SettingsSection.Appearance, SettingsSection.WebLinks, SettingsSection.Appearance)
            for (section in sections) {
                navigation.navigateTo(section)
                repeat(if (interrupted) 3 else 180) { scene.render(time.also { time += 16_666_667 }).close() }
                if (!interrupted) assertFalse(scene.hasInvalidations(), "Stationary $section must settle after its transition")
            }
            repeat(180) { scene.render(time.also { time += 16_666_667 }).close() }
            assertFalse(scene.hasInvalidations(), "Interrupted category transitions must settle")
        } finally {
            scene.close()
            bootstrap.close()
        }
    }
}
