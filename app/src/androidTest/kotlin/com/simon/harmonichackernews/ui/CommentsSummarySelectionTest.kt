@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.comments.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentsSummarySelectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectedSummaryKeepsSheetExpandedAndClearingSelectionRestoresSwipes() {
        val controller = showSummary()
        val summary = compose.onNodeWithText(summaryText)
        summary.performTouchInput { longClick(Offset(80f, 20f)) }
        compose.runOnIdle {
            assertTrue(controller.summarySelectionState.selectedTexts.isNotEmpty())
        }
        dragDown(summary)
        compose.runOnIdle {
            assertEquals("Selection drags must not move the comments sheet", 1f, controller.sheetSlideOffset, 0.001f)
        }

        compose.runOnIdle { controller.summarySelectionState.clear() }
        dragDown(summary)
        compose.runOnIdle {
            assertTrue("Normal sheet dragging must resume after clearing selection", controller.sheetSlideOffset < 0.99f)
        }
    }

    @Test
    fun longPressDragAcrossMultipleLinesKeepsSelectionAndSheetPosition() {
        val controller = showSummary()
        val summary = compose.onNodeWithText(summaryText)
        summary.performTouchInput {
            down(Offset(80f, 20f))
            advanceEventTime(800)
            moveTo(Offset(160f, height - 10f), delayMillis = 600)
            up()
        }
        compose.runOnIdle {
            assertEquals(1f, controller.sheetSlideOffset, 0.001f)
            val selected = controller.summarySelectionState.selectedTexts.joinToString { it.text }
            assertTrue("Dragging should select more than a word: $selected", selected.length > 30)
        }
    }

    private fun showSummary(): CommentsScreenController {
        val story = StoryListItemSnapshot(
            StorySnapshot(42, title = "Summary selection fixture", url = "https://example.org"),
            StoryPresentationSnapshot(loaded = true, aiSummaryText = summaryText, summaryGeneratedSuccessfully = true),
        )
        val controller = CommentsScreenController.create(
            shouldSmoothScroll = { true }, story = story, showWebsite = false, accountUser = null,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = NoOpListener(),
        )
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        )
        controller.updateContent(CommentsScreenState(story = story, integratedWebView = true))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CommentsScaffold(controller, reserveUpButtonInset = false, retainedContent = {
                    LazyColumn(Modifier.fillMaxSize().testTag("comments")) {
                        item {
                            Column {
                                Text("Article header", Modifier.height(120.dp))
                                StoryAiSummary(
                                    story, settings, onOpenLink = {},
                                    selectionState = controller.summarySelectionState,
                                )
                            }
                        }
                        items(30) { Text("Comment $it", Modifier.height(80.dp)) }
                    }
                })
            }
        }
        compose.waitForIdle()
        return controller
    }

    private fun dragDown(summary: SemanticsNodeInteraction) {
        val bounds = summary.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("comments").performTouchInput {
            val start = Offset(bounds.center.x, bounds.top + 20f)
            swipe(start, start + Offset(0f, 300f), durationMillis = 600)
        }
    }

    private val summaryText = "Small improvements make familiar workflows easier to use. " +
        "Convenience needs to be balanced with reliability and control. " +
        "Try the approach with a real task and check where it breaks down."

    private class NoOpListener : CommentsScreenController.Listener {
        override fun onToggleComment(comment: PortableCommentItem, position: Int) = Unit
        override fun onCommentAction(comment: PortableCommentItem, action: CommentMenuAction) = Unit
        override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) = Unit
        override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) = Unit
        override fun onHeaderClick() = Unit
        override fun onHeaderPreviewImageResult(imageUrl: String, success: Boolean) = Unit
        override fun onHeaderPreviewTintExtracted(sourceUrl: String, baseColorArgb: Int, paletteConfigKey: String, tintColorArgb: Int): Int? = null
        override fun onHeaderAction(action: CommentsHeaderAction) = Unit
        override fun onShareAction(action: CommentsShareAction) = Unit
        override fun onMoreAction(action: CommentsMoreAction) = Unit
        override fun onSearchResultSelected(comment: PortableCommentItem) = Unit
        override fun onSearchQueryChanged(query: String) = Unit
        override fun onSortComments(sortType: String) = Unit
        override fun onSheetAction(action: CommentsSheetAction) = Unit
        override fun onCollapseSheetForWebsite() = Unit
        override fun onSheetProgressChanged(expandedFraction: Float) = Unit
        override fun onSheetSettled(expanded: Boolean) = Unit
        override fun onHeaderColorChanged(color: Int) = Unit
        override fun onHeaderCoverageChanged(coverage: Float) = Unit
        override fun onPollOption(optionId: Int) = Unit
    }
}
