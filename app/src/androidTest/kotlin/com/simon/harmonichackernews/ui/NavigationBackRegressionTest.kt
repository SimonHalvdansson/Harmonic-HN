package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test fun restoredCommentDialogConsumesBackAfterNestedStories() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.navigationState.openStory(StoryRoute(49805972))
            }
            compose.waitForIdle()
            val parent = requireNotNull(navigation.getCommentsCoordinator())
            val comments = requireNotNull(parent.composeUiController)
            compose.runOnIdle {
                comments.showCommentActions(PortableCommentItem(
                    CommentSnapshot(49805972, author = "reader", text = "Linked comment"),
                    CommentPresentationSnapshot(expanded = true),
                ))
            }
            compose.waitForIdle()
            repeat(3) {
                compose.runOnIdle {
                    navigation.navigationState.openLinkedStory(StoryRoute(48352939).toDestination())
                }
                compose.waitForIdle()
            }
            repeat(3) {
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
            }
            compose.runOnIdle {
                assertSame(parent, navigation.getCommentsCoordinator())
                assertTrue(comments.isCommentActionOverlayShowing())
                assertTrue(parent.handlesBackInternally())
            }
            val serial = navigation.navigationState.state.value.storyRequest?.serial
            compose.runOnUiThread {
                val dispatcher = compose.activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(240f, 500f, 0.65f, BackEventCompat.EDGE_LEFT))
                dispatcher.onBackPressed()
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(serial, navigation.navigationState.state.value.storyRequest?.serial)
                assertFalse(comments.isCommentActionOverlayShowing())
            }
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
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
