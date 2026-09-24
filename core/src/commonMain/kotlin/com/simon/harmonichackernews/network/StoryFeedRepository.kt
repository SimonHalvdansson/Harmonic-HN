package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface StoryFeedResult {
    data class ItemIds(val ids: List<Int>) : StoryFeedResult
    data class Scraped(val page: HackerNewsListPage) : StoryFeedResult
    data class LinkDirectory(val stories: List<Story>) : StoryFeedResult
}

interface StoryFeedLoader {
    suspend fun load(storyType: StoryType, frontDay: String? = null): StoryFeedResult
    suspend fun loadNextScrapedPage(
        storyType: StoryType,
        nextPageUrl: String,
    ): HackerNewsListPage
}

/** Chooses the API, RSS or scraped HN source for a main-list story type. */
class StoryFeedRepository(
    private val hackerNewsRepository: HackerNewsRepository,
    private val webRepository: HackerNewsWebRepository,
    private val unslopRepository: UnslopRepository,
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : StoryFeedLoader {
    override suspend fun load(
        storyType: StoryType,
        frontDay: String?,
    ): StoryFeedResult = withContext(requestDispatcher) { loadFeed(storyType, frontDay) }

    // Transport initialization can suspend. Keep the subsequent request pipeline and parsing
    // off the caller's UI dispatcher too, so the first composition cannot delay the index request.
    private suspend fun loadFeed(storyType: StoryType, frontDay: String?): StoryFeedResult = when {
        storyType == StoryType.UNSLOP -> StoryFeedResult.ItemIds(unslopRepository.getStoryIds())
        storyType.isFrontpageLinkList ->
            StoryFeedResult.LinkDirectory(webRepository.getListDirectory())
        storyType.isScrapedFrontpage -> StoryFeedResult.Scraped(
            webRepository.getStoryList(
                path = requireNotNull(storyType.hackerNewsPath) {
                    "Missing Hacker News path for ${storyType.label}"
                },
                commentsPage = storyType.usesCommentRows(),
                day = frontDay,
            )
        )
        else -> StoryFeedResult.ItemIds(hackerNewsRepository.getStoryIds(storyType))
    }

    override suspend fun loadNextScrapedPage(
        storyType: StoryType,
        nextPageUrl: String,
    ): HackerNewsListPage = withContext(requestDispatcher) {
        webRepository.getStoryListPage(nextPageUrl, storyType.usesCommentRows())
    }
}
