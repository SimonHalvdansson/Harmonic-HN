package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption
import com.simon.harmonichackernews.network.StoryResourceTintKind

/** Common UI-to-feature binding. Hosts implement only operations that need platform facilities. */
class StoriesFeatureListener(
    private val feature: StoriesFeatureRuntime,
    private val platform: PlatformCallbacks,
) : StoriesComposeController.Listener {
    override fun onTypeSelected(index: Int) {
        val type = feature.storyTypeAt(index)
        if (type == StoryType.UNKNOWN || type == feature.currentType) return
        feature.selectTypeAndRefresh(type)
        activeListChanged(searching = false)
    }

    override fun onOpenSearch() {
        feature.openSearch()
        activeListChanged(searching = true)
    }

    override fun onCloseSearch() {
        feature.closeSearch()
        activeListChanged(searching = false)
    }

    override fun onSearch(query: String) = feature.submitSearch(query)
    override fun onSearchOption(kind: StorySearchOption, index: Int) =
        feature.selectSearchOption(kind, index)
    override fun onToggleOnlyClicked() = feature.toggleOnlyClicked()
    override fun onRefresh() = feature.refresh(false)
    override fun onShowCached() = feature.showCachedStories()
    override fun onLoadMore() = feature.loadMore()
    override fun onSavedFilterSelected(filter: SavedItemFilter) = feature.selectSavedFilter(filter)

    override fun onShiftFrontDate(days: Int) = feature.shiftFrontPageDay(days)
    override fun onPickFrontDate() = platform.showFrontDatePicker()
    override fun onFrontDateSelected(day: Long) = feature.selectFrontPageDay(day)
    override fun onMoreAction(action: StoriesMenuAction) = feature.menu(action)
    override fun onCacheStoriesConfirmed(storyCount: Int) = feature.requestStoryCache(storyCount)
    override fun onLinkClick(story: Story) = feature.selectStoryLink(story)
    override fun onCommentClick(story: Story) = feature.selectStoryComments(story)
    override fun onCommentStoryClick(story: Story) = feature.selectCommentStory(story)
    override fun onCommentRepliesClick(story: Story) = feature.selectStoryComments(story)
    override fun onStoryLongClick(story: Story, tintBaseColorArgb: Int) =
        story.takeIf { it in feature.activeStories }
            ?.let { feature.previewDeck(it.id, tintBaseColorArgb) }
    override fun onStoryPreviewImageLoaded(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
    ) {
        feature.completePreviewImageLoad(storyId, pageUrl, imageUrl, success = true)
    }

    override fun onStoryPreviewImageLoadFailed(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
    ) {
        feature.completePreviewImageLoad(storyId, pageUrl, imageUrl, success = false)
    }

    override fun onStoryTintExtracted(
        story: Story,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
        favicon: Boolean,
    ) {
        feature.recordStoryResourceTint(
            story = story,
            kind = if (favicon) StoryResourceTintKind.FAVICON
            else StoryResourceTintKind.PREVIEW_IMAGE,
            sourceUrl = sourceUrl,
            baseColorArgb = baseColorArgb,
            paletteConfigKey = paletteConfigKey,
            tintColorArgb = tintColorArgb,
        )
    }

    override fun onVisibleStoryRange(lastVisibleIndex: Int) {
        feature.loadVisibleStories(lastVisibleIndex)
        feature.prefetchVisibleStoryResources(lastVisibleIndex)
    }

    override fun onStoryPreviewStopScroll() = Unit
    override fun onStoryPreviewVisibilityChanged(showing: Boolean) =
        platform.onStoryPreviewVisibilityChanged(showing)

    override fun onStoryPreviewNavigate(
        story: Story,
        showWebsite: Boolean,
    ): Boolean {
        feature.openStory(story, showWebsite)
        return platform.isSplitLayout()
    }

    override fun onStoryPreviewAction(
        story: Story,
        action: StoryPreviewActionKind,
    ) = feature.previewAction(story, action)

    private fun activeListChanged(searching: Boolean) {
        if (!searching) feature.refreshBookmarksIfNeeded(platform.hostStarted)
        platform.onSearchStateChanged(searching)
    }

    interface PlatformCallbacks {
        val hostStarted: Boolean
        fun onSearchStateChanged(searching: Boolean)
        fun showFrontDatePicker()
        fun onStoryPreviewVisibilityChanged(showing: Boolean)
        fun isSplitLayout(): Boolean
    }
}
