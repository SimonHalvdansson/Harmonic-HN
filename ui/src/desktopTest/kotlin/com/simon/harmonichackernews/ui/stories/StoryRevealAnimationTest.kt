package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryRevealAnimationTest {
    @Test
    fun firstRevealHasIntermediateAlphaButRecycledRowsStartFullyVisible() = SwingUtilities.invokeAndWait {
        val controller = controller()
        assertFalse(controller.hasRevealedStory(42))
        val present = mutableStateOf(true)
        val storyId = mutableStateOf(42)
        var alpha: State<Float>? = null
        var compositions = 0
        val scene = ImageComposeScene(100, 100) {
            if (present.value) key(storyId.value) {
                val rowAlpha = rememberStoryRevealAlpha(storyId.value, controller)
                alpha = rowAlpha
                SideEffect { compositions++ }
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = rowAlpha.value)) })
            }
        }
        var time = 0L
        fun frame(): Float {
            scene.render(time.also { time += 16_666_667L }).close()
            return alpha!!.value
        }
        try {
            // Scene construction may already run effects. Check the unmarked precondition
            // before construction, then observe the fade while the session flag is marked.
            val first = List(30) { frame() to controller.hasRevealedStory(42) }
            assertTrue(controller.hasRevealedStory(42))
            assertEquals(0f, first.first().first)
            assertTrue(first.any { (value, seen) -> seen && value > 0f && value < 1f },
                "Marking the story must not cut its first fade short: $first")
            assertEquals(1f, first.last().first)
            assertTrue(compositions <= 3, "Reveal frames must not recompose the row: $compositions")

            // Leaving and re-entering composition is what lazy item recycling does on scroll-back.
            present.value = false
            repeat(3) { frame() }
            present.value = true
            repeat(30) { assertEquals(1f, frame(), "A recycled seen row must not replay its reveal") }

            storyId.value = 43
            val next = List(30) { frame() }
            assertTrue(controller.hasRevealedStory(43))
            assertEquals(0f, next.first())
            assertTrue(next.any { it > 0f && it < 1f })
            assertEquals(1f, next.last())
        } finally { scene.close() }
    }

    @Test
    fun newControllerUsesItsSessionFlagsAndClearedFlagsApplyAfterRowReentry() = SwingUtilities.invokeAndWait {
        val seen = mutableSetOf(42)
        val current = mutableStateOf(controller(seen))
        val present = mutableStateOf(true)
        var alpha: State<Float>? = null
        val scene = ImageComposeScene(100, 100) {
            if (present.value) key(current.value) {
                val rowAlpha = rememberStoryRevealAlpha(42, current.value)
                alpha = rowAlpha
                Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = rowAlpha.value)) })
            }
        }
        var time = 0L
        fun frame(): Float {
            scene.render(time.also { time += 16_666_667L }).close()
            return alpha!!.value
        }
        try {
            repeat(4) { assertEquals(1f, frame()) }
            current.value = controller(seen)
            repeat(4) { assertEquals(1f, frame(), "Recreated controllers in the same session retain seen rows") }
            current.value = controller()
            val freshSession = List(30) { frame() }
            assertEquals(0f, freshSession.first())
            assertTrue(freshSession.any { it > 0f && it < 1f })
            assertEquals(1f, freshSession.last())

            current.value = controller(seen)
            repeat(4) { assertEquals(1f, frame()) }
            present.value = false
            repeat(3) { frame() }
            seen.clear()
            present.value = true
            val reset = List(30) { frame() }
            assertEquals(0f, reset.first())
            assertTrue(reset.any { it > 0f && it < 1f })
            assertEquals(1f, reset.last())
        } finally { scene.close() }
    }

    private fun controller(seen: MutableSet<Int> = mutableSetOf()) = StoriesScreenController.create(
        defaultStoryHeightPx = 100,
        savedItemState = object : SavedItemStateReader {
            override fun isBookmarked(itemId: Int) = false
            override fun isFavorited(itemId: Int) = false
            override fun isUpvoted(itemId: Int, isComment: Boolean) = false
        },
        listener = NoOpListener,
        revealedStoryIds = seen,
    )

    private object NoOpListener : StoriesScreenController.Listener {
        override fun onTypeSelected(index: Int) = Unit
        override fun onOpenSearch() = Unit
        override fun onCloseSearch() = Unit
        override fun onSearch(query: String) = Unit
        override fun onSearchOption(kind: StorySearchOption, index: Int) = Unit
        override fun onToggleOnlyRead() = Unit
        override fun onRefresh(showMainLoadingIndicator: Boolean) = Unit
        override fun onShowCached() = Unit
        override fun onLoadMore() = Unit
        override fun onSavedFilterSelected(filter: SavedItemFilter) = Unit
        override fun onShiftFrontDate(days: Int) = Unit
        override fun onPickFrontDate() = Unit
        override fun onFrontDateSelected(day: Long) = Unit
        override fun onMoreAction(action: StoriesMenuAction) = Unit
        override fun onCacheStoriesConfirmed(storyCount: Int, downloadWebViewContents: Boolean) = Unit
        override fun onLinkClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentStoryClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentRepliesClick(story: StoryListItemSnapshot) = Unit
        override fun onStoryLongClick(story: StoryListItemSnapshot, tintBaseColorArgb: Int) = null
        override fun onStoryPreviewImageLoaded(storyId: Int, pageUrl: String, imageUrl: String) = Unit
        override fun onStoryPreviewImageLoadFailed(storyId: Int, pageUrl: String, imageUrl: String) = Unit
        override fun onStoryTintExtracted(story: StoryListItemSnapshot, sourceUrl: String, baseColorArgb: Int,
            paletteConfigKey: String, tintColorArgb: Int, favicon: Boolean) = Unit
        override fun onVisibleStoryRange(firstVisibleIndex: Int, lastVisibleIndex: Int) = Unit
        override fun onStoryPreviewStopScroll() = Unit
        override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
        override fun onStoryPreviewNavigate(story: StoryListItemSnapshot, showWebsite: Boolean) = false
        override fun onStoryPreviewAction(story: StoryListItemSnapshot, action: StoryPreviewActionKind) = Unit
    }
}
