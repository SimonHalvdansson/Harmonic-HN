package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.CommentFeedItem
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentFeedItemTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loadingAndResolvedCardsKeepActionsAndLimitLongComments() {
        val title = mutableStateOf<String?>(null)
        val body = (1..40).joinToString("") { "<p>Comment line $it</p>" }
        var storiesOpened = 0
        var repliesOpened = 0
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("dark", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                CommentFeedItem(
                    rootStoryTitle = title.value,
                    timeText = "1h",
                    html = body,
                    canOpenStory = true,
                    displaySettings = settings,
                    onOpenLink = {},
                    onStoryClick = { storiesOpened++ },
                    onRepliesClick = { repliesOpened++ },
                    modifier = Modifier.testTag("comment-feed-card"),
                )
            }
        }
        compose.onNodeWithText("On").assertIsDisplayed()
        compose.onNodeWithText("Loading", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Story").assertIsDisplayed().performClick()
        compose.onNodeWithText("Replies").assertIsDisplayed().performClick()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Comment line 1", substring = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(layouts)
        }
        assertEquals(16, layouts.single().lineCount)
        assertTrue(layouts.single().hasVisualOverflow)
        compose.runOnIdle { title.value = "Resolved parent story" }
        compose.onNodeWithText("On \"Resolved parent story\"").assertIsDisplayed()
        compose.onNodeWithText("Story").performClick()
        compose.onNodeWithText("Replies").performClick()
        assertEquals(2, storiesOpened)
        assertEquals(2, repliesOpened)
    }

    private val settings = StoryDisplaySettings(
        showPoints = true, compactPoints = false, includeTopLevelDomain = true,
        showCommentsCount = true, compactView = false, showFavicons = true,
        previewImageMode = StoryPreviewMode.MEDIUM, borderlessLargePreviewImage = false,
        showPreviewText = false, storyTextSize = 20f, showIndex = true, compactHeader = false,
        commentsButtonOnLeft = false, displayStyle = DisplayStyle.RAISED, tintCardsFromImages = false,
        paletteTintMode = "default", dimReadStories = false, hotnessThreshold = 0,
        faviconProvider = "default", font = "default", commentTextSize = 14f,
    )
}
