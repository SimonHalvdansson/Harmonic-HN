@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.stories.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransparentPreviewRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun transparentDestinationDoesNotRevealTheOldCropAtHandoff() {
        val source = ImageBitmap(100, 100).also {
            Canvas(it).drawRect(Rect(0f, 0f, 50f, 100f), Paint().apply { color = Color.Red })
        }
        val target = ImageBitmap(100, 100).also {
            Canvas(it).drawRect(Rect(50f, 0f, 100f, 100f), Paint().apply { color = Color.Blue })
        }
        val progress = mutableFloatStateOf(0f)
        val bounds = Rect(0f, 0f, 200f, 200f)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(220.dp)) {
                    StoryPreviewTransitionOverlay(
                        StoryPreviewSharedTransitionState(
                            progress = progress.floatValue, active = true, hideTargetContent = true,
                            drawOverlayShadows = false,
                            source = StoryPreviewSourceGeometry(container = bounds, image = bounds),
                            sourceSnapshot = { if (it == StoryPreviewSourceElement.Image) source else null },
                            targetContainer = bounds, targetScale = 1f, targetCommentsButton = null,
                            rootOffset = Offset.Zero,
                            targetBounds = { if (it == StoryPreviewSharedElement.Image) bounds else null },
                            targetSnapshot = { if (it == StoryPreviewSharedElement.Image) target else null },
                            updateTargetBounds = { _, _ -> }, updateTargetLayer = { _, _ -> },
                            updateCommentsButtonBounds = {},
                        ), Color.White,
                    )
                }
            }
        }
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            compose.runOnIdle { progress.floatValue = fraction }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val left = pixels[50, 100]
            val right = pixels[150, 100]
            assertEquals("Old crop must fade through transparent target pixels", fraction, left.green, 0.03f)
            assertEquals(1f, left.red, 0.03f)
            assertEquals(1f - fraction, right.red, 0.03f)
            assertEquals(1f, right.blue, 0.03f)
        }
    }
}
