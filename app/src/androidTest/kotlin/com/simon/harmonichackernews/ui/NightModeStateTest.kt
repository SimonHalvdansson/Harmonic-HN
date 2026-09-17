package com.simon.harmonichackernews.ui

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightModeStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun systemNightModeRetainsActivityAndTheOpenPreviewPage() {
        val activity = compose.activity
        val originalMode = activity.getSystemService(UiModeManager::class.java).nightMode
        val initiallyDark = activity.resources.configuration.isNight()
        val controller = requireNotNull(activity.navigationController.storiesComposeController)
        val stories = listOf(previewStory(1, "First preview"), previewStory(2, "Second preview"))
        try {
            compose.runOnIdle {
                controller.showStoryPreview(stories, listOf(0xffeeeeee.toInt(), 0xffeeeeee.toInt()), 2)
            }
            compose.onNodeWithText("Second preview").assertIsDisplayed()
            for (dark in listOf(!initiallyDark, initiallyDark)) {
                setNightMode(if (dark) "yes" else "no")
                compose.waitUntil(timeoutMillis = 10_000) {
                    compose.activity.resources.configuration.isNight() == dark
                }
                compose.runOnIdle {
                    assertSame("Night mode must re-theme the existing activity", activity, compose.activity)
                    assertSame(controller, compose.activity.navigationController.storiesComposeController)
                    assertEquals(2, controller.visibleStoryPreviewId)
                    assertEquals(0, controller.storyPreviewDismissRequest)
                }
                compose.onNodeWithText("Second preview").assertIsDisplayed()
            }
        } finally {
            setNightMode(when (originalMode) {
                UiModeManager.MODE_NIGHT_YES -> "yes"
                UiModeManager.MODE_NIGHT_NO -> "no"
                UiModeManager.MODE_NIGHT_CUSTOM -> "custom"
                else -> "auto"
            })
            compose.runOnIdle { controller.completeStoryPreviewDismiss() }
        }
    }

    private fun previewStory(id: Int, title: String): StoryListItemSnapshot {
        val story = Story(title, id, false, false)
        return StoryListItemSnapshot(story.toSnapshot(), story.presentationSnapshot())
    }

    private fun Configuration.isNight() =
        uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun setNightMode(mode: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd uimode night $mode")
        ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }
}
