package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption

/** Common UI-to-feature binding. Hosts implement only operations that need platform facilities. */
class StoriesFeatureListener(
    private val feature: StoriesFeatureRuntime,
    private val platform: PlatformCallbacks,
) : StoriesComposeController.Listener {
    override fun onTypeSelected(index: Int) {
        val type = platform.storyTypeAt(index)
        if (type == StoryType.UNKNOWN || type == feature.currentType) return
        feature.selectTypeAndRefresh(type)
        platform.onActiveListChanged(searching = false)
    }

    override fun onOpenSearch() {
        feature.openSearch()
        platform.onActiveListChanged(searching = true)
    }

    override fun onCloseSearch() {
        feature.closeSearch()
        platform.onActiveListChanged(searching = false)
    }

    override fun onSearch(query: String) = feature.submitSearch(query)
    override fun onSearchOption(kind: StorySearchOption, index: Int) =
        feature.selectSearchOption(kind, index)
    override fun onToggleOnlyClicked() = feature.toggleOnlyClicked()
    override fun onRefresh() = feature.refresh(false)
    override fun onShowCached() = platform.showCachedStories()
    override fun onLoadMore() = feature.loadMore()
    override fun onSavedFilterSelected(filter: SavedItemFilter) = feature.selectSavedFilter(filter)

    override fun onShiftFrontDate(days: Int) = feature.shiftFrontPageDay(days)
    override fun onPickFrontDate() = platform.showFrontDatePicker()
    override fun onFrontDateSelected(day: Long) = feature.selectFrontPageDay(day)
    override fun onMoreAction(action: StoriesMenuAction) = platform.onMoreAction(action)
    override fun onCacheStoriesConfirmed(storyCount: Int) = platform.cacheStories(storyCount)
    override fun onLinkClick(story: Story) = feature.selectStoryLink(story)
    override fun onCommentClick(story: Story) = feature.selectStoryComments(story)
    override fun onCommentStoryClick(story: Story) = feature.selectCommentStory(story)
    override fun onCommentRepliesClick(story: Story) = feature.selectStoryComments(story)
    override fun onStoryLongClick(story: Story) = platform.showStoryPreview(story)

    override fun onVisibleStoryRange(lastVisibleIndex: Int) {
        feature.loadVisibleStories(lastVisibleIndex)
        platform.onVisibleStoryRange(lastVisibleIndex)
    }

    override fun onStoryPreviewStopScroll() = Unit
    override fun onStoryPreviewVisibilityChanged(showing: Boolean) =
        platform.onStoryPreviewVisibilityChanged(showing)

    override fun onStoryPreviewNavigate(
        story: Story,
        position: Int,
        showWebsite: Boolean,
    ): Boolean = platform.onStoryPreviewNavigate(story, position, showWebsite)

    override fun onStoryPreviewAction(
        story: Story,
        position: Int,
        action: StoryPreviewActionKind,
    ) = platform.onStoryPreviewAction(story, position, action)

    interface PlatformCallbacks {
        fun storyTypeAt(index: Int): StoryType
        fun onActiveListChanged(searching: Boolean)
        fun showCachedStories()
        fun showFrontDatePicker()
        fun onMoreAction(action: StoriesMenuAction)
        fun cacheStories(storyCount: Int)
        fun showStoryPreview(story: Story)
        fun onVisibleStoryRange(lastVisibleIndex: Int)
        fun onStoryPreviewVisibilityChanged(showing: Boolean)
        fun onStoryPreviewNavigate(story: Story, position: Int, showWebsite: Boolean): Boolean
        fun onStoryPreviewAction(
            story: Story,
            position: Int,
            action: StoryPreviewActionKind,
        )
    }
}
