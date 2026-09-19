package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButtonColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterButtonMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectionColorsFadeAndRapidChangesSettleOnCurrentSelection() {
        val selected = mutableStateOf(false)
        val checkedBackground = Color(0xff336699)
        compose.setContent {
            Box(Modifier.background(Color.White)) {
                HarmonicFilterButton(
                    label = "Posts",
                    selected = selected.value,
                    position = 0,
                    lastPosition = 0,
                    colors = HarmonicFilterButtonColors(
                        checkedBackground, Color.White, checkedBackground, Color.Black, Color.Gray,
                    ),
                    onClick = { selected.value = !selected.value },
                    modifier = Modifier.width(180.dp).testTag("filter"),
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
        assertEquals(Color.White.red, sampledBackground().red, 0.02f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { selected.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("filter").assertIsSelected()
        val intermediate = sampledBackground()
        assertTrue("Selection fades from white to the selected color", intermediate.red in 0.23f..0.98f)

        compose.runOnUiThread { selected.value = false }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnUiThread { selected.value = true }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("filter").assertIsSelected()
        val settled = sampledBackground()
        assertEquals(checkedBackground.red, settled.red, 0.02f)
        assertEquals(checkedBackground.green, settled.green, 0.02f)
        assertEquals(checkedBackground.blue, settled.blue, 0.02f)
    }

    private fun sampledBackground(): Color {
        val pixels = compose.onNodeWithTag("filter").captureToImage().toPixelMap()
        return pixels[pixels.width / 8, pixels.height / 2]
    }
}

@RunWith(AndroidJUnit4::class)
class FilterButtonDisabledMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>(
        effectContext = object : MotionDurationScale {
            override val scaleFactor = 0f
        },
    )

    @Test
    fun disabledMotionSnapsAtFirstAnimationFrameAndKeepsSelectionClickable() {
        val selected = mutableStateOf(false)
        val checkedBackground = Color(0xff336699)
        var clicks = 0
        compose.setContent {
            Box(Modifier.background(Color.White)) {
                HarmonicFilterButton(
                    label = "Posts",
                    selected = selected.value,
                    position = 0,
                    lastPosition = 0,
                    colors = HarmonicFilterButtonColors(
                        checkedBackground, Color.White, checkedBackground, Color.Black, Color.Gray,
                    ),
                    onClick = {
                        clicks++
                        selected.value = !selected.value
                    },
                    modifier = Modifier.width(180.dp).testTag("filter"),
                    fontFamily = FontFamily.SansSerif,
                )
            }
        }
        compose.onNodeWithTag("filter").assertIsNotSelected()
        assertBackground(Color.White)
        compose.mainClock.autoAdvance = false

        compose.onNodeWithTag("filter").performClick()
        // One frame recomposes the selection; the next delivers the animation's first frame.
        // At a zero duration scale this must already be the final color, not an intermediate.
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("filter").assertIsSelected()
        assertBackground(checkedBackground)

        compose.onNodeWithTag("filter").performClick()
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.onNodeWithTag("filter").assertIsNotSelected()
        assertBackground(Color.White)
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    private fun assertBackground(expected: Color) {
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("filter").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 8, pixels.height / 2]
        assertEquals(expected.red, actual.red, 0.02f)
        assertEquals(expected.green, actual.green, 0.02f)
        assertEquals(expected.blue, actual.blue, 0.02f)
    }
}
