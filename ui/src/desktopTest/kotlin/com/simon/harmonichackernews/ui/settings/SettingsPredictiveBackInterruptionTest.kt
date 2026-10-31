package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.navigation.LocalPredictiveBackCompletion
import com.simon.harmonichackernews.ui.navigation.PredictiveBackCompletion
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsPredictiveBackInterruptionTest {
    @Test
    fun rapidSecondBackDoesNotReplayTheReleasedPageExit() = verifyFollowingBack(cancel = false)

    @Test
    fun cancellingTheSecondBackKeepsTheFirstPopWithoutReplayingItsExit() = verifyFollowingBack(cancel = true)

    private fun verifyFollowingBack(cancel: Boolean) = SwingUtilities.invokeAndWait {
        val navigation = SettingsNavigationStore(twoPane = false).apply {
            navigateTo(SettingsSection.Appearance)
            navigateTo(SettingsSection.Theme, true)
        }
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val lifecycle = SettingsTestOwner()
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsBackInterruptionTest")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val completion = PredictiveBackCompletion()
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycle,
                    LocalViewModelStoreOwner provides lifecycle,
                    LocalHarmonicUiDependencies provides dependencies,
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalPredictiveBackCompletion provides completion,
                ) {
                    val back = rememberSettingsPredictiveBack(navigation, false, true, {})
                    SettingsNavigationShell(
                        navigation, PaneScaffoldDirective(1, 0.dp, 1, 0.dp, 360.dp, emptyList()),
                        false, 0.dp, {}, {},
                        predictiveBackOverlay = back.overlay,
                        completedPredictiveBack = back.completed,
                        renderList = { _, _, _, _ -> Box(Modifier.fillMaxSize().background(Color.Green)) },
                        renderDetail = { section, _, _, _ ->
                            Box(Modifier.fillMaxSize().background(
                                if (section == SettingsSection.Theme) Color.Blue else Color.Yellow,
                            ))
                        },
                    )
                }
            }
        }
        var time = 0L
        fun frame(): Color = scene.render(time.also { time += 16_666_667L }).use {
            val pixels = it.toComposeImageBitmap().toPixelMap()
            pixels[150, 300]
        }
        fun event(progress: Float) = NavigationEvent(
            touchX = progress * 150f, touchY = 300f, progress = progress,
            swipeEdge = NavigationEvent.EDGE_LEFT,
        )
        try {
            repeat(60) { frame() }
            assertEquals(Color.Blue, frame())
            input.backStarted(event(0f))
            input.backProgressed(event(.65f))
            repeat(2) { frame() }
            input.backCompleted()
            repeat(2) { frame() }
            input.backStarted(event(0f))
            input.backProgressed(event(.65f))
            repeat(2) { frame() }
            if (cancel) input.backCancelled() else input.backCompleted()
            repeat(60) { index ->
                val color = frame()
                assertTrue(color.blue < .1f, "Released Theme page flashed again at frame $index: $color")
            }
            assertEquals(if (cancel) listOf(SettingsSection.Appearance) else emptyList(), navigation.state.value.detailStack)
            assertEquals(if (cancel) Color.Yellow else Color.Green, frame())
        } finally {
            scene.close()
            dispatcher.dispose()
            lifecycle.viewModelStore.clear()
            bootstrap.close()
        }
    }
}
