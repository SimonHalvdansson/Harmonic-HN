package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.navigation.StatusBarProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusBarProtectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun movingModalStaysUnderGradientButAbovePageDim() {
        val cardTop = mutableFloatStateOf(-20f)
        val dim = mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.Green).testTag("scene")) {
                Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.floatValue)))
                // The same modal and floating-control layers used by both navigation panes.
                Spacer(Modifier.offset(x = 32.dp, y = cardTop.floatValue.dp)
                    .size(100.dp, 120.dp).zIndex(100f).background(Color.Red))
                StatusBarProtection(Color.White, 40.dp, dim.floatValue)
                Spacer(Modifier.size(12.dp).zIndex(101f).background(Color.Cyan))
            }
        }
        val density = compose.activity.resources.displayMetrics.density
        for (top in listOf(-20f, 0f, 24f, 0f, -20f)) {
            compose.runOnIdle { cardTop.floatValue = top; dim.floatValue = 0f }
            val baseline = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val x = (64 * density).toInt()
            val y = ((top.coerceAtLeast(0f) + 16) * density).toInt()
            val gradientCoverage = baseline[x, y].green
            assertTrue("The gradient must cover the moving card at y=$top", gradientCoverage > 0.2f)
            for (alpha in listOf(0.16f, 0.32f, 0.16f, 0f)) {
                compose.runOnIdle { dim.floatValue = alpha }
                val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
                // Only the foreground gradient dims; the dialog beneath stays red.
                assertColor(Color(
                    red = 1f - alpha * gradientCoverage,
                    green = (1f - alpha) * gradientCoverage,
                    blue = (1f - alpha) * gradientCoverage,
                ), pixels[x, y])
                assertColor(Color.Red, pixels[x, (80 * density).toInt()])
                assertColor(Color.Cyan, pixels[(4 * density).toInt(), (4 * density).toInt()])
            }
        }
    }

    @Test
    fun gradientAndPageDarkenExactlyOnceThroughoutFade() {
        val dim = mutableFloatStateOf(0f)
        compose.setContent {
            Box(Modifier.size(200.dp).background(Color.Green).testTag("scene")) {
                Spacer(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.floatValue)))
                StatusBarProtection(Color.White, 40.dp, dim.floatValue)
            }
        }
        val baseline = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
        val density = compose.activity.resources.displayMetrics.density
        for (alpha in listOf(0.16f, 0.32f, 0.16f, 0f)) {
            compose.runOnIdle { dim.floatValue = alpha }
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            for (yDp in listOf(4, 24, 48, 70)) {
                val x = (100 * density).toInt()
                val y = (yDp * density).toInt()
                val original = baseline[x, y]
                assertColor(Color(
                    original.red * (1f - alpha),
                    original.green * (1f - alpha),
                    original.blue * (1f - alpha),
                ), pixels[x, y])
            }
        }
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals("red", expected.red, actual.red, 0.015f)
        assertEquals("green", expected.green, actual.green, 0.015f)
        assertEquals("blue", expected.blue, actual.blue, 0.015f)
    }
}
