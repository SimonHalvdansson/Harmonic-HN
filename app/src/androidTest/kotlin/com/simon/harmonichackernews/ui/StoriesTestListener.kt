package com.simon.harmonichackernews.ui

import com.simon.harmonichackernews.presentation.*
import com.simon.harmonichackernews.ui.stories.StoriesScreenController

internal class StoriesTestListener(
    private val remove: (StoryListItemSnapshot) -> Unit = {},
) : StoriesScreenController.Listener {
    override fun onTypeSelected(index: Int) = Unit
    override fun onOpenSearch() = Unit
    override fun onCloseSearch() = Unit
    override fun onSearch(query: String) = Unit
    override fun onSearchOption(kind: StorySearchOption, index: Int) = Unit
    override fun onRefresh(showMainLoadingIndicator: Boolean) = Unit
    override fun onShowCached() = Unit
    override fun onLoadMore() = Unit
    override fun onSavedFilterSelected(filter: SavedItemFilter) = Unit
    override fun onShiftFrontDate(days: Int) = Unit
    override fun onPickFrontDate() = Unit
    override fun onFrontDateSelected(day: Long) = Unit
    override fun onMoreAction(action: StoriesMenuAction) = Unit
    override fun onCacheStoriesConfirmed(
        storyCount: Int,
        downloadWebViewContents: Boolean,
    ) = Unit
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
    override fun onVisibleStoryRange(firstVisibleIndex: Int, lastVisibleIndex: Int) = Unit
    override fun onStoryPreviewStopScroll() = Unit
    override fun onStoryPreviewVisibilityChanged(showing: Boolean) = Unit
    override fun onStoryPreviewNavigate(
        story: StoryListItemSnapshot,
        showWebsite: Boolean,
    ): Boolean = false
    override fun onStoryPreviewAction(
        story: StoryListItemSnapshot,
        action: StoryPreviewActionKind,
    ) = remove(story)
}
