package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
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

    @Test fun reopeningSubmissionsFromItsAuthorsPostRetainsTheParent() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        val tablet = compose.activity.resources.configuration.smallestScreenWidthDp >= 600
        try {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.openSubmissions("starkparker")
            }
            compose.waitForIdle()
            compose.runOnIdle { navigation.openSubmissionStory(StoryRoute(49777833).toDestination()) }
            compose.waitForIdle()
            val parent = requireNotNull(navigation.getCommentsCoordinator())
            val parentRoot = parent.webViewRoot
            val parentWidth = parentRoot.width
            val parentStack = navigation.navigationState.state.value.destinationStack
            for (userName in listOf("starkparker", "pg")) {
                compose.runOnIdle { navigation.showUserDialog(userName, null) }
                compose.waitForIdle()
                compose.runOnIdle {
                    navigation.dismissUserDialog()
                    navigation.openSubmissions(userName)
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertTrue("The preceding post must keep its original surface", parentRoot.isAttachedToWindow)
                    assertEquals(parentWidth, parentRoot.width)
                    val dispatcher = compose.activity.onBackPressedDispatcher
                    dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                    dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.8f, BackEventCompat.EDGE_LEFT))
                }
                compose.waitForIdle()
                compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(MainDestination.SUBMISSIONS, navigation.navigationState.state.value.currentDestination)
                    assertEquals(parentWidth, parentRoot.width)
                }
                if (tablet) {
                    // Also update a retained nested scene, then cover it with a third visit.
                    compose.runOnIdle { navigation.openSubmissionStory(StoryRoute(47938725).toDestination()) }
                    compose.waitForIdle()
                    val nestedRoot = requireNotNull(navigation.getCommentsCoordinator()).webViewRoot
                    compose.runOnIdle { navigation.openSubmissions(userName) }
                    compose.waitForIdle()
                    compose.runOnIdle {
                        assertTrue(parentRoot.isAttachedToWindow)
                        assertTrue(nestedRoot.isAttachedToWindow)
                        navigation.closeSubmissions()
                    }
                    compose.waitForIdle()
                    compose.runOnIdle {
                        assertSame(nestedRoot, navigation.getCommentsCoordinator()?.webViewRoot)
                        navigation.closeStory()
                    }
                    compose.waitForIdle()
                }
                compose.runOnIdle {
                    val dispatcher = compose.activity.onBackPressedDispatcher
                    dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                    dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.8f, BackEventCompat.EDGE_LEFT))
                    dispatcher.onBackPressed()
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(parentStack, navigation.navigationState.state.value.destinationStack)
                    assertSame(parent, navigation.getCommentsCoordinator())
                    assertEquals(parentWidth, parentRoot.width)
                }
            }
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    @Test fun buttonBackAfterPredictivePopStillAnimates() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            for (reopenChild in listOf(false, true)) {
                compose.runOnIdle {
                    navigation.navigationState.returnToStories()
                    navigation.dismissWelcomeDialog()
                    navigation.dismissChangelogDialog()
                    navigation.openSettings("debug")
                    navigation.navigationState.openStory(StoryRoute(47938725))
                }
                compose.waitForIdle()
                val parent = requireNotNull(navigation.getCommentsCoordinator()).webViewRoot
                compose.runOnIdle {
                    navigation.navigationState.openLinkedStory(StoryRoute(48352939).toDestination())
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    val dispatcher = compose.activity.onBackPressedDispatcher
                    dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                    dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.8f, BackEventCompat.EDGE_LEFT))
                    dispatcher.onBackPressed()
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertEquals(47938725, navigation.navigationState.state.value.storyRequest?.storyId)
                }
                if (reopenChild) {
                    // Revisiting the same parent through a newly opened story must not reuse an
                    // earlier gesture's completion either.
                    compose.runOnIdle {
                        navigation.navigationState.openLinkedStory(StoryRoute(48352939).toDestination())
                    }
                    compose.waitForIdle()
                    val reopened = requireNotNull(navigation.getCommentsCoordinator()).webViewRoot
                    compose.mainClock.autoAdvance = false
                    compose.runOnUiThread { navigation.closeStory() }
                    compose.mainClock.advanceTimeBy(64)
                    compose.runOnUiThread {
                        assertTrue("Revisiting the same parent must animate the new story's exit", reopened.isAttachedToWindow)
                    }
                    compose.mainClock.advanceTimeBy(600)
                    compose.mainClock.autoAdvance = true
                    compose.waitForIdle()
                }
                compose.mainClock.autoAdvance = false
                compose.runOnUiThread { navigation.closeStory() }
                compose.mainClock.advanceTimeBy(64)
                compose.runOnUiThread {
                    assertTrue("A completed child gesture must not skip the parent's button exit", parent.isAttachedToWindow)
                }
                compose.mainClock.advanceTimeBy(600)
                compose.runOnUiThread { assertFalse(parent.isAttachedToWindow) }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    @Test fun submissionsKeepsTheDebugStoryInItsOriginalFullScreenSurface() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.openSettings("debug")
                navigation.navigationState.openStory(StoryRoute(47938725))
            }
            compose.waitForIdle()
            compose.runOnIdle { navigation.navigationState.openLinkedStory(StoryRoute(48352939).toDestination()) }
            compose.waitForIdle()
            val parent = requireNotNull(navigation.getCommentsCoordinator())
            val root = parent.webViewRoot
            val width = root.width
            val height = root.height
            compose.runOnIdle { navigation.openSubmissions("pg") }
            compose.waitForIdle()
            compose.runOnIdle {
                assertTrue("Submissions must retain its actual parent surface", root.isAttachedToWindow)
                assertEquals("A full-screen parent must not turn into a Stories detail pane", width, root.width)
                assertEquals(height, root.height)
            }
            compose.runOnIdle {
                val dispatcher = compose.activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.8f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(width, root.width)
                compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(MainDestination.SUBMISSIONS, navigation.navigationState.state.value.currentDestination) }
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame(parent, navigation.getCommentsCoordinator())
                assertEquals(width, root.width)
            }
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    @Test fun retainedStoryKeepsItsOwnStatusBarTintDuringChildBack() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.openSettings("debug")
                navigation.navigationState.openStory(StoryRoute(47938725))
            }
            compose.waitForIdle()
            val parent = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
            compose.runOnIdle { navigation.navigationState.openLinkedStory(StoryRoute(48352939).toDestination()) }
            compose.waitForIdle()
            val child = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
            compose.runOnIdle {
                val dispatcher = compose.activity.onBackPressedDispatcher
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 500f, 0.8f, BackEventCompat.EDGE_LEFT))
            }
            compose.waitForIdle()
            // Distinct deterministic colors make this a rendering assertion independent of
            // downloaded preview images or the emulator's selected theme.
            compose.runOnIdle {
                parent.updateStatusBarHeaderColor(Color.Magenta)
                parent.updateStatusBarHeaderCoverage(1f)
                child.updateStatusBarHeaderColor(Color.Blue)
                child.updateStatusBarHeaderCoverage(1f)
            }
            compose.waitForIdle()
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            var parentTintPixels = 0
            for (y in 0 until pixels.height / 5) {
                for (x in pixels.width / 40 until pixels.width / 12) {
                    val pixel = pixels[x, y]
                    if (pixel.red > 0.5f && pixel.blue > 0.5f && pixel.green < 0.2f) parentTintPixels++
                }
            }
            assertTrue("The revealed parent's status bar must keep its own tint", parentTintPixels > 50)
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            compose.waitForIdle()
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    @Test fun tabletUpRetainsStandaloneStoryUntilItsExitFinishes() {
        org.junit.Assume.assumeTrue(compose.activity.resources.configuration.smallestScreenWidthDp >= 600)
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            // Exercise both an empty detail pane and an existing story beneath Settings.
            for (underlyingStory in listOf(false, true)) {
                compose.runOnIdle {
                    navigation.navigationState.returnToStories()
                    if (underlyingStory) navigation.navigationState.openStory(StoryRoute(47938725))
                    navigation.openSettings("debug")
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    navigation.navigationState.openStory(StoryRoute(48352939))
                }
                compose.waitForIdle()
                val outgoingRoot = requireNotNull(navigation.getCommentsCoordinator()).webViewRoot
                compose.mainClock.autoAdvance = false
                compose.runOnUiThread { navigation.closeStory() }
                compose.mainClock.advanceTimeBy(64)
                compose.runOnUiThread {
                    assertEquals(MainDestination.SETTINGS, navigation.navigationState.state.value.currentDestination)
                    assertTrue("Up must retain the outgoing story during its exit", outgoingRoot.isAttachedToWindow)
                }
                compose.mainClock.advanceTimeBy(600)
                compose.runOnUiThread {
                    assertFalse("The outgoing story must be released after its exit", outgoingRoot.isAttachedToWindow)
                }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }

    @Test fun secondGestureDuringSettingsPopIsNotLost() {
        val activity = compose.activity
        val twoPane = activity.resources.configuration.smallestScreenWidthDp >= 600
        compose.runOnIdle {
            compose.activity.navigationController.navigationState.returnToStories()
            compose.activity.navigationController.openSettings("debug")
        }
        compose.waitForIdle()
        doubleBack()
        if (twoPane) {
            // Tablet Settings has no separate list destination: the second back exits the app.
            assertTrue(activity.isFinishing || activity.isDestroyed)
        } else {
            compose.runOnIdle {
                assertEquals(MainDestination.STORIES, activity.navigationController.navigationState.state.value.currentDestination)
            }
        }
    }

    @Test fun secondGestureFromSubmissionsPopsTheUnderlyingDebugPage() {
        val twoPane = compose.activity.resources.configuration.smallestScreenWidthDp >= 600
        compose.runOnIdle {
            compose.activity.navigationController.navigationState.returnToStories()
            compose.activity.navigationController.openSettings("debug")
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.navigationController.navigationState.openSubmissions("pg") }
        compose.waitForIdle()
        doubleBack()
        compose.runOnIdle {
            assertEquals(
                if (twoPane) MainDestination.STORIES else MainDestination.SETTINGS,
                compose.activity.navigationController.navigationState.state.value.currentDestination,
            )
        }
        if (twoPane) return
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
