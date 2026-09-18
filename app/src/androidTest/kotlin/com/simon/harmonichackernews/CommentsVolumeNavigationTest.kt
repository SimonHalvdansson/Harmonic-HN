package com.simon.harmonichackernews

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsVolumeNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun volumeKeysNavigateOnlyTheVisibleCommentsDestination() {
        val activity = compose.activity
        val app = activity.harmonicAppComposition
        val navigation = activity.navigationController
        val preferences = AndroidKeyValueStore.defaults(activity)
        val originalVolume = preferences.getString(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION, null)
        val hadWebView = preferences.contains(UserPreferenceKeys.WEBVIEW)
        val originalWebView = app.userSettings.reading.integratedWebView
        val originalNavigation = navigation.navigationState.restoration()
        // Use a fresh cache entry so neither content nor network timing controls this regression.
        val storyId = generateSequence(990000017) { it + 1 }.first { !app.storyCache.hasStoryPayload(it) }
        try {
            assertTrue(runBlocking {
                app.storyCache.storeStory(
                    storyId,
                    """{"id":$storyId,"type":"story","title":"Volume navigation regression","author":"test","children":[]}""",
                )
            })
            compose.runOnIdle {
                app.settings.setCommentsVolumeNavigation(CommentVolumeNavigationMode.TOP_LEVEL)
                app.settings.setReadingBoolean(ReadingBooleanPreference.INTEGRATED_WEB_VIEW, false)
                navigation.dismissWelcomeDialog()
                navigation.dismissChangelogDialog()
                navigation.openStory(StoryDestination(storyId))
            }
            compose.waitUntil(10_000) {
                var ready = false
                compose.runOnUiThread {
                    ready = navigation.getCommentsCoordinator()?.canNavigateCommentsWithVolumeButtons() == true
                }
                ready
            }
            val coordinator = requireNotNull(navigation.getCommentsCoordinator())
            val controller = requireNotNull(coordinator.composeUiController)
            assertVisibleKeys(controller, topLevelOnly = true)

            compose.runOnIdle { navigation.openSettings(null) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(MainDestination.SETTINGS, navigation.navigationState.state.value.currentDestination)
                assertSame("Settings must retain the underlying comments host", coordinator, navigation.getCommentsCoordinator())
                assertFalse(coordinator.canNavigateCommentsWithVolumeButtons())
                assertKeysDoNotNavigate(controller)
                navigation.closeSettings()
            }
            compose.waitForIdle()
            assertVisibleKeys(controller, topLevelOnly = true)

            compose.runOnIdle { navigation.openEditor(EditorDestination()) }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(MainDestination.EDITOR, navigation.navigationState.state.value.currentDestination)
                assertSame("The editor must retain the underlying comments host", coordinator, navigation.getCommentsCoordinator())
                assertFalse(coordinator.canNavigateCommentsWithVolumeButtons())
                assertKeysDoNotNavigate(controller)
                navigation.closeEditor()
            }
            compose.waitForIdle()
            compose.runOnIdle { app.settings.setCommentsVolumeNavigation(CommentVolumeNavigationMode.ALL) }
            assertVisibleKeys(controller, topLevelOnly = false)
            compose.runOnIdle {
                app.settings.setCommentsVolumeNavigation(CommentVolumeNavigationMode.DISABLED)
                assertKeysDoNotNavigate(controller)
            }
        } finally {
            compose.runOnIdle {
                navigation.closeEditor()
                navigation.closeSettings()
                navigation.navigationState.restore(originalNavigation)
                if (originalVolume == null) preferences.remove(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION)
                else preferences.putString(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION, originalVolume)
                if (hadWebView) preferences.putBoolean(UserPreferenceKeys.WEBVIEW, originalWebView)
                else preferences.remove(UserPreferenceKeys.WEBVIEW)
            }
            compose.waitForIdle()
            runBlocking { app.storyCache.remove(storyId) }
        }
    }

    private fun assertVisibleKeys(controller: CommentsComposeController, topLevelOnly: Boolean) {
        compose.runOnIdle {
            assertTrue(requireNotNull(compose.activity.navigationController.getCommentsCoordinator())
                .canNavigateCommentsWithVolumeButtons())
            for (code in listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) {
                controller.navigationRequest?.let(controller::consumeNavigationRequest)
                assertTrue(compose.activity.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code)))
                val request = requireNotNull(controller.navigationRequest)
                assertEquals(code == KeyEvent.KEYCODE_VOLUME_DOWN, request.forward)
                assertEquals(topLevelOnly, request.topLevelOnly)
                assertTrue(request.scaleLongScrollSpeed)
                // Observe the production scroll request synchronously before Compose consumes it.
                controller.consumeNavigationRequest(request)
            }
        }
    }

    private fun assertKeysDoNotNavigate(controller: CommentsComposeController) {
        controller.navigationRequest?.let(controller::consumeNavigationRequest)
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) {
            // Calling the Activity callback directly does not inject an OS volume change.
            compose.activity.onKeyDown(code, KeyEvent(KeyEvent.ACTION_DOWN, code))
            assertNull("Covered/disabled comments must not receive a scroll request", controller.navigationRequest)
        }
    }
}
