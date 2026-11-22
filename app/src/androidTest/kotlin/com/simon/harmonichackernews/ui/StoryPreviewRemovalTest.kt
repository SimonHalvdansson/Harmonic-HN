package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.ui.stories.StoriesScreenController
import com.simon.harmonichackernews.ui.stories.StoryPreviewOverlay
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real preview pager with deterministic saved-list updates and no account writes. */
@RunWith(AndroidJUnit4::class)
class StoryPreviewRemovalTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var scene: HarmonicSceneComposition? = null
    private var previewScrimAlpha = 0f

    @After fun closeScene() { scene?.close() }

    @Test fun bookmarkRemovalPagesForwardAndPrunesTheOutgoingCard() = assertPaging(StoryPreviewActionKind.Bookmark, 2, 3)
    @Test fun favoriteRemovalPagesForwardAndPrunesTheOutgoingCard() = assertPaging(StoryPreviewActionKind.Favorite, 2, 3)
    @Test fun removingTheFinalPageMovesBackward() = assertPaging(StoryPreviewActionKind.Bookmark, 3, 2)
    @Test fun lastBookmarkFadesOutBeforeDismissing() = assertLastItem(StoryPreviewActionKind.Bookmark)
    @Test fun lastFavoriteFadesOutBeforeDismissing() = assertLastItem(StoryPreviewActionKind.Favorite)

    private fun assertPaging(action: StoryPreviewActionKind, openedId: Int, expectedId: Int) {
        val controller = showPreview(action, listOf(1, 2, 3), openedId)
        val originalTop = outgoingCardTop()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Remove $openedId").performClick()
            // Draw the first animation frame before advancing to the middle of the transition.
            // Android dispatches drawing separately from the Compose test clock.
            compose.mainClock.advanceTimeByFrame()
            compose.onRoot().captureToImage()
            compose.mainClock.advanceTimeBy(96)
            assertNotNull("Keep the outgoing card during paging", controller.storyPreviewRemovalRequest)
            val movingTop = outgoingCardTop()
            assertTrue(
                "The removed card must move toward the page edge (top moved from $originalTop to $movingTop)",
                if (expectedId > openedId) movingTop < originalTop - 10 else movingTop > originalTop + 10,
            )
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Story $expectedId").assertIsDisplayed()
            compose.onNodeWithText("Story $openedId").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(expectedId, controller.visibleStoryPreviewId)
                assertEquals(listOf(1, 2, 3) - openedId, controller.storyPreviewOverlay!!.stories.map { it.id })
                assertNull(controller.storyPreviewRemovalRequest)
            }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun outgoingCardTop(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = pixels.width / 2
        return (0 until pixels.height).firstOrNull { y ->
            val color = pixels[x, y]
            color.red > 0.9f && color.green < 0.1f && color.blue < 0.1f
        } ?: error("The outgoing red card is outside the viewport")
    }

    private fun assertLastItem(action: StoryPreviewActionKind) {
        val controller = showPreview(action, listOf(1), 1)
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Remove 1").performClick()
            compose.mainClock.advanceTimeBy(80)
            assertNotNull("Retain the card while it fades out", controller.storyPreviewOverlay)
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            assertTrue("The red card should be partway through its fade: $center", center.green in 0.05f..0.95f)
            assertTrue("The host's status-bar scrim must fade with the card", previewScrimAlpha in 0.01f..0.31f)
            compose.mainClock.advanceTimeBy(1_000)
            compose.runOnIdle { assertNull(controller.storyPreviewOverlay) }
            compose.onNodeWithText("Story 1").assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun showPreview(
        action: StoryPreviewActionKind,
        ids: List<Int>,
        openedId: Int,
    ): StoriesScreenController {
        var items = ids.map { id ->
            StoryListItemSnapshot(
                StorySnapshot(id = id, title = "Story $id"),
                StoryPresentationSnapshot(loaded = true, isLink = false),
            )
        }
        lateinit var controller: StoriesScreenController
        fun publish() {
            controller.updateContent(StoriesState(
                mainList = PortableStoryListState(items = items),
                currentType = if (action == StoryPreviewActionKind.Bookmark) StoryType.BOOKMARKS else StoryType.FAVORITES,
            ))
        }
        controller = StoriesScreenController.create(
            defaultStoryHeightPx = 100,
            savedItemState = object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = items.any { it.id == itemId }
                override fun isFavorited(itemId: Int) = items.any { it.id == itemId }
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            },
            listener = StoriesTestListener { story ->
                items = items.filterNot { it.id == story.id }
                publish()
            },
        )
        publish()
        controller.showStoryPreview(items, List(items.size) { 0xffff0000.toInt() }, openedId)
        val app = (compose.activity.application as HarmonicApplication).composition
        val testScene = app.createScene().also { scene = it }
        val dependencies = HarmonicUiDependencies(app, testScene)
        compose.setContent {
            CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                val palette = HarmonicThemeCatalog.resolve("light", false)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        StoryPreviewOverlay(
                            controller = controller,
                            tablet = false,
                            onScrimAlphaChanged = { previewScrimAlpha = it },
                        ) { story, page, _, modifier ->
                            Box(modifier.height(200.dp).background(if (story.id == openedId) Color.Red else Color.Blue)) {
                                BasicText(story.title.orEmpty(), Modifier.padding(16.dp))
                                Button(
                                    onClick = { controller.onStoryPreviewAction(page, action) },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                ) { BasicText("Remove ${story.id}") }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Story $openedId").assertIsDisplayed()
        return controller
    }

}
