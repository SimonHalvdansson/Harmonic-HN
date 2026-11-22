@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.presentation.NewStoriesFilter
import com.simon.harmonichackernews.presentation.PortableStoryListState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.ui.stories.HeaderStatus
import com.simon.harmonichackernews.ui.stories.StoriesRoot
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoriesAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun completedFilteredFeedShowsRecoveryAndStopsLoading() {
        var requestedFilter: NewStoriesFilter? = null
        val controller = StoriesScreenController.create(
            defaultStoryHeightPx = 100,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = object : StoriesScreenController.Listener by StoriesTestListener() {
                override fun onNewStoriesFilterChanged(filter: NewStoriesFilter) { requestedFilter = filter }
            },
        )
        val loading = StoriesState(
            currentType = StoryType.NEW_STORIES,
            newStoriesFilter = NewStoriesFilter(minimumPoints = 50, minimumComments = 50),
        )
        controller.updateContent(loading)
        compose.setContent { HeaderStatus(controller, searchMode = false) }
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.onNodeWithText("No stories match these filters").assertDoesNotExist()
        compose.onNode(progress).assertIsDisplayed()

        compose.runOnIdle {
            controller.updateContent(loading.copy(mainList = PortableStoryListState(hasCompletedLoad = true)))
        }
        compose.onNodeWithText("No stories match these filters").assertIsDisplayed()
        compose.onNodeWithText("Try lower minimums or clear the filters.").assertIsDisplayed()
        compose.onNode(progress).assertDoesNotExist()
        compose.onNodeWithText("Clear filters").performClick()
        compose.runOnIdle { assertEquals(NewStoriesFilter(), requestedFilter) }
    }

    @Test
    fun searchHidesRetainedFeedSemanticsAndBackRestoresItsState() {
        val searching = mutableStateOf(false)
        val predictiveBack = mutableStateOf(false)
        compose.setContent {
            StoriesRoot(
                searching = searching.value,
                suppressSearchAutoFocus = false,
                predictiveBackActive = predictiveBack.value,
                predictiveBackProgress = if (predictiveBack.value) 0.5f else 0f,
                backgroundColor = Color.White,
                mainLayer = {
                    val clicks = remember { mutableStateOf(0) }
                    BasicText("Feed clicks: ${clicks.value}", Modifier.clickable { clicks.value++ })
                },
                searchLayer = { BasicText("Search results") },
            )
        }
        compose.onNodeWithText("Feed clicks: 0").performClick()
        compose.onNodeWithText("Feed clicks: 1").assertIsDisplayed()
        compose.onNodeWithText("Search results").assertDoesNotExist()

        compose.runOnIdle { searching.value = true }
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithText("Feed clicks: 1").assertDoesNotExist()

        compose.runOnIdle { predictiveBack.value = true }
        compose.onNodeWithText("Search results").assertDoesNotExist()
        compose.onNodeWithText("Feed clicks: 1").assertDoesNotExist()

        compose.runOnIdle { predictiveBack.value = false }
        compose.onNodeWithText("Search results").assertIsDisplayed()

        compose.runOnIdle { searching.value = false }
        compose.onNodeWithText("Feed clicks: 1").assertIsDisplayed()
        compose.onNodeWithText("Search results").assertDoesNotExist()
    }
}
