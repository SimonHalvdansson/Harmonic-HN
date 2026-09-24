package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.common.TransformOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransformOverlayCoordinatesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun retainedPreviewKeepsContainerAlignedDuringParentBackTransform() {
        val scale = mutableFloatStateOf(1f)
        val translation = mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White).testTag("viewport")) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale.floatValue
                    scaleY = scale.floatValue
                    translationX = size.width * translation.floatValue
                    clip = true
                }) {
                    TransformOverlay(
                        contentKey = Unit, sourceBounds = null, dismissRequestVersion = 0,
                        predictiveBackProgress = 0f, predictiveBackEdge = 0,
                        maxWidth = 240.dp, horizontalPadding = 24.dp, verticalPadding = 24.dp,
                        targetCornerRadius = 0.dp, containerColor = Color.Magenta,
                        shadowElevation = 0.dp,
                        onDismissRequest = {}, onDismissAnimationFinished = {},
                    ) {
                        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Cyan))
                    }
                }
            }
        }
        fun assertContainerCovered() {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("viewport").captureToImage().toPixelMap()
            var exposedContainer = 0
            var visibleContent = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.9f && color.blue > 0.9f && color.green < 0.1f) exposedContainer++
                if (color.red < 0.1f && color.blue > 0.9f && color.green > 0.9f) visibleContent++
            }
            assertTrue("Preview content must remain visible", visibleContent > 1_000)
            assertTrue(
                "Parent transforms must not separate the container from its content ($exposedContainer pixels)",
                exposedContainer < 100,
            )
        }
        assertContainerCovered()
        // Match the retained destination's scale and partially offscreen position during Back.
        for (offset in listOf(-0.2f, 0.2f, 0f)) {
            compose.runOnIdle { scale.floatValue = 0.85f; translation.floatValue = offset }
            assertContainerCovered()
        }
        compose.runOnIdle { scale.floatValue = 1f; translation.floatValue = 0f }
        assertContainerCovered()
    }
}
