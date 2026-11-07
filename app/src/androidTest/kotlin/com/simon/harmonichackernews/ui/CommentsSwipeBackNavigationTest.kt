package com.simon.harmonichackernews.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentBooleanPreference
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.StoryNavigationSeed
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.ui.navigation.MainLaunchIntentRouter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Real touch input through the production host, including native Views and retained destinations. */
@RunWith(AndroidJUnit4::class)
class CommentsSwipeBackNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var settings: AppSettingsRepository
    private var originalSwipeBack = false
    private lateinit var navigation: com.simon.harmonichackernews.ui.navigation.AndroidMainNavigationController

    @Before fun prepare() {
        navigation = compose.activity.navigationController
        val app = compose.activity.harmonicAppComposition
        settings = app.settings
        originalSwipeBack = settings.snapshot().comments.swipeBack
        runBlocking { assertTrue(DebugCachedPostFixture.seed(app.storyCache::storeStory)) }
        runBlocking { assertTrue(app.storyCache.storeStory(DebugCachedPostFixture.storyId + 1,
            DebugCachedPostFixture.payload.replace(DebugCachedPostFixture.storyId.toString(),
                (DebugCachedPostFixture.storyId + 1).toString()))) }
        compose.runOnIdle {
            settings.setCommentBoolean(CommentBooleanPreference.SWIPE_BACK, true)
            navigation.closeWelcome()
            navigation.dismissChangelogDialog()
            navigation.navigationState.returnToStories()
        }
        compose.waitForIdle()
    }

    @After fun restore() {
        settings.setCommentBoolean(CommentBooleanPreference.SWIPE_BACK, originalSwipeBack)
    }

    @Test fun enabledSwipeReturnsToStoriesAndRepeatedVisitsWork() {
        phoneOnly()
        repeat(3) {
            openComments()
            swipeRight()
            assertDestination(MainDestination.STORIES)
        }
    }

    @Test fun websiteCannotSwipeBackUntilCommentsAreExpandedAgain() {
        phoneOnly()
        val original = settings.snapshot().reading
        try {
            compose.runOnIdle {
                settings.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, true)
                navigation.navigationState.openStory(StoryDestination(Int.MAX_VALUE, seed = StoryNavigationSeed(
                    StorySnapshot(Int.MAX_VALUE, title = "Swipe-back browser fixture", url = "https://example.invalid/swipe-back"),
                    isLink = true,
                )))
            }
            compose.waitForIdle()
            val coordinator = requireNotNull(navigation.getCommentsCoordinator())
            val controller = requireNotNull(coordinator.composeUiController)
            fun swipePage() {
                // The native browser's error page can own a second Compose root.
                compose.onNodeWithTag("comments-story-pane-${coordinator.sessionKey}").performTouchInput {
                    swipe(Offset(width * 0.12f, height * 0.55f), Offset(width * 0.95f, height * 0.55f), 600)
                }
                compose.waitForIdle()
            }
            for (closeWebsiteOnBack in listOf(false, true)) {
                compose.runOnIdle {
                    settings.setReadingBoolean(ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK, closeWebsiteOnBack)
                    controller.requestWebsite()
                }
                compose.waitForIdle()
                compose.runOnIdle {
                    assertTrue(controller.isWebsiteVisible())
                    assertFalse(coordinator.canSwipeBackFromComments())
                }
                swipePage()
                assertDestination(MainDestination.STORY)
                compose.runOnIdle { assertTrue(controller.isWebsiteVisible()) }
            }
            compose.runOnIdle { controller.requestExpandSheet() }
            compose.waitForIdle()
            compose.runOnIdle { assertTrue(coordinator.canSwipeBackFromComments()) }
            swipePage()
            assertDestination(MainDestination.STORIES)
        } finally {
            compose.runOnIdle {
                settings.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, original.integratedWebView)
                settings.setReadingBoolean(ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK, original.closeWebViewOnBack)
            }
        }
    }

    @Test fun systemTouchStartingOnHeaderAndCommentTextNavigatesBack() {
        phoneOnly()
        for (text in listOf("A cached post for offline UI testing", "This is a cached top-level comment.")) {
            openComments()
            val bounds = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
            val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
            val command = "input swipe ${(width * 0.12f).toInt()} ${bounds.center.y.toInt()} " +
                "${(width * 0.95f).toInt()} ${bounds.center.y.toInt()} 600"
            val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
            assertDestination(MainDestination.STORIES)
        }
    }

    @Test fun disabledSwipeLeavesCommentsAndButtonBackStillWorks() {
        phoneOnly()
        compose.runOnIdle { settings.setCommentBoolean(CommentBooleanPreference.SWIPE_BACK, false) }
        openComments()
        // With swipe-back disabled, a slow drag within a comment can become its normal
        // long press. Keep this input below the long-press timeout to test plain Back.
        swipeRight(durationMillis = 300)
        assertDestination(MainDestination.STORY)
        compose.runOnIdle { assertFalse(navigation.getCommentsCoordinator()!!.handlesBackInternally()) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertDestination(MainDestination.STORIES)
    }

    @Test fun settingToggleSurvivesActivityRecreation() {
        phoneOnly()
        compose.runOnIdle { navigation.openSettings("comments") }
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Swipe back from comments"))
        compose.onNodeWithText("Swipe back from comments").assertIsOn().performClick().assertIsOff()
        compose.activityRule.scenario.recreate()
        navigation = compose.activity.navigationController
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Swipe back from comments"))
        compose.onNodeWithText("Swipe back from comments").assertIsOff()
        assertFalse(settings.snapshot().comments.swipeBack)
        compose.onNodeWithText("Swipe back from comments").performClick().assertIsOn()
        compose.runOnIdle { navigation.navigationState.returnToStories() }
        openComments()
        swipeRight()
        assertDestination(MainDestination.STORIES)
    }

    @Test fun shortDragVerticalScrollAndCancelledPointerKeepTheSameComments() {
        phoneOnly()
        openComments()
        val original = requireNotNull(navigation.getCommentsCoordinator())
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.55f), Offset(width * 0.38f, height * 0.55f), 1_000)
        }
        assertDestination(MainDestination.STORY)
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.2f, height * 0.55f))
            moveTo(Offset(width * 0.7f, height * 0.55f), 300)
            cancel()
        }
        assertDestination(MainDestination.STORY)
        scrollComments()
        compose.runOnIdle {
            assertSame(original, navigation.getCommentsCoordinator())
            val controller = requireNotNull(original.composeUiController)
            assertTrue(controller.firstVisibleCommentId != 0 || controller.firstVisibleCommentOffset > 0)
        }
        swipeRight()
        assertDestination(MainDestination.STORIES)
    }

    @Test fun commentsFromSettingsAndSubmissionsReturnToTheirActualParent() {
        phoneOnly()
        for (parent in listOf(MainDestination.SETTINGS, MainDestination.SUBMISSIONS)) {
            compose.runOnIdle {
                navigation.navigationState.returnToStories()
                if (parent == MainDestination.SETTINGS) navigation.openSettings("debug")
                else navigation.openSubmissions("pg")
            }
            compose.waitForIdle()
            openComments()
            swipeRight()
            assertDestination(parent)
        }
    }

    @Test fun interceptedInternalLinkRestoresTheSameScrolledComments() {
        phoneOnly()
        openComments()
        val original = requireNotNull(navigation.getCommentsCoordinator())
        val controller = requireNotNull(original.composeUiController)
        scrollComments()
        val position = controller.firstVisibleCommentId to controller.firstVisibleCommentOffset
        assertTrue("The retained parent must be scrolled", position.first != 0 || position.second > 0)
        val serial = navigation.navigationState.state.value.storyRequest!!.serial
        compose.runOnIdle {
            navigation.scene.links.open("https://news.ycombinator.com/item?id=${DebugCachedPostFixture.storyId + 1}")
        }
        compose.waitUntil(10_000) { navigation.navigationState.state.value.storyRequest?.serial != serial }
        compose.waitForIdle()
        swipeRight()
        compose.runOnIdle {
            assertEquals(serial, navigation.navigationState.state.value.storyRequest?.serial)
            assertSame(original, navigation.getCommentsCoordinator())
            assertEquals(position, controller.firstVisibleCommentId to controller.firstVisibleCommentOffset)
        }
    }

    @Test fun interceptedExternalLinkSwipeFinishesTheActivity() {
        phoneOnly()
        val activity = compose.activity
        compose.runOnIdle {
            MainLaunchIntentRouter(navigation).route(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://news.ycombinator.com/item?id=${DebugCachedPostFixture.storyId}")))
        }
        compose.waitForIdle()
        assertTrue(navigation.isExternalStoryEntry)
        swipeRight(waitForIdle = false)
        compose.waitUntil(5_000) { activity.isFinishing || activity.isDestroyed }
    }

    @Test fun commentActionOverlayDoesNotSwipeAwayItsScreen() {
        phoneOnly()
        openComments()
        val controller = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
        compose.runOnIdle {
            controller.restoreCommentActions(PortableCommentItem(
                CommentSnapshot(900_000_011, author = "cache_reader", text = "A comment", expandedAnchorText = "A comment"),
                CommentPresentationSnapshot(expanded = true),
            ))
        }
        compose.waitForIdle()
        swipeRight()
        assertDestination(MainDestination.STORY)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertDestination(MainDestination.STORY)
    }

    @Test fun twoPaneDisablesGestureAndSettingWithoutChangingPreference() {
        assumeTrue(navigation.isAdaptiveTwoPane())
        openComments()
        swipeRight()
        assertDestination(MainDestination.STORY)
        val activity = compose.activity
        compose.runOnIdle {
            MainLaunchIntentRouter(navigation).route(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://news.ycombinator.com/item?id=${DebugCachedPostFixture.storyId + 1}")))
        }
        compose.waitForIdle()
        swipeRight()
        assertDestination(MainDestination.STORY)
        assertFalse(activity.isFinishing)
        compose.runOnIdle { navigation.openSettings("comments") }
        compose.waitForIdle()
        compose.onAllNodes(hasScrollAction()).onLast()
            .performScrollToNode(hasText("Swipe back from comments"))
        compose.onNodeWithText("Swipe back from comments").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(settings.snapshot().comments.swipeBack) }
        // Standalone details (including external links) also disable the gesture on tablets.
        openComments()
        swipeRight()
        assertDestination(MainDestination.STORY)
    }

    private fun openComments() {
        compose.runOnIdle { navigation.navigationState.openStory(StoryRoute(DebugCachedPostFixture.storyId)) }
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("comment-row").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun phoneOnly() = assumeTrue(!navigation.isAdaptiveTwoPane())

    private fun assertDestination(expected: MainDestination) {
        compose.waitForIdle()
        compose.waitUntil(5_000) { navigation.navigationState.state.value.currentDestination == expected }
        compose.runOnIdle { assertEquals(expected, navigation.navigationState.state.value.currentDestination) }
    }

    private fun scrollComments() {
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.5f, height * 0.7f))
            repeat(20) { step ->
                moveTo(Offset(width * 0.5f, height * (0.7f - (step + 1) * 0.02f)), 30)
            }
            // Settle the drag without starting a fling before recording the exact position.
            advanceEventTime(300)
            up()
        }
        compose.waitForIdle()
    }

    private fun swipeRight(waitForIdle: Boolean = true, durationMillis: Long = 600) {
        compose.onRoot().performTouchInput {
            swipe(Offset(width * 0.12f, height * 0.55f), Offset(width * 0.95f, height * 0.55f), durationMillis)
        }
        if (waitForIdle) compose.waitForIdle()
    }
}
