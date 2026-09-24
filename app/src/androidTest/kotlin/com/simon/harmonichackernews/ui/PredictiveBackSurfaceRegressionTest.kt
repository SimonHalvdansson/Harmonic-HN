package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.simon.harmonichackernews.navigation.MainStoryRequest
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.navigation.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.navigation.HarmonicAppRoot
import com.simon.harmonichackernews.ui.navigation.SinglePaneNavigationScene
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
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    SinglePaneNavigationScene(
                        storyRequests = (1..depth).map { MainStoryRequest(it, StoryRoute(it).toDestination()) },
                        completedPredictivePop = false,
                        predictiveBackActive = animation != null,
                        showStoriesRoot = true,
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
            compose.runOnIdle { depth = storyDepth }
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
