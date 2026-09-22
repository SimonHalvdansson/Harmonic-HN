package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBackRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun secondGestureDuringSettingsPopIsNotLost() {
        compose.runOnIdle {
            compose.activity.navigationController.navigationState.returnToStories()
            compose.activity.navigationController.openSettings("debug")
        }
        compose.waitForIdle()
        doubleBack()
        compose.runOnIdle {
            assertEquals(MainDestination.STORIES, compose.activity.navigationController.navigationState.state.value.currentDestination)
        }
    }

    @Test fun secondGestureFromSubmissionsPopsTheUnderlyingDebugPage() {
        compose.runOnIdle {
            compose.activity.navigationController.navigationState.returnToStories()
            compose.activity.navigationController.openSettings("debug")
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.navigationController.navigationState.openSubmissions("pg") }
        compose.waitForIdle()
        doubleBack()
        compose.runOnIdle {
            assertEquals(MainDestination.SETTINGS, compose.activity.navigationController.navigationState.state.value.currentDestination)
        }
        // Debug was popped by the second gesture; one more back must leave the settings list.
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(MainDestination.STORIES, compose.activity.navigationController.navigationState.state.value.currentDestination)
        }
    }

    @Test fun secondGesturePopsBothNestedStories() {
        compose.runOnIdle {
            val navigation = compose.activity.navigationController.navigationState
            navigation.returnToStories()
            navigation.openStory(StoryRoute(47938725))
            navigation.openLinkedStory(StoryRoute(48352939).toDestination())
        }
        compose.waitForIdle()
        doubleBack()
        compose.runOnIdle {
            assertEquals(MainDestination.STORIES, compose.activity.navigationController.navigationState.state.value.currentDestination)
        }
    }

    private fun doubleBack() {
        compose.mainClock.autoAdvance = false
        try {
            repeat(2) {
                compose.runOnUiThread {
                    val dispatcher = compose.activity.onBackPressedDispatcher
                    dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                    dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 500f, 0.65f, BackEventCompat.EDGE_LEFT))
                }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.mainClock.advanceTimeBy(32)
            }
            compose.mainClock.advanceTimeBy(2_000)
        } finally {
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }
}
