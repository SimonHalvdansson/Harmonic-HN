package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.StoryFeedResult

data class StoryFeedApplication(
    val applied: Boolean,
    val loadVisibleStories: Boolean,
    val loadedStories: List<Story> = emptyList(),
)

/**
 * Lifecycle-independent owner for applying feed pages to a story list.
 *
 * Network transport belongs to [StoriesPresenter], while this runtime owns placeholder creation,
 * structural list changes, scraped-page state and pagination visibility. A platform shell only
 * hydrates cached rows and starts the platform image/cache work for newly visible items.
 */
class StoryFeedRuntime(
    private val sessionState: StoriesSessionState,
    private val clickedStoryIds: () -> Set<Int>,
    private val shouldHideClickedStories: () -> Boolean,
    private val hydrateCachedStory: (Story) -> Boolean,
    private val shouldHideHydratedStory: (Story) -> Boolean,
) {
    private var scrapedStoryType: StoryType = StoryType.UNKNOWN
    private var nextPageLoading = false

    fun applyInitial(
        store: StoryListStore,
        storyType: StoryType,
        result: StoryFeedResult,
    ): StoryFeedApplication {
        store.setShowingCached(false)
        return when (result) {
            is StoryFeedResult.ItemIds -> {
                val stories = placeholders(result.ids)
                store.replace(stories)
                StoryFeedApplication(true, loadVisibleStories = true, stories)
            }

            is StoryFeedResult.Scraped -> {
                scrapedStoryType = storyType
                nextPageLoading = false
                sessionState.scrapedFrontpageNextPageUrl = result.page.nextPageUrl
                if (result.page.itemIds.isEmpty()) {
                    store.fail(StoryLoadFailure.GENERAL)
                    StoryFeedApplication(false, loadVisibleStories = false)
                } else {
                    val stories = placeholders(result.page.itemIds, result.page.commentIds.toSet())
                    store.replace(
                        stories,
                        canLoadMore = !result.page.nextPageUrl.isNullOrEmpty(),
                    )
                    StoryFeedApplication(true, loadVisibleStories = true, stories)
                }
            }

            is StoryFeedResult.LinkDirectory -> {
                if (result.stories.isEmpty()) {
                    store.fail(StoryLoadFailure.GENERAL)
                    StoryFeedApplication(false, loadVisibleStories = false)
                } else {
                    store.replace(result.stories)
                    store.markLoadedThrough(store.stories.lastIndex)
                    StoryFeedApplication(true, loadVisibleStories = false, result.stories)
                }
            }
        }
    }

    fun beginNextScrapedPage(store: StoryListStore, storyType: StoryType): String? {
        val nextPageUrl = sessionState.scrapedFrontpageNextPageUrl
        if (nextPageLoading || storyType != scrapedStoryType || nextPageUrl.isNullOrEmpty()) {
            return null
        }
        nextPageLoading = true
        store.beginLoadMore()
        return nextPageUrl
    }

    fun restoreScrapedPagination(storyType: StoryType?) {
        nextPageLoading = false
        scrapedStoryType = storyType ?: StoryType.UNKNOWN
    }

    fun applyNextScrapedPage(
        store: StoryListStore,
        storyType: StoryType,
        page: HackerNewsListPage,
    ): StoryFeedApplication {
        if (storyType != scrapedStoryType) return StoryFeedApplication(false, false)
        nextPageLoading = false
        sessionState.scrapedFrontpageNextPageUrl = page.nextPageUrl
        val newStories = StoryPlaceholderFactory.createNew(
            existingStories = store.stories,
            itemIds = page.itemIds,
            commentIds = page.commentIds.toSet(),
            clickedIds = clickedStoryIds(),
            hideClicked = shouldHideClickedStories(),
            hydrateCachedStory = hydrateCachedStory,
            shouldHideHydratedStory = shouldHideHydratedStory,
        )
        store.mutateStories { addAll(newStories) }
        val canLoadMore = !page.nextPageUrl.isNullOrEmpty()
        store.setCanLoadMore(canLoadMore)
        store.finishLoadMore(canLoadMore)
        if (store.state.value.paginationEnabled && newStories.isNotEmpty()) {
            store.setVisibleStoryCount(
                (store.state.value.visibleStoryCount + newStories.size).coerceAtMost(
                    store.stories.size,
                ),
            )
        }
        return StoryFeedApplication(true, loadVisibleStories = true, newStories)
    }

    fun failNextScrapedPage(store: StoryListStore, storyType: StoryType): Boolean {
        if (storyType != scrapedStoryType) return false
        nextPageLoading = false
        store.finishLoadMore(canLoadMore = true)
        return true
    }

    fun resetScrapedPagination(store: StoryListStore) {
        sessionState.scrapedFrontpageNextPageUrl = null
        nextPageLoading = false
        scrapedStoryType = StoryType.UNKNOWN
        store.finishLoadMore(store.state.value.canLoadMore)
    }

    private fun placeholders(
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
    ): MutableList<Story> = StoryPlaceholderFactory.create(
        itemIds = itemIds,
        commentIds = commentIds,
        clickedIds = clickedStoryIds(),
        hideClicked = shouldHideClickedStories(),
        hydrateCachedStory = hydrateCachedStory,
        shouldHideHydratedStory = shouldHideHydratedStory,
    )
}
