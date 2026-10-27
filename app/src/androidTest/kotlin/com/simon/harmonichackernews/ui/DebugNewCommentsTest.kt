package com.simon.harmonichackernews.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DebugNewCommentsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun debugMenuOpensARepeatableThreadWithEveryCommentMarkedNew() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.closeWelcome()
                navigation.dismissChangelogDialog()
                navigation.openSettings("debug")
            }
            compose.onNodeWithText("All comments marked as new").performScrollTo()
            capture("debug-menu")
            compose.onNodeWithText("All comments marked as new").performClick()
            compose.waitUntil(15_000) {
                compose.runOnUiThread {
                    navigation.getCommentsCoordinator()?.composeUiController?.let { it.commentsLoaded && it.visibleComments.isNotEmpty() } == true
                }
            }
            compose.runOnUiThread {
                val controller = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
                assertEquals(6, controller.visibleComments.size)
                assertTrue(controller.visibleComments.all { it.comment.isNew })
            }
            capture("new-comments-story")
            // The full app keeps its header layout active. Dispatch the same row callback
            // without waiting for global Compose idleness; the isolated row tests cover taps.
            fun toggleRoot() = compose.runOnUiThread {
                val controller = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
                val root = controller.visibleComments.first()
                controller.listener.onToggleComment(root.comment, root.sourceIndex)
            }
            toggleRoot()
            compose.waitUntil(5_000) {
                compose.runOnUiThread { navigation.getCommentsCoordinator()?.composeUiController?.visibleComments?.size == 5 }
            }
            toggleRoot()
            compose.waitUntil(5_000) {
                compose.runOnUiThread { navigation.getCommentsCoordinator()?.composeUiController?.visibleComments?.size == 6 }
            }
            capture("new-comments-expanded")
        } finally {
            compose.runOnUiThread { navigation.navigationState.restore(original) }
        }
    }

    private fun capture(name: String) {
        val label = InstrumentationRegistry.getArguments().getString("polishEvidence") ?: return
        val directory = File(compose.activity.getExternalFilesDir(null), "polish/$label").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
