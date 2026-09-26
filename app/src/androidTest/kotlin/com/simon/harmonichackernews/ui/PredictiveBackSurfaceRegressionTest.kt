package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.settings.SettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.SettingsNavigationStore
import com.simon.harmonichackernews.ui.settings.SettingsPredictiveBackOverlay
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.HarmonicApplication
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.navigation.rememberMainNavigationBackPreview
import com.simon.harmonichackernews.ui.navigation.MainNavigationSurfaceKey
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.navigation.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.navigation.HarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.mainNavigationScenePlan
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PredictiveBackSurfaceRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun adaptiveReflowNeverAttachesTheSameNativeStoryTwice() {
        val navigation = MainNavigationStore().apply { openStory(StoryRoute(1)) }
        var twoPane by mutableStateOf(false)
        val nativeStories = mutableMapOf<Int, android.widget.FrameLayout>()
        val mounts = mutableMapOf<Int, Int>()
        compose.setContent {
            val snapshot by navigation.state.collectAsState()
            val plan = mainNavigationScenePlan(snapshot, twoPane)
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    plan = plan,
                    stories = { detail, paneComments -> if (detail != null) paneComments(detail) },
                    comments = { request, _ ->
                        DisposableEffect(request.serial) {
                            mounts[request.serial] = (mounts[request.serial] ?: 0) + 1
                            onDispose { }
                        }
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { nativeStories.getOrPut(request.serial) { android.widget.FrameLayout(it) } },
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    settings = {},
                    submissions = { _, detail, paneComments -> if (detail != null) paneComments(detail) },
                    editor = {}, immersive = {}, foreground = {},
                )
            }
        }
        compose.waitForIdle()
        for (inSubmissions in listOf(false, true)) {
            if (inSubmissions) {
                compose.runOnIdle {
                    navigation.openSubmissions("reader")
                    navigation.openStory(StoryRoute(2))
                }
                compose.waitForIdle()
            }
            for (wide in listOf(true, false, true)) {
                compose.runOnIdle { twoPane = wide }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertTrue(nativeStories.values.all { it.isAttachedToWindow })
                    assertTrue("Reflow must retain screen-local state, not remount the story", mounts.values.all { it == 1 })
                }
            }
        }
    }

    @Test fun storyUpAnimatesToItsParentWhenAnOlderStoryRemainsUnderneath() {
        val navigation = MainNavigationStore()
        compose.setContent {
            TestRoot(navigation,
                comments = { Fill(if (it.storyId == 1) Color.Yellow else Color.Blue) },
                submissions = { _, _ -> Fill(Color.Magenta) },
            )
        }
        for (fromSettings in listOf(false, true)) {
            compose.runOnIdle {
                navigation.returnToStories()
                navigation.openStory(StoryRoute(1))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                if (fromSettings) navigation.openSettings("debug") else navigation.openSubmissions("reader")
            }
            compose.waitForIdle()
            compose.runOnIdle { navigation.openStory(StoryRoute(2)) }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            try {
                compose.runOnUiThread { navigation.detailRemovedFromBackStack() }
                repeat(40) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
                    val center = pixels[pixels.width / 2, pixels.height / 2]
                    if (frame == 3) {
                        assertTrue("Up must still show the outgoing post during its fade (Settings=$fromSettings): $center",
                            center.red < 0.95f && center.blue > 0.8f)
                    }
                    assertTrue("An older story or Stories leaked during Up frame $frame (Settings=$fromSettings)",
                        center.green < 0.1f)
                }
            } finally {
                compose.mainClock.autoAdvance = true
            }
            compose.waitForIdle()
            assertTrue("Up must finish on its immediate parent",
                countPixels { it.red > 0.9f && it.blue > 0.9f && it.green < 0.1f } > 1_000)
        }
    }

    @Test fun secondSubmissionsOpenNeverExposesTheOlderStoriesLayout() {
        val navigation = MainNavigationStore().apply { openSubmissions("reader") }
        compose.setContent {
            TestRoot(navigation, twoPane = true,
                submissions = { request, _ -> Fill(if (request.serial == 1) Color.Blue else Color.Red) },
            )
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                navigation.openStory(StoryRoute(1))
                navigation.openSubmissions("reader")
            }
            compose.mainClock.advanceTimeBy(64)
            // The UI can render slowly while a new list/detail scene is being created.
            // Wall-clock time passing must not hide a parent whose child is still fading in.
            android.os.SystemClock.sleep(250)
            repeat(32) { frame ->
                val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue("Older Stories layout flashed during the second open at frame $frame: $center",
                    center.green < 0.1f)
                compose.mainClock.advanceTimeByFrame()
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertTrue("The new Submissions visit must finish opening",
            countPixels { it.red > 0.9f && it.green < 0.1f && it.blue < 0.1f } > 1_000)
    }

    @Test fun closingDebugStoryNeverDrawsStoriesBetweenStoryAndSettings() {
        val navigation = MainNavigationStore().apply { openSettings("debug") }
        val twoPane = compose.activity.resources.configuration.smallestScreenWidthDp >= 600
        compose.setContent {
            TestRoot(navigation, twoPane = twoPane)
        }
        repeat(2) {
            compose.runOnIdle { navigation.openStory(StoryRoute(1)) }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            try {
                compose.runOnUiThread { navigation.detailRemovedFromBackStack() }
                // Inspect every frame through the exit AND the scene switch after disposal.
                // Checking only that the outgoing view stays attached misses a later flash.
                repeat(40) { frame ->
                    compose.mainClock.advanceTimeByFrame()
                    val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
                    val center = pixels[pixels.width / 2, pixels.height / 2]
                    assertTrue("Stories flashed over Settings at exit frame $frame", center.green < 0.1f)
                }
            } finally {
                compose.mainClock.autoAdvance = true
            }
            compose.waitForIdle()
            assertTrue("Settings must remain the final visible surface",
                countPixels { it.red > 0.9f && it.blue > 0.9f && it.green < 0.1f } > 1_000)
        }
    }

    @Test fun settingsBackRevealsStoriesDuringGestureAndRetainsItsComposition() {
        val navigation = MainNavigationStore()
        var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
        var completed by mutableStateOf(false)
        var storiesMounts = 0
        compose.setContent {
            TestRoot(navigation, animation = animation, completed = completed,
                stories = {
                    DisposableEffect(Unit) { storiesMounts++; onDispose { } }
                    Fill(Color.Green)
                },
                settings = { Fill(Color.Blue) },
            )
        }
        compose.runOnIdle { navigation.openSettings(null) }
        compose.waitForIdle()
        repeat(2) { attempt ->
            compose.runOnIdle { animation = heldGesture() }
            assertTrue("Stories must be drawn before Settings is popped",
                countPixels { it.green > 0.5f && it.red < 0.1f && it.blue < 0.1f } > 1_000)
            assertTrue("Settings remains visible while the gesture is held",
                countPixels { it.blue > 0.5f && it.red < 0.1f && it.green < 0.1f } > 1_000)
            compose.runOnIdle {
                if (attempt == 1) {
                    completed = true
                    navigation.closeSettings()
                }
                animation = null
            }
            compose.waitForIdle()
        }
        assertEquals(1, storiesMounts)
        assertEquals(0, countPixels { it.blue > 0.5f && it.red < 0.1f && it.green < 0.1f })
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test fun nestedSettingsBackKeepsParentAndDoesNotReplayCompletedExit() {
        val navigation = SettingsNavigationStore(initialSection = SettingsSection.Appearance, twoPane = false)
        var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
        var completed by mutableStateOf(false)
        var appearanceMounts = 0
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        compose.setContent {
            DisposableEffect(scene) { onDispose { scene.close() } }
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
                    .copy(maxHorizontalPartitions = 1)
                HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                    SettingsNavigationShell(
                        navigation = navigation,
                        directive = directive,
                        isFoldable = false,
                        tabletPaneHorizontalPadding = 0.dp,
                        onBackFromSettings = {}, onSectionChanged = {},
                        modifier = Modifier.testTag("viewport"),
                        predictiveBackOverlay = animation?.let {
                            SettingsPredictiveBackOverlay(
                                it.enterModifier, it.exitModifier, SettingsSection.Theme, SettingsSection.Appearance,
                            )
                        },
                        completedPredictiveBack = completed,
                        renderList = { _, _, _, _ -> Box(Modifier.fillMaxSize().background(Color.Green)) },
                        renderDetail = { section, _, _, _ ->
                            if (section == SettingsSection.Appearance) {
                                DisposableEffect(Unit) {
                                    appearanceMounts++
                                    onDispose { }
                                }
                            }
                            Box(Modifier.fillMaxSize().background(
                                if (section == SettingsSection.Appearance) Color.Yellow else Color.Blue,
                            ))
                        },
                    )
                }
            }
        }
        compose.runOnIdle { navigation.navigateTo(SettingsSection.Theme, true) }
        compose.waitForIdle()
        compose.runOnIdle { animation = heldGesture() }
        assertTrue("The retained Appearance page must be visible",
            countPixels { it.red > 0.5f && it.green > 0.5f && it.blue < 0.1f } > 1_000)
        assertEquals("The Settings list cannot leak around Appearance", 0,
            countPixels { it.green > 0.5f && it.red < 0.1f && it.blue < 0.1f })
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                completed = true
                navigation.navigateBack()
            }
            compose.mainClock.advanceTimeBy(32)
            compose.runOnUiThread { animation = null; completed = false }
            compose.mainClock.advanceTimeBy(32)
            assertEquals("The completed Theme surface must not flash again", 0,
                countPixels { it.blue > 0.5f && it.red < 0.1f && it.green < 0.1f })
            compose.mainClock.advanceTimeBy(600)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle { assertEquals(1, appearanceMounts) }
    }

    @Test fun submissionsBackRevealsStoryOpenedFromDebug() {
        val navigation = MainNavigationStore().apply {
            openSettings("debug")
            openStory(StoryRoute(1))
        }
        var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
        var completed by mutableStateOf(false)
        compose.setContent {
            TestRoot(navigation, animation = animation, completed = completed,
                comments = { Fill(Color.Yellow) },
            )
        }
        compose.waitForIdle()
        compose.runOnIdle { navigation.openSubmissions("reader") }
        // Let the old story-parent exit-retention timeout expire before starting Back.
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        repeat(2) { attempt ->
            compose.runOnIdle { animation = heldGesture() }
            compose.waitForIdle()
            assertTrue("Submissions must reveal its story parent",
                countPixels { it.red > 0.5f && it.green > 0.5f && it.blue < 0.1f } > 1_000)
            assertEquals("Debug must not cover the story or leak around its edges", 0,
                countPixels { it.red > 0.5f && it.blue > 0.5f && it.green < 0.1f })
            compose.runOnIdle {
                animation = null
                if (attempt == 1) {
                    completed = true
                    navigation.closeSubmissions()
                }
            }
            compose.waitForIdle()
        }
        assertTrue("Committing Back must restore the story",
            countPixels { it.red > 0.9f && it.green > 0.9f && it.blue < 0.1f } > 1_000)
        compose.runOnIdle { navigation.detailRemovedFromBackStack() }
        compose.waitForIdle()
        assertTrue("A subsequent Back must still restore Debug",
            countPixels { it.red > 0.9f && it.blue > 0.9f && it.green < 0.1f } > 1_000)
    }

    @Test fun gestureMovesSurfaceWithoutChangingBackdropCoordinates() {
        var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
        var entering by mutableStateOf(true)
        var bounds = Rect.Zero
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).testTag("viewport")) {
                val transform = animation?.let {
                    if (entering) it.enterModifier else it.exitModifier
                } ?: Modifier
                Box(Modifier.fillMaxSize().then(transform)) {
                    Box(Modifier.fillMaxSize().background(Color.Blue)
                        .onGloballyPositioned { bounds = it.boundsInWindow() })
                }
            }
        }
        compose.waitForIdle()
        val restingBounds = bounds
        val restingBluePixels = countPixels { it.blue > 0.9f && it.red < 0.1f }
        for (isEntering in listOf(true, false)) {
            compose.runOnIdle { entering = isEntering; animation = heldGesture() }
            compose.waitForIdle()
            assertEquals("Backdrop sampling must stay in the page's coordinate space", restingBounds, bounds)
            val movingBluePixels = countPixels { it.blue > 0.5f && it.red < 0.1f }
            assertTrue("The rendered surface must still shrink", movingBluePixels < restingBluePixels * 0.9f)
            assertTrue("The surface must stay visible", movingBluePixels > restingBluePixels * 0.4f)
            compose.runOnIdle { animation = null }
            assertEquals(restingBluePixels, countPixels { it.blue > 0.9f && it.red < 0.1f })
        }
    }

    @Test fun nestedBackRevealsOnlyItsImmediatePredecessor() {
        var animation by mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
        var depth by mutableStateOf(2)
        val navigation = MainNavigationStore().apply {
            (1..2).forEach { openLinkedStory(StoryRoute(it).toDestination()) }
        }
        compose.setContent {
            TestRoot(navigation, animation = animation,
                comments = { request ->
                    Fill(when (request.serial) {
                        depth -> Color.Blue
                        depth - 1 -> Color.Yellow
                        else -> Color.Magenta
                    })
                },
            )
        }
        for (storyDepth in listOf(2, 3)) {
            compose.runOnIdle {
                depth = storyDepth
                if (storyDepth == 3) navigation.openLinkedStory(StoryRoute(3).toDestination())
            }
            compose.waitForIdle()
            compose.runOnIdle { animation = heldGesture() }
            compose.waitForIdle()
            assertEquals("The story list must not show through", 0,
                countPixels { it.green > 0.5f && it.red < 0.1f && it.blue < 0.1f })
            assertEquals("Older stories must not show through", 0,
                countPixels { it.red > 0.5f && it.blue > 0.5f && it.green < 0.1f })
            assertTrue("The immediate predecessor must be visible",
                countPixels { it.red > 0.5f && it.green > 0.5f && it.blue < 0.1f } > 1_000)
            compose.runOnIdle { animation = null }
            compose.waitForIdle()
        }
    }

    @Composable
    private fun Fill(color: Color) { Box(Modifier.fillMaxSize().background(color)) }

    @Composable
    private fun TestRoot(
        navigation: MainNavigationStore,
        twoPane: Boolean = false,
        animation: DefaultActivityPredictiveBackAnimation? = null,
        completed: Boolean = false,
        stories: @Composable () -> Unit = { Fill(Color.Green) },
        comments: @Composable (com.simon.harmonichackernews.navigation.MainStoryRequest) -> Unit = { Fill(Color.Blue) },
        settings: @Composable () -> Unit = { Fill(Color.Magenta) },
        submissions: @Composable (com.simon.harmonichackernews.navigation.MainSubmissionsRequest,
            com.simon.harmonichackernews.navigation.MainStoryRequest?) -> Unit = { _, _ -> Fill(Color.Blue) },
    ) {
        val snapshot by navigation.state.collectAsState()
        val plan = mainNavigationScenePlan(snapshot, twoPane)
        val preview = rememberMainNavigationBackPreview(plan, animation,
            animation?.enterModifier ?: Modifier, animation?.exitModifier ?: Modifier)
        var lastSource by remember { mutableStateOf<MainNavigationSurfaceKey?>(null) }
        SideEffect { if (preview != null) lastSource = preview.source }
        val palette = HarmonicThemeCatalog.resolve("light", false)
        HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
            HarmonicAppRoot(
                plan = plan, preview = preview,
                completedPredictiveBack = if (completed) setOfNotNull(lastSource) else emptySet(),
                modifier = Modifier.background(Color.White).testTag("viewport"),
                stories = { _, _ -> stories() }, comments = { request, _ -> comments(request) }, settings = { settings() },
                submissions = { request, detail, _ -> submissions(request, detail) }, editor = {}, immersive = {}, foreground = {},
            )
        }
    }

    private fun heldGesture() = DefaultActivityPredictiveBackAnimation(
        BackEventCompat(550f, 1200f, 0.8f, BackEventCompat.EDGE_LEFT),
    )

    private fun countPixels(predicate: (Color) -> Boolean): Int {
        val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            if (predicate(pixels[x, y])) count++
        }
        return count
    }
}
