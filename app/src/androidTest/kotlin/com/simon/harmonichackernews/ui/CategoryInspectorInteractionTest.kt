@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel
import com.simon.harmonichackernews.ui.content.StoryRow
import com.simon.harmonichackernews.ui.content.StoryRowStyleContext
import com.simon.harmonichackernews.ui.content.toStoryRowStyle
import com.simon.harmonichackernews.ui.settings.sampleCategoryInspection
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryInspectorInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actualChipMorphAndNestedEditorReturnWithoutOpeningOrMovingTheStory() {
        val app = kotlinx.coroutines.runBlocking {
            (compose.activity.application as HarmonicApplication).awaitStartup()
        }
        val scene = app.createScene()
        val inspection = sampleCategoryInspection()
        val model = SettingsStoryPreviewModel.copy(title = inspection.title, previewText = "",
            faviconUrl = null, previewImageUrl = null,
            categoryLabels = inspection.labels, categoryInspection = inspection)
        val settings = StoryDisplaySettings.from(app.userSettings.story)
            .copy(previewImageMode = StoryPreviewMode.OFF, showPreviewText = false)
        var storyOpens = 0
        lateinit var listState: LazyListState
        try {
            compose.setContent {
                val palette = HarmonicThemeCatalog.scheme("dynamic", false)
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene)) {
                    HarmonicTheme(palette.colorScheme, palette.dark) {
                        listState = rememberLazyListState(initialFirstVisibleItemIndex = 12, initialFirstVisibleItemScrollOffset = 7)
                        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = listState) {
                            items((0 until 40).toList(), key = { it }) { index ->
                                if (index == 12) StoryRow(model,
                                    settings.toStoryRowStyle(StoryRowStyleContext(model.points, model.commentCount, false)),
                                    listItem = true, onLinkClick = { storyOpens++ }, onCommentClick = { storyOpens++ })
                                else Text("Retained story $index", Modifier.fillMaxWidth().padding(24.dp))
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            val originalIndex = listState.firstVisibleItemIndex
            val originalOffset = listState.firstVisibleItemScrollOffset
            compose.mainClock.autoAdvance = false
            // A physical pointer tap exercises competition with StoryRow's combinedClickable.
            compose.onNodeWithTag("category-label-ai").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(180)
            compose.onNodeWithTag("category-inspector-content", useUnmergedTree = true).assertExists()
            compose.runOnIdle { assertEquals(0, storyOpens) }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription("Similarity, 42 out of 100").assertExists()
            // The sheet initializes its anchors after the new Android window is attached.
            // Let that asynchronous layout and its show animation settle on the live test
            // clock; a single manual advance can run before those anchors exist.
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("category-inspector-edit-examples", useUnmergedTree = true).performClick()
            val examplesInfo = compose.onNodeWithContentDescription("About category examples")
            compose.waitUntil(5_000) { examplesInfo.isDisplayed() }
            examplesInfo.assertIsDisplayed()
            Espresso.pressBack()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("About category examples").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithTag("category-inspector-content", useUnmergedTree = true).assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Close category inspector").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("category-inspector-content", useUnmergedTree = true).assertExists()
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("category-inspector-content", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(0, storyOpens)
                assertEquals(originalIndex, listState.firstVisibleItemIndex)
                assertEquals(originalOffset, listState.firstVisibleItemScrollOffset)
            }
            compose.onNodeWithTag("category-label-ai").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
            scene.close()
        }
    }
}
