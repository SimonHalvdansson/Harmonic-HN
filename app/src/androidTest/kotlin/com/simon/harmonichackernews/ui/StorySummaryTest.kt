package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.comments.StorySummary
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorySummaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun absentAndLoadedSummaryComposeWithoutPreviewScopeAndRouteLinkTaps() {
        val initial = StoryListItemSnapshot(
            StorySnapshot(42, url = "https://example.org/article?edition=1"),
            StoryPresentationSnapshot(loaded = true),
        )
        val story = mutableStateOf(initial)
        val opened = mutableListOf<String>()
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                // The header places summary text outside its preview CompositionLocal scope.
                StorySummary(story.value, settings, onOpenLink = { opened += it })
            }
        }
        compose.onNodeWithText("Summary").assertDoesNotExist()
        compose.runOnIdle {
            story.value = initial.copy(presentation = initial.presentation.copy(
                aiSummaryText = "[Details](#details) and [discussion](https://news.ycombinator.com/item?id=42)",
                summaryGeneratedSuccessfully = true,
            ))
        }
        compose.onNodeWithText("Summary").assertIsDisplayed()
        val text = compose.onNodeWithText("Details and discussion").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        for (label in listOf("Details", "discussion")) {
            val character = layout.layoutInput.text.text.indexOf(label) + label.length / 2
            text.performTouchInput { click(layout.getBoundingBox(character).center) }
        }
        compose.runOnIdle {
            assertEquals(
                listOf(
                    "https://example.org/article?edition=1#details",
                    "https://news.ycombinator.com/item?id=42",
                ),
                opened,
            )
        }
    }

    private val settings = CommentDisplaySettings(
        collapseParent = false, showFavicons = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.RAISED, showDividers = false,
        highlightCommentMeta = false, collectReferenceLinks = false, hasAccountDetails = false,
        canProvideSummary = false, showAdditionalSummaryInfo = false, enableSummaryBoldFormatting = true,
    )
}
