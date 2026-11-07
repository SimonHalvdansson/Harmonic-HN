@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.navigation.commentsSwipeBack
import com.simon.harmonichackernews.ui.navigation.rememberCommentsSwipeBackState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsSwipeBackGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var enabled by mutableStateOf(true)
    private var visit by mutableStateOf(1)
    private var pops = 0

    @Test fun finalMovementDeliveredWithPointerUpCountsTowardCompletion() {
        content()
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.1f, height * 0.7f))
            moveTo(Offset(width * 0.6f, height * 0.7f), 600)
            advanceEventTime(300)
            updatePointerTo(0, Offset(width * 0.95f, height * 0.7f))
            up()
        }
        compose.waitForIdle()
        assertEquals(1, pops)
    }

    @Test fun disablingOrNavigatingDuringDragCancelsTheOldVisit() {
        content()
        for (navigate in listOf(false, true)) {
            compose.runOnIdle { enabled = true }
            compose.onRoot().performTouchInput {
                down(Offset(width * 0.1f, height * 0.7f))
                moveTo(Offset(width * 0.8f, height * 0.7f), 300)
            }
            compose.runOnIdle { if (navigate) visit++ else enabled = false }
            compose.onRoot().performTouchInput { up() }
            compose.waitForIdle()
            assertEquals(0, pops)
        }
    }

    @Test fun navigatingWhileAReleasedSwipeSettlesDoesNotPopTheNewVisit() {
        content()
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.7f), Offset(width * 0.95f, height * 0.7f), 300)
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnUiThread { visit++ }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(0, pops)
    }

    @Test fun childHorizontalScrollingAndMultitouchKeepThePageOpen() {
        content()
        compose.onNodeWithTag("horizontal").performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.5f), Offset(width * 0.95f, height * 0.5f), 300)
        }
        compose.waitForIdle()
        assertEquals(0, pops)
        compose.onRoot().performTouchInput {
            down(0, Offset(width * 0.1f, height * 0.7f))
            down(1, Offset(width * 0.2f, height * 0.7f))
            moveTo(0, Offset(width * 0.95f, height * 0.7f), 300)
            up(1)
            up(0)
        }
        compose.waitForIdle()
        assertEquals(0, pops)
        // Ordinary rightward movement outside the child still navigates exactly once.
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.1f, height * 0.7f), Offset(width * 0.95f, height * 0.7f), 300)
        }
        compose.waitForIdle()
        assertEquals(1, pops)
    }

    private fun content() {
        compose.setContent {
            val state = rememberCommentsSwipeBackState(visit, enabled)
            Box(Modifier.fillMaxSize().background(Color.White)
                .commentsSwipeBack(state, enabled, { true }) { pops++ }) {
                Row(Modifier.fillMaxWidth().height(150.dp).testTag("horizontal")
                    .horizontalScroll(rememberScrollState(500))) {
                    Text("A horizontally scrolling code block", Modifier.width(2_000.dp))
                }
            }
        }
        compose.waitForIdle()
    }
}
