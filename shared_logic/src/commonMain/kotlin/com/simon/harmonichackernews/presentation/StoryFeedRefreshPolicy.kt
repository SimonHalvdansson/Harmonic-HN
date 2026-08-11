package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.network.HttpStatusException

enum class StoryFeedSource {
    SEARCH,
    ALGOLIA,
    BOOKMARKS,
    USER_ITEMS,
    HISTORY,
    FRONTPAGE_LINKS,
    SCRAPED_FRONTPAGE,
    HACKER_NEWS_API,
}

data class StoryFeedRefreshPlan(
    val source: StoryFeedSource,
    val showRefreshIndicator: Boolean,
    val clearItems: Boolean,
    val loadCachedUserItems: Boolean,
    val recordRefreshTime: Boolean,
)

/** Portable routing and freshness policy for story-feed refreshes. */
object StoryFeedRefreshPolicy {
    const val STALE_AFTER_MILLIS: Long = 60L * 60L * 1_000L

    fun plan(
        searching: Boolean,
        storyType: StoryType,
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean,
        listIsEmpty: Boolean,
    ): StoryFeedRefreshPlan {
        val source = when {
            searching -> StoryFeedSource.SEARCH
            storyType.isAlgolia -> StoryFeedSource.ALGOLIA
            storyType.isBookmarks -> StoryFeedSource.BOOKMARKS
            storyType.isUserItemList -> StoryFeedSource.USER_ITEMS
            storyType.isHistory -> StoryFeedSource.HISTORY
            storyType.isFrontpageLinkList -> StoryFeedSource.FRONTPAGE_LINKS
            storyType.isScrapedFrontpage -> StoryFeedSource.SCRAPED_FRONTPAGE
            else -> StoryFeedSource.HACKER_NEWS_API
        }
        return StoryFeedRefreshPlan(
            source = source,
            showRefreshIndicator = showSwipeRefreshIndicator && !showMainLoadingIndicator,
            clearItems = showMainLoadingIndicator,
            loadCachedUserItems = source == StoryFeedSource.USER_ITEMS &&
                (showMainLoadingIndicator || listIsEmpty),
            recordRefreshTime = source !in setOf(
                StoryFeedSource.SEARCH,
                StoryFeedSource.ALGOLIA,
            ),
        )
    }

    fun shouldRefreshRestoredState(
        failure: StoryLoadFailure?,
        listIsEmpty: Boolean,
        searching: Boolean,
        searchQuery: String,
        storyType: StoryType,
    ): Boolean {
        if (failure != null || !listIsEmpty) return false
        if (searching) return searchQuery.isNotEmpty()
        return storyType !in setOf(
            StoryType.BOOKMARKS,
            StoryType.HISTORY,
            StoryType.FAVORITES,
            StoryType.UPVOTED,
        )
    }

    fun shouldShowUpdateAffordance(
        nowMillis: Long,
        lastLoadedMillis: Long,
        alwaysShow: Boolean,
        searching: Boolean,
        storyType: StoryType,
    ): Boolean {
        if (alwaysShow) return true
        val refreshableSource = !searching &&
            !storyType.isBookmarks &&
            !storyType.isUserItemList &&
            !storyType.isAlgolia
        return refreshableSource && nowMillis - lastLoadedMillis > STALE_AFTER_MILLIS
    }

    fun failureFor(error: Throwable): StoryLoadFailure = when {
        error is HttpStatusException && error.statusCode == 404 -> StoryLoadFailure.NOT_FOUND
        error is HttpStatusException && error.statusCode == 429 -> StoryLoadFailure.RATE_LIMITED
        else -> StoryLoadFailure.GENERAL
    }
}
