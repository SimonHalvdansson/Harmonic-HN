package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StoriesComposeControllerPreviewNavigationTest {
    @Test
    fun commentsNavigationKeepsPreviewReadyForBack() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = false)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(0, controller.storyPreviewDismissRequest)
    }

    @Test
    fun commentsNavigationDismissesPreviewWhenStoriesRemainVisible() {
        val controller = controller(destinationRemainsBesideStories = true)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = false)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(1, controller.storyPreviewDismissRequest)
    }

    @Test
    fun websiteNavigationStillDismissesPreviewInSinglePane() {
        val controller = controller(destinationRemainsBesideStories = false)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = true)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(1, controller.storyPreviewDismissRequest)
    }

    @Test
    fun websiteNavigationKeepsPreviewInSplitLayout() {
        val controller = controller(destinationRemainsBesideStories = true)

        controller.onStoryPreviewNavigate(page = 0, showWebsite = true)

        assertNotNull(controller.storyPreviewOverlay)
        assertEquals(0, controller.storyPreviewDismissRequest)
    }

    private fun controller(destinationRemainsBesideStories: Boolean): StoriesComposeController =
        StoriesComposeController.create(
            defaultStoryHeightPx = 100,
            savedItemState = EmptySavedItemState,
            listener = TestListener(destinationRemainsBesideStories),
        ).also { controller ->
            controller.showStoryPreview(
                stories = listOf(
                    StoryListItemSnapshot(
                        story = StorySnapshot(id = 1, title = "Story"),
                        presentation = StoryPresentationSnapshot(loaded = true),
                    ),
                ),
                cardColors = intArrayOf(0),
                openedStoryId = 1,
            )
        }

    private data object EmptySavedItemState : SavedItemStateReader {
        override fun isBookmarked(itemId: Int): Boolean = false
        override fun isFavorited(itemId: Int): Boolean = false
        override fun isUpvoted(itemId: Int, isComment: Boolean): Boolean = false
    }

    private class TestListener(
        private val destinationRemainsBesideStories: Boolean,
    ) : StoriesComposeController.Listener {
        override fun onTypeSelected(index: Int) = Unit
        override fun onOpenSearch() = Unit
        override fun onCloseSearch() = Unit
        override fun onSearch(query: String) = Unit
        override fun onSearchOption(kind: StorySearchOption, index: Int) = Unit
        override fun onToggleOnlyClicked() = Unit
        override fun onRefresh() = Unit
        override fun onShowCached() = Unit
        override fun onLoadMore() = Unit
        override fun onSavedFilterSelected(filter: SavedItemFilter) = Unit
        override fun onShiftFrontDate(days: Int) = Unit
        override fun onPickFrontDate() = Unit
        override fun onFrontDateSelected(day: Long) = Unit
        override fun onMoreAction(action: StoriesMenuAction) = Unit
        override fun onCacheStoriesConfirmed(storyCount: Int) = Unit
        override fun onLinkClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentStoryClick(story: StoryListItemSnapshot) = Unit
        override fun onCommentRepliesClick(story: StoryListItemSnapshot) = Unit
        override fun onStoryLongClick(
            story: StoryListItemSnapshot,
            tintBaseColorArgb: Int,
        ) = null
        override fun onStoryPreviewImageLoaded(storyId: Int, pageUrl: String, imageUrl: String) = Unit
        override fun onStoryPreviewImageLoadFailed(
            storyId: Int,
            pageUrl: String,
            imageUrl: String,
        ) = Unit
        override fun onStoryTintExtracted(
            story: StoryListItemSnapshot,
            sourceUrl: String,
            baseColorArgb: Int,
            paletteConfigKey: String,
            tintColorArgb: Int,
            favicon: Boolean,
        ) = Unit
        override fun onVisibleStoryRange(lastVisibleIndex: Int) = Unit
        override fun onStoryPreviewStopScroll() = Unit
        override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
        override fun onStoryPreviewNavigate(
            story: StoryListItemSnapshot,
            showWebsite: Boolean,
        ): Boolean = destinationRemainsBesideStories
        override fun onStoryPreviewAction(
            story: StoryListItemSnapshot,
            action: StoryPreviewActionKind,
        ) = Unit
    }
}
