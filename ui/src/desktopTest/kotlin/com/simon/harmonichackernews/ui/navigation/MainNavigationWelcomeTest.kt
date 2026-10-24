package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainNavigationWelcomeTest {
    @Test
    fun welcomeUsesTheNormalActivityTransitionAndRetainsItsSettingsParent() = SwingUtilities.invokeAndWait {
        val navigation = MainNavigationStore().apply { openSettings("appearance") }
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val completion = PredictiveBackCompletion()
        var settingsMounts = 0
        var welcomeMounts = 0
        var welcomeIdentity: Any? = null
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) {
                CompositionLocalProvider(
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalPredictiveBackCompletion provides completion,
                ) {
                    val snapshot by navigation.state.collectAsState()
                    val back = rememberDefaultActivityPredictiveBackState(
                        requestKey = snapshot.welcomeRequest?.let { MainNavigationSurfaceKey(MainDestination.WELCOME, it.serial) },
                        enabled = snapshot.currentDestination == MainDestination.WELCOME,
                        completedFrameHoldCount = 3,
                        onBack = navigation::closeWelcome,
                    )
                    val plan = mainNavigationScenePlan(snapshot, false)
                    val gesture = back.animation
                    val preview = rememberMainNavigationBackPreview(plan, gesture,
                        gesture?.enterModifier ?: Modifier, gesture?.exitModifier ?: Modifier)
                    HarmonicAppRoot(
                        plan = plan, preview = preview,
                        completedPredictiveBack = setOfNotNull(back.completedRequestKey),
                        stories = { _, _ -> Box(Modifier.fillMaxSize().background(Color.Green)) },
                        comments = { _, _ -> },
                        settings = {
                            DisposableEffect(Unit) { settingsMounts++; onDispose { } }
                            Box(Modifier.fillMaxSize().background(Color.Cyan))
                        },
                        submissions = { _, _, _ -> }, editor = {}, immersive = {}, foreground = {},
                        welcome = {
                            welcomeIdentity = remember { Any() }
                            DisposableEffect(Unit) { welcomeMounts++; onDispose { } }
                            Box(Modifier.fillMaxSize().background(Color.Magenta))
                        },
                    )
                }
            }
        }
        var time = 0L
        fun frame() = scene.render(time.also { time += 16_666_667L }).use {
            it.toComposeImageBitmap().toPixelMap()
        }
        fun settle() { repeat(90) { frame() } }
        fun event(progress: Float) = NavigationEvent(
            touchX = progress * 150, touchY = 300f,
            progress = progress, swipeEdge = NavigationEvent.EDGE_LEFT,
        )
        try {
            settle()
            navigation.openWelcome(styleChooser = true)
            repeat(3) { assertEquals(Color.Cyan, frame()[150, 300], "Opening must not flash an opaque welcome page") }
            settle()
            assertEquals(Color.Magenta, frame()[150, 300])
            val identity = welcomeIdentity
            input.backStarted(event(0f))
            input.backProgressed(event(.8f))
            repeat(30) { frame() }
            val held = frame()
            assertEquals(Color.Magenta, held[150, 300], "Welcome stays opaque during the held gesture")
            assertTrue(held[15, 300].green > .5f, "The retained Settings page must be revealed")
            input.backCancelled()
            settle()
            assertEquals(MainDestination.WELCOME, navigation.state.value.currentDestination)
            assertSame(identity, welcomeIdentity)
            input.backStarted(event(0f))
            input.backProgressed(event(.8f))
            repeat(10) { frame() }
            input.backCompleted()
            settle()
            assertEquals(MainDestination.SETTINGS, navigation.state.value.currentDestination)
            repeat(30) { assertEquals(Color.Cyan, frame()[150, 300], "A completed Back must not replay an exit") }
            assertEquals(1, settingsMounts)
            assertEquals(1, welcomeMounts)
        } finally {
            scene.close()
            dispatcher.dispose()
        }
    }
}
