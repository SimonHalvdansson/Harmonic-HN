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
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.navigation.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.navigation.HarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.mainNavigationScenePlan
import com.simon.harmonichackernews.ui.navigation.rememberMainNavigationPresentation
import com.simon.harmonichackernews.ui.navigation.SinglePaneNavigationScene
import com.simon.harmonichackernews.ui.navigation.SubmissionsNavigationStack
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

    @Test fun storyUpAnimatesToItsParentWhenAnOlderStoryRemainsUnderneath() {
        val navigation = MainNavigationStore()
        compose.setContent {
            val snapshot by navigation.state.collectAsState()
            val presentation = rememberMainNavigationPresentation(snapshot, isTwoPane = false, false)
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    navigation = snapshot,
                    transitionOffsetPx = 96,
                    completedSettingsPredictiveBack = false,
                    completedSubmissionsPredictiveBack = false,
                    completedEditorPredictiveBack = false,
                    storyExitInProgress = presentation.storyExitInProgress,
                    modifier = Modifier.testTag("viewport"),
                    base = {
                        SinglePaneNavigationScene(
                            scene = presentation.scene,
                            completedPredictivePop = false,
                            predictiveBackActive = false,
                            onStoryLayersEmpty = presentation.onStoryLayersEmpty,
                            stories = { Box(Modifier.fillMaxSize().background(Color.Green)) },
                            comments = { request ->
                                Box(Modifier.fillMaxSize().background(
                                    if (request.storyId == 1) Color.Yellow else Color.Blue,
                                ))
                            },
                        )
                    },
                    settings = { Box(Modifier.fillMaxSize().background(Color.Magenta)) },
                    submissions = { Box(Modifier.fillMaxSize().background(Color.Magenta)) },
                    editor = {}, immersive = {}, foreground = {},
                )
            }
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
            val snapshot by navigation.state.collectAsState()
            val plan = mainNavigationScenePlan(snapshot, isTwoPane = true)
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    navigation = snapshot,
                    transitionOffsetPx = 96,
                    completedSettingsPredictiveBack = false,
                    completedSubmissionsPredictiveBack = false,
                    completedEditorPredictiveBack = false,
                    submissionsInTwoPane = true,
                    modifier = Modifier.testTag("viewport"),
                    base = { Box(Modifier.fillMaxSize().background(Color.Green)) },
                    submissions = {
                        SubmissionsNavigationStack(
                            scenes = plan.submissionsScenes,
                            predictiveBackActive = false,
                            completedPredictiveBack = false,
                            enterModifier = Modifier,
                            exitModifier = Modifier,
                        ) { scene ->
                            Box(Modifier.fillMaxSize().background(
                                if (scene.request.serial == 1) Color.Blue else Color.Red,
                            ))
                        }
                    },
                    settings = {}, editor = {}, immersive = {}, foreground = {},
                )
            }
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
            val snapshot by navigation.state.collectAsState()
            val presentation = rememberMainNavigationPresentation(snapshot, twoPane, false)
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    navigation = snapshot,
                    transitionOffsetPx = 96,
                    completedSettingsPredictiveBack = false,
                    completedSubmissionsPredictiveBack = false,
                    completedEditorPredictiveBack = false,
                    storyExitInProgress = presentation.storyExitInProgress,
                    modifier = Modifier.testTag("viewport"),
                    base = {
                        val stories: @Composable () -> Unit = {
                            Box(Modifier.fillMaxSize().background(Color.Green))
                        }
                        if (presentation.renderTwoPaneStoryScene) {
                            stories()
                        } else {
                            SinglePaneNavigationScene(
                                scene = presentation.scene,
                                completedPredictivePop = false,
                                predictiveBackActive = false,
                                onStoryLayersEmpty = presentation.onStoryLayersEmpty,
                                stories = stories,
                                comments = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                            )
                        }
                    },
                    settings = { Box(Modifier.fillMaxSize().background(Color.Magenta)) },
                    submissions = {}, editor = {}, immersive = {}, foreground = {},
                )
            }
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
            val snapshot by navigation.state.collectAsState()
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    navigation = snapshot,
                    transitionOffsetPx = 96,
                    completedSettingsPredictiveBack = completed,
                    completedSubmissionsPredictiveBack = false,
                    completedEditorPredictiveBack = false,
                    modifier = Modifier.background(Color.White).testTag("viewport"),
                    basePredictiveModifier = animation?.enterModifier ?: Modifier,
                    settingsPredictiveModifier = animation?.exitModifier ?: Modifier,
                    base = {
                        SinglePaneNavigationScene(
                            scene = mainNavigationScenePlan(snapshot, isTwoPane = false),
                            completedPredictivePop = false,
                            predictiveBackActive = false,
                            stories = {
                                DisposableEffect(Unit) {
                                    storiesMounts++
                                    onDispose { }
                                }
                                Box(Modifier.fillMaxSize().background(Color.Green))
                            },
                            comments = {},
                        )
                    },
                    settings = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                    submissions = {}, editor = {}, immersive = {}, foreground = {},
                )
            }
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
            val snapshot by navigation.state.collectAsState()
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                HarmonicAppRoot(
                    navigation = snapshot,
                    transitionOffsetPx = 96,
                    completedSettingsPredictiveBack = false,
                    completedSubmissionsPredictiveBack = completed,
                    completedEditorPredictiveBack = false,
                    modifier = Modifier.background(Color.White).testTag("viewport"),
                    basePredictiveModifier = animation?.enterModifier ?: Modifier,
                    submissionsPredictiveModifier = animation?.exitModifier ?: Modifier,
                    base = {
                        if (snapshot.storyRequest != null) {
                            Box(Modifier.fillMaxSize().background(Color.Yellow))
                        }
                    },
                    settings = { Box(Modifier.fillMaxSize().background(Color.Magenta)) },
                    submissions = { Box(Modifier.fillMaxSize().background(Color.Blue)) },
                    editor = {}, immersive = {}, foreground = {},
                )
            }
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
            val snapshot by navigation.state.collectAsState()
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    SinglePaneNavigationScene(
                        scene = mainNavigationScenePlan(snapshot, isTwoPane = false),
                        completedPredictivePop = false,
                        predictiveBackActive = animation != null,
                        storiesPredictiveModifier = animation?.enterModifier ?: Modifier,
                        commentsPredictiveModifier = animation?.exitModifier ?: Modifier,
                        stories = { Box(Modifier.fillMaxSize().background(Color.Green)) },
                        comments = { request ->
                            val color = when (request.serial) {
                                depth -> Color.Blue
                                depth - 1 -> Color.Yellow
                                else -> Color.Magenta
                            }
                            Box(Modifier.fillMaxSize().background(color))
                        },
                    )
                }
            }
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
