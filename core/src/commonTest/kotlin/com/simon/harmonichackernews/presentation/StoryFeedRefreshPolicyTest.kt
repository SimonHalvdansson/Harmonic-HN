package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.HttpStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryFeedRefreshPolicyTest {
    @Test
    fun everyStoryTypeRoutesToItsPortableFeedSource() {
        assertEquals(StoryFeedSource.ALGOLIA, plan(StoryType.LAST_24_HOURS).source)
        assertEquals(StoryFeedSource.BOOKMARKS, plan(StoryType.BOOKMARKS).source)
        assertEquals(StoryFeedSource.USER_ITEMS, plan(StoryType.FAVORITES).source)
        assertEquals(StoryFeedSource.USER_ITEMS, plan(StoryType.UPVOTED).source)
        assertEquals(StoryFeedSource.HISTORY, plan(StoryType.HISTORY).source)
        assertEquals(StoryFeedSource.SCRAPED_FRONTPAGE, plan(StoryType.FRONT).source)
        assertEquals(StoryFeedSource.SCRAPED_FRONTPAGE, plan(StoryType.CLASSIC).source)
        assertEquals(StoryFeedSource.HACKER_NEWS_API, plan(StoryType.TOP_STORIES).source)
        assertEquals(
            StoryFeedSource.SEARCH,
            plan(StoryType.TOP_STORIES, searching = true).source,
        )
    }

    @Test
    fun refreshPresentationAndCacheDecisionsArePartOfThePlan() {
        val plan = StoryFeedRefreshPolicy.plan(
            searching = false,
            storyType = StoryType.FAVORITES,
            showSwipeRefreshIndicator = true,
            showMainLoadingIndicator = true,
            listIsEmpty = false,
        )

        assertFalse(plan.showRefreshIndicator)
        assertTrue(plan.clearItems)
        assertTrue(plan.loadCachedUserItems)
        assertTrue(plan.recordRefreshTime)
    }

    @Test
    fun restoredEmptyNetworkFeedsRefreshButLocalEmptyFeedsDoNot() {
        assertTrue(
            StoryFeedRefreshPolicy.shouldRefreshRestoredState(
                failure = null,
                listIsEmpty = true,
                searching = false,
                searchQuery = "",
                storyType = StoryType.TOP_STORIES,
            ),
        )
        assertFalse(
            StoryFeedRefreshPolicy.shouldRefreshRestoredState(
                failure = null,
                listIsEmpty = true,
                searching = false,
                searchQuery = "",
                storyType = StoryType.HISTORY,
            ),
        )
        assertFalse(
            StoryFeedRefreshPolicy.shouldRefreshRestoredState(
                failure = StoryLoadFailure.GENERAL,
                listIsEmpty = true,
                searching = false,
                searchQuery = "",
                storyType = StoryType.TOP_STORIES,
            ),
        )
    }

    @Test
    fun updateAffordanceAppearsOnlyForStaleRefreshableFeedsUnlessForced() {
        val staleNow = StoryFeedRefreshPolicy.STALE_AFTER_MILLIS + 1

        assertTrue(updateVisible(staleNow, StoryType.TOP_STORIES))
        assertFalse(updateVisible(StoryFeedRefreshPolicy.STALE_AFTER_MILLIS, StoryType.TOP_STORIES))
        assertFalse(updateVisible(staleNow, StoryType.BOOKMARKS))
        assertFalse(updateVisible(staleNow, StoryType.LAST_WEEK))
        assertFalse(updateVisible(staleNow, StoryType.TOP_STORIES, searching = true))
        assertTrue(
            StoryFeedRefreshPolicy.shouldShowUpdateAffordance(
                nowMillis = 0,
                lastLoadedMillis = 0,
                alwaysShow = true,
                searching = true,
                storyType = StoryType.BOOKMARKS,
            ),
        )
    }

    @Test
    fun networkFailuresMapToSharedPresentationFailures() {
        assertEquals(
            StoryLoadFailure.NOT_FOUND,
            StoryFeedRefreshPolicy.failureFor(HttpStatusException(404, "Not found", "url")),
        )
        assertEquals(
            StoryLoadFailure.RATE_LIMITED,
            StoryFeedRefreshPolicy.failureFor(HttpStatusException(429, "Limited", "url")),
        )
        assertEquals(
            StoryLoadFailure.GENERAL,
            StoryFeedRefreshPolicy.failureFor(IllegalStateException("broken")),
        )
    }

    private fun plan(type: StoryType, searching: Boolean = false) =
        StoryFeedRefreshPolicy.plan(
            searching = searching,
            storyType = type,
            showSwipeRefreshIndicator = false,
            showMainLoadingIndicator = false,
            listIsEmpty = false,
        )

    private fun updateVisible(
        nowMillis: Long,
        type: StoryType,
        searching: Boolean = false,
    ) = StoryFeedRefreshPolicy.shouldShowUpdateAffordance(
        nowMillis = nowMillis,
        lastLoadedMillis = 0,
        alwaysShow = false,
        searching = searching,
        storyType = type,
    )
}
