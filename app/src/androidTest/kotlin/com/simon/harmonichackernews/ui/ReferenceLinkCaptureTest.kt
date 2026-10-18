@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.content.ReferenceLinkRow
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceLinkCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun firstLongPressWaitsForRecordedContentAndRepeatedPressesStillOpen() {
        var clicks = 0
        val captures = mutableListOf<Pair<Rect, GraphicsLayer?>>()
        compose.setContent {
            MaterialTheme {
                ReferenceLinkRow(
                    marker = "[1]",
                    label = "Reference article",
                    typography = rememberContentTypography("devicedefault"),
                    onClick = { clicks++ },
                    onLongClick = { bounds, layer -> captures += bounds to layer },
                )
            }
        }
        val row = compose.onNodeWithText("Reference article")
        row.performClick()
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertTrue(captures.isEmpty())
        }
        repeat(2) { index ->
            row.performTouchInput { longClick() }
            compose.waitUntil(5_000) { captures.size == index + 1 }
            compose.runOnIdle {
                val (bounds, nullableLayer) = captures.last()
                val layer = checkNotNull(nullableLayer)
                assertTrue(bounds.width > 0f && bounds.height > 0f)
                assertFalse(layer.isReleased)
                assertTrue(layer.size.width > 0 && layer.size.height > 0)
                assertEquals(1, clicks)
            }
        }
    }
}
