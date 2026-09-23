@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.GlassPreferences
import com.simon.harmonichackernews.settings.GlassSwitch
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlassBlurRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun adaptiveGlassDoesNotMixSharpTextBackIntoCompactSurfaces() {
        compose.setContent {
            Column {
                val surfaces = listOf(
                    HazeGlassAppearance.Subtle to 48.dp,
                    HazeGlassAppearance.FloatingButton to 80.dp,
                    HazeGlassAppearance.Dialog to 220.dp,
                )
                surfaces.forEachIndexed { index, (appearance, height) ->
                    CompositionLocalProvider(LocalHazePreferences provides SurfaceEffectPreferences(
                        mode = SurfaceEffectMode.Glass,
                        glass = GlassPreferences(switches = mapOf(GlassSwitch.TintEnabled to false)),
                    )) {
                        HazeHost {
                            val haze = currentSharedHazeState()
                            Box(Modifier.size(240.dp, height).testTag("glass-$index"), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(240.dp, height).sharedHazeSource(haze)) {
                                    drawRect(Color.Black)
                                    drawRect(Color.White, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
                                }
                                val shape = RoundedCornerShape(16.dp)
                                val base = Modifier.size(240.dp, height)
                                Box(if (appearance == HazeGlassAppearance.Dialog) {
                                    base.sharedHazeDialogBackground(Color.White, shape)
                                } else {
                                    base.sharedHazeBackground(haze, Color.White, shape, glassAppearance = appearance)
                                })
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        repeat(3) { index ->
            val pixels = compose.onNodeWithTag("glass-$index").captureToImage().toPixelMap()
            val center = pixels.width / 2
            val y = pixels.height / 2
            val edgeJump = pixels[center + 1, y].red - pixels[center - 1, y].red
            val spread = pixels[center - 10, y].red - pixels[pixels.width / 4, y].red
            assertTrue("Glass $index must blur the backdrop, spread=$spread", spread > 0.05f)
            assertTrue("Glass $index must not retain a sharp edge, jump=$edgeJump", edgeJump < 0.06f)
        }
    }
}
