@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.presentation.NewStoriesFilter
import com.simon.harmonichackernews.presentation.PortableStoryListState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.stories.HeaderStatus
import com.simon.harmonichackernews.ui.stories.StoriesRoot
import com.simon.harmonichackernews.ui.stories.StoriesScreen
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoriesAccessibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchPullShowsOnlyThePullIndicatorAndNextQueryShowsHeaderProgress() {
        val settings = StoredUserSettings(InMemoryKeyValueStore(), emptyFlow())
        var state = StoriesState(
            searching = true, searchDraft = "kotlin",
            displaySettings = StoryDisplaySettings.from(settings.story),
        )
        lateinit var controller: StoriesScreenController
        fun publish(query: String, loading: Boolean) {
            state = state.copy(searchDraft = query, search = state.search.copy(loading = loading))
            controller.updateContent(state)
        }
        controller = StoriesScreenController.create(
            defaultStoryHeightPx = 100,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = object : StoriesScreenController.Listener by StoriesTestListener() {
                override fun consumeSearchFocusRequest() = false
                override fun onRefresh(showMainLoadingIndicator: Boolean) {
                    controller.updateContent(state)
                    publish(state.searchDraft, true)
                }
                override fun onSearch(query: String) = publish(query, true)
            },
        )
        controller.updateContent(state)
        compose.setContent {
            StoriesScreen(
                controller = controller, storyItemModelCacheKey = 0,
                storyItemModel = { _, _, _, _, _ -> error("No stories in this fixture") },
                filterColors = harmonicFilterButtonColors(),
            )
        }
        val progress = compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        ))
        compose.onNodeWithText("No stories found").assertIsDisplayed()
        compose.onRoot().performTouchInput { swipeDown() }
        progress.assertIsDisplayed()
        val pullTop = progress.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { publish(state.searchDraft, false) }
        progress.assertDoesNotExist()

        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("compose")
        field.performImeAction()
        progress.assertIsDisplayed()
        assertTrue("Query progress belongs below the search field", progress.fetchSemanticsNode().boundsInRoot.top >
            field.fetchSemanticsNode().boundsInRoot.bottom)
        assertTrue("Pull progress belongs above the search filters", pullTop <
            compose.onNodeWithText("Relevance").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { assertEquals("compose", state.searchDraft) }
    }

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
