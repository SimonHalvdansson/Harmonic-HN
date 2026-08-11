package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story

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

/** Chooses the API or scraped HN source for a main-list story type. */
class StoryFeedRepository(
    private val hackerNewsRepository: HackerNewsRepository,
    private val webRepository: HackerNewsWebRepository,
) : StoryFeedLoader {
    override suspend fun load(
        storyType: StoryType,
        frontDay: String?,
    ): StoryFeedResult = when {
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
    ): HackerNewsListPage = webRepository.getStoryListPage(
        nextPageUrl,
        storyType.usesCommentRows(),
    )
}
