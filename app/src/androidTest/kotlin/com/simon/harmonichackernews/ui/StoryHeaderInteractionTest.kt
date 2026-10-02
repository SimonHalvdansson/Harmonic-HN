@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.comments.StoryHeaderClickArea
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class StoryHeaderInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pdfBeforeYearRendersAsBadgeWithoutRepeatingMarker() {
        val story = com.simon.harmonichackernews.data.Story().apply {
            title = "A paper [pdf] (2025)"
            url = "https://example.com/paper.pdf"
        }
        com.simon.harmonichackernews.network.StoryTextProcessor.applyTitleBadges(story)
        val title = com.simon.harmonichackernews.ui.content.storyTitlePresentation(
            story.title, story.pdfTitle, story.videoTitle,
        )
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                com.simon.harmonichackernews.ui.content.StoryTitleText(title.text, title.badge)
            }
        }
        compose.onNodeWithText("A paper (2025)", substring = true).assertExists()
        compose.onNodeWithText("[pdf]", substring = true).assertDoesNotExist()
        assertEquals(com.simon.harmonichackernews.ui.content.StoryTitleBadge.PDF, title.badge)
    }

    @Test
    fun rippleExtendsBeyondHeaderWithoutMovingContent() {
        var clicks = 0
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Column(Modifier.fillMaxWidth().background(Color.White).testTag("root").padding(vertical = 24.dp)) {
                    StoryHeaderClickArea(true, "Fixture", { clicks++ }) {
                        Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Blue).testTag("image"))
                        Text("Fixture title", Modifier.testTag("title"))
                        Text("example.com", Modifier.testTag("domain"))
                    }
                    Text("123 points", Modifier.padding(top = 6.dp).testTag("meta"))
                }
            }
        }
        compose.waitForIdle()
        val root = compose.onNodeWithTag("root")
        val header = compose.onNodeWithContentDescription("Open article: Fixture")
        val initialBounds = listOf("image", "title", "domain", "meta").map {
            compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val resting = root.captureToImage().toPixelMap()
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val headerBounds = header.fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        compose.mainClock.autoAdvance = false
        header.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500)
        SystemClock.sleep(500)
        try {
            val pressed = root.captureToImage().toPixelMap()
            fun deltaAt(y: Float): Float {
                val x = pressed.width / 2
                val row = (y - rootBounds.top).roundToInt()
                val a = resting[x, row]
                val b = pressed[x, row]
                return abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
            }
            assertTrue("Ripple includes the new upper gap", deltaAt(headerBounds.top - 6 * density) > 0.02f)
            assertTrue("Ripple stops after 12dp above header", deltaAt(headerBounds.top - 14 * density) < 0.01f)
            assertTrue("Ripple includes half the metadata gap", deltaAt(headerBounds.bottom + density) > 0.02f)
            assertTrue("Ripple stops before metadata", deltaAt(headerBounds.bottom + 5 * density) < 0.01f)
            assertEquals(initialBounds, listOf("image", "title", "domain", "meta").map {
                compose.onNodeWithTag(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            })
        } finally {
            header.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        compose.runOnIdle { assertEquals(1, clicks) }
    }
}
