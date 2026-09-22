@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.TransformOverlay
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceOutlineRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun materialCrossfadePreservesSourceOutlineAtClosingHandoff() {
        val rootBounds = mutableStateOf(Rect.Zero)
        val dismiss = mutableIntStateOf(0)
        val mode = mutableStateOf(SurfaceEffectMode.Glass)
        var finished = false
        val border = Color(0.2f, 0.2f, 0.2f)
        compose.setContent {
            CompositionLocalProvider(LocalHazePreferences provides SurfaceEffectPreferences(mode = mode.value)) {
                HazeHost {
                    Box(Modifier.fillMaxSize().testTag("outline-fixture")
                        .onGloballyPositioned { rootBounds.value = it.boundsInWindow() }) {
                        Box(Modifier.fillMaxSize().sharedHazeSource(currentSharedHazeState()).background(Color.White))
                        val origin = rootBounds.value.topLeft
                        TransformOverlay(
                            contentKey = mode.value,
                            sourceBounds = Rect(origin.x + 80f, origin.y + 120f, origin.x + 380f, origin.y + 220f),
                            dismissRequestVersion = dismiss.intValue,
                            predictiveBackProgress = 0f, predictiveBackEdge = 0,
                            maxWidth = 300.dp, horizontalPadding = 16.dp, verticalPadding = 16.dp,
                            targetCornerRadius = 28.dp, sourceCornerRadius = 6.dp,
                            containerColor = Color.White, sourceContainerColor = Color.White,
                            sourceBorderColor = border, sourceBorderWidth = 2.dp,
                            glassBackground = true,
                            onDismissRequest = {}, onDismissAnimationFinished = { finished = true },
                        ) { Box(Modifier.height(120.dp)) }
                    }
                }
            }
        }
        for (material in listOf(SurfaceEffectMode.Glass, SurfaceEffectMode.Frosted)) {
            compose.runOnIdle { mode.value = material; dismiss.intValue = 0; finished = false }
            compose.waitForIdle()
            compose.runOnIdle { dismiss.intValue++ }
            compose.waitForIdle()
            assertTrue("Closing animation must complete", finished)
            // Keep the last overlay frame alive, just before the source row takes over. Its
            // outline must already have the source color, including with translucent materials.
            val pixel = compose.onNodeWithTag("outline-fixture").captureToImage().toPixelMap()[230, 122]
            assertEquals("$material border must not be brightened by the material blend", border.red, pixel.red, 0.04f)
            assertEquals(border.green, pixel.green, 0.04f)
            assertEquals(border.blue, pixel.blue, 0.04f)
        }
    }
}
