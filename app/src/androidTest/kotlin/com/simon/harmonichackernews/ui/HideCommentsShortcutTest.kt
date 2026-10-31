package com.simon.harmonichackernews.ui

import androidx.activity.BackEventCompat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.ui.settings.HideCommentsSettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HideCommentsShortcutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun placeholderShortcutCentersSettingAndBackRestoresTheSameDialog() {
        val navigation = compose.activity.navigationController
        val original = navigation.navigationState.restoration()
        try {
            compose.runOnIdle {
                navigation.closeWelcome()
                navigation.dismissChangelogDialog()
                navigation.navigationState.openStory(StoryRoute(DebugCachedPostFixture.storyId))
            }
            compose.waitForIdle()
            val controller = requireNotNull(navigation.getCommentsCoordinator()?.composeUiController)
            for ((index, text) in listOf("[dead]", "[delayed]", "[flagged]", "[deleted]", "[removed]").withIndex()) {
                val shortcut = "Toggle visibility of $text"
                val comment = PortableCommentItem(
                    CommentSnapshot(900_000_100 + index, author = "placeholder_reader",
                        text = " \n$text\t ", expandedAnchorText = text),
                    CommentPresentationSnapshot(expanded = true),
                )
                compose.runOnIdle { controller.restoreCommentActions(comment) }
                compose.onNodeWithText(shortcut).assertIsDisplayed().performClick()
                compose.onNodeWithText("Hide comments").assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(HideCommentsSettingsRoute,
                        navigation.navigationState.state.value.settingsRequest?.initialSectionRoute)
                }
                val row = compose.onNodeWithText("Hide comments").fetchSemanticsNode().boundsInRoot
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertEquals("The setting should be centered on screen", root.center.y, row.center.y, 12f)
                if (index == 0) {
                    compose.onNodeWithText("Hide comments").performClick()
                    compose.onNodeWithText("Applies when the entire comment is [flagged], [dead] or [delayed].")
                        .assertIsDisplayed()
                    compose.onNodeWithText("Done").performClick()
                    compose.onNodeWithText("Hide comments").assertIsDisplayed()
                    compose.runOnIdle {
                        val dispatcher = compose.activity.onBackPressedDispatcher
                        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
                        dispatcher.dispatchOnBackProgressed(BackEventCompat(400f, 900f, 0.7f, BackEventCompat.EDGE_LEFT))
                        dispatcher.dispatchOnBackCancelled()
                    }
                    compose.onNodeWithText("Hide comments").assertIsDisplayed()
                }
                if (index % 2 == 0) {
                    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
                } else {
                    compose.onNodeWithContentDescription("Navigate up").performClick()
                }
                compose.onNodeWithText(shortcut).assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(MainDestination.STORY, navigation.navigationState.state.value.currentDestination)
                    assertSame(controller, navigation.getCommentsCoordinator()?.composeUiController)
                    assertEquals(comment.id, controller.getVisibleCommentActionId())
                    assertTrue(controller.commentActionDismissRequest == 0)
                }
                compose.runOnIdle { controller.requestDismissCommentActions() }
                compose.waitForIdle()
            }
            compose.runOnIdle {
                controller.restoreCommentActions(PortableCommentItem(
                    CommentSnapshot(900_000_200, text = "A normal comment mentioning [dead]",
                        expandedAnchorText = "A normal comment mentioning [dead]"),
                    CommentPresentationSnapshot(expanded = true),
                ))
            }
            compose.onNodeWithText("Toggle visibility of [dead]").assertDoesNotExist()
        } finally {
            compose.runOnIdle { navigation.navigationState.restore(original) }
        }
    }
}
