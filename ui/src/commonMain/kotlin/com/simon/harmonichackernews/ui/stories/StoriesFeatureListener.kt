package com.simon.harmonichackernews.ui.stories

import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.StoriesIntent
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoriesStore
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption

/** Common UI-to-feature binding. Hosts implement only operations that need platform facilities. */
class StoriesFeatureListener(
    private val store: StoriesStore,
    private val platform: PlatformCallbacks,
) : StoriesComposeController.Listener {
    override fun onTypeSelected(index: Int) {
        store.accept(StoriesIntent.SelectType(index))
        activeListChanged(searching = false)
    }

    override fun onOpenSearch() {
        store.accept(StoriesIntent.OpenSearch)
        activeListChanged(searching = true)
    }

    override fun onCloseSearch() {
        store.accept(StoriesIntent.CloseSearch)
        activeListChanged(searching = false)
    }

    override fun onSearch(query: String) = store.accept(StoriesIntent.Search(query))
    override fun onSearchOption(kind: StorySearchOption, index: Int) =
        store.accept(StoriesIntent.SelectSearchOption(kind, index))
    override fun onToggleOnlyClicked() = store.accept(StoriesIntent.ToggleOnlyClicked)
    override fun onRefresh(showMainLoadingIndicator: Boolean) = store.accept(
        StoriesIntent.Refresh(showMainLoadingIndicator),
    )
    override fun onShowCached() = store.accept(StoriesIntent.ShowCached)
    override fun onLoadMore() = store.accept(StoriesIntent.LoadMore)
    override fun onSavedFilterSelected(filter: SavedItemFilter) =
        store.accept(StoriesIntent.SelectSavedFilter(filter))

    override fun onShiftFrontDate(days: Int) = store.accept(StoriesIntent.ShiftFrontDate(days))
    override fun onPickFrontDate() = platform.showFrontDatePicker()
    override fun onFrontDateSelected(day: Long) =
        store.accept(StoriesIntent.SelectFrontDate(day))
    override fun onMoreAction(action: StoriesMenuAction) =
        store.accept(StoriesIntent.More(action))
    override fun onCacheStoriesConfirmed(storyCount: Int) =
        store.accept(StoriesIntent.CacheStories(storyCount))
    override fun onLinkClick(story: StoryListItemSnapshot) =
        store.accept(StoriesIntent.OpenLink(story.id))
    override fun onCommentClick(story: StoryListItemSnapshot) =
        store.accept(StoriesIntent.OpenComments(story.id))
    override fun onCommentStoryClick(story: StoryListItemSnapshot) =
        store.accept(StoriesIntent.OpenCommentStory(story.id))
    override fun onCommentRepliesClick(story: StoryListItemSnapshot) =
        store.accept(StoriesIntent.OpenComments(story.id))
    override fun onStoryLongClick(
        story: StoryListItemSnapshot,
        tintBaseColorArgb: Int,
    ) = store.previewDeck(story.id, tintBaseColorArgb)
    override fun onStoryPreviewImageLoaded(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
    ) {
        store.accept(StoriesIntent.CompletePreviewImage(storyId, pageUrl, imageUrl, true))
    }

    override fun onStoryPreviewImageLoadFailed(
        storyId: Int,
        pageUrl: String,
        imageUrl: String,
    ) {
        store.accept(StoriesIntent.CompletePreviewImage(storyId, pageUrl, imageUrl, false))
    }

    override fun onStoryTintExtracted(
        story: StoryListItemSnapshot,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
        tintColorArgb: Int,
        favicon: Boolean,
    ) {
        store.accept(
            StoriesIntent.RecordTint(
                story.id,
                sourceUrl,
                baseColorArgb,
                paletteConfigKey,
                tintColorArgb,
                favicon,
            ),
        )
    }

    override fun onVisibleStoryRange(lastVisibleIndex: Int) {
        store.accept(StoriesIntent.VisibleRange(lastVisibleIndex))
    }

    override fun onStoryPreviewStopScroll() = Unit
    override fun onStoryPreviewVisibilityChanged(showing: Boolean) =
        platform.onStoryPreviewVisibilityChanged(showing)

    override fun onStoryPreviewNavigate(
        story: StoryListItemSnapshot,
        showWebsite: Boolean,
    ): Boolean {
        store.accept(StoriesIntent.OpenPreviewStory(story.id, showWebsite))
        return platform.isSplitLayout()
    }

    override fun onStoryPreviewAction(
        story: StoryListItemSnapshot,
        action: StoryPreviewActionKind,
    ) {
        store.accept(StoriesIntent.PreviewAction(story.id, action))
    }

    private fun activeListChanged(searching: Boolean) {
        platform.onSearchStateChanged(searching)
    }

    interface PlatformCallbacks {
        fun onSearchStateChanged(searching: Boolean)
        fun showFrontDatePicker()
        fun onStoryPreviewVisibilityChanged(showing: Boolean)
        fun isSplitLayout(): Boolean
    }
}
