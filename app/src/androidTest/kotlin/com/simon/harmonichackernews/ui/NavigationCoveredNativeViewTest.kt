@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.navigation.ActivityBackPreview
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationStack
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Draw suppression must retain actual Android children, including their local scroll position. */
@RunWith(AndroidJUnit4::class)
class NavigationCoveredNativeViewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun coveredNativeParentKeepsScrollAndReappearsOnCanceledAndCompletedBack() {
        var entries by mutableStateOf(emptyList<Int>())
        var preview by mutableStateOf<ActivityBackPreview<Int>?>(null)
        var completed by mutableStateOf(false)
        val native = mutableMapOf<Int, ScrollView>()
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.fillMaxSize().testTag("stack")) {
                    ActivityNavigationStack(
                        entries, { it }, hideCoveredRoot = true,
                        preview = preview, completedPredictiveBack = completed,
                        root = { Box(Modifier.fillMaxSize().background(Color.Red)) },
                        content = { id ->
                            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                                ScrollView(context).apply {
                                    val color = if (id == 1) android.graphics.Color.GREEN else android.graphics.Color.BLUE
                                    setBackgroundColor(color)
                                    addView(View(context).apply {
                                        // ScrollView measures its child with an unspecified height;
                                        // a bare View needs an intrinsic minimum to be scrollable.
                                        minimumHeight = 6_000
                                        setBackgroundColor(color)
                                    },
                                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 6_000))
                                    native[id] = this
                                }
                            })
                        },
                    )
                }
            }
        }
        compose.runOnIdle { entries = listOf(1) }
        compose.waitForIdle()
        val parent = compose.runOnIdle { native.getValue(1) }
        // Compose can be idle before the native ScrollView's first Android layout. Establish
        // a real scroll range and a completed native frame before testing retained position.
        compose.waitUntil(5_000) {
            compose.runOnUiThread {
                parent.height > 0 && parent.getChildAt(0).height - parent.height >= 200 &&
                    !parent.isLayoutRequested
            }
        }
        compose.runOnIdle {
            assertTrue("Native fixture must have at least 200px of scroll range",
                parent.getChildAt(0).height - parent.height >= 200)
            parent.scrollTo(0, 200)
        }
        compose.waitForIdle()
        assertPixel(Color.Green)
        val initialNativeState = compose.runOnIdle {
            "viewport=${parent.width}x${parent.height}, child=${parent.getChildAt(0).height}, " +
                "scrollY=${parent.scrollY}, focused=${parent.hasFocus()}"
        }
        compose.runOnIdle {
            assertEquals("Initial native scroll must survive a frame before cover: $initialNativeState", 200, parent.scrollY)
        }
        compose.runOnIdle { entries = listOf(1, 2) }
        compose.waitForIdle()
        assertPixel(Color.Blue)
        compose.runOnIdle {
            assertTrue(parent.isAttachedToWindow)
            assertEquals("Covered native state: before=[$initialNativeState], " +
                "viewport=${parent.width}x${parent.height}, child=${parent.getChildAt(0).height}, " +
                "focused=${parent.hasFocus()}", 200, parent.scrollY)
            preview = ActivityBackPreview(2, 1, Modifier, Modifier.padding(start = 80.dp))
        }
        compose.waitForIdle()
        assertPixel(Color.Green, leftEdge = true)
        compose.runOnIdle { preview = null }
        compose.waitForIdle()
        assertPixel(Color.Blue)
        compose.runOnIdle {
            preview = ActivityBackPreview(2, 1, Modifier, Modifier.padding(start = 80.dp))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            entries = listOf(1)
            completed = true
        }
        compose.waitForIdle()
        compose.runOnIdle { preview = null; completed = false }
        compose.waitForIdle()
        assertPixel(Color.Green)
        compose.runOnIdle {
            assertSame(parent, native.getValue(1))
            assertTrue(parent.isAttachedToWindow)
            assertEquals(200, parent.scrollY)
        }
    }

    private fun assertPixel(color: Color, leftEdge: Boolean = false) {
        val pixels = compose.onNodeWithTag("stack").captureToImage().toPixelMap()
        val pixel = pixels[if (leftEdge) 10 else pixels.width / 2, pixels.height / 2]
        assertEquals(color.red, pixel.red, 0.01f)
        assertEquals(color.green, pixel.green, 0.01f)
        assertEquals(color.blue, pixel.blue, 0.01f)
    }
}
