package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.StoryFeedResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoryFeedRuntimeTest {
    @Test
    fun refreshReordersFeedWithoutDiscardingLoadedStoryObjects() {
        val runtime = runtime(StoriesSessionState())
        val store = StoryListStore()
        val retained = Story("Loaded details", 2, true, false)
        store.replace(listOf(Story("Old", 1, true, false), retained))

        val application = runtime.applyInitial(
            store,
            StoryType.TOP_STORIES,
            StoryFeedResult.ItemIds(listOf(3, 2, 4)),
        )

        assertTrue(application.applied)
        assertEquals(listOf(3, 2, 4), store.stories.map(Story::id))
        assertSame(retained, store.stories[1])
        assertEquals("Loaded details", store.stories[1].title)
        assertTrue(store.stories[1].loaded)
    }

    @Test
    fun appliesAndExtendsScrapedFeedWithoutDuplicatingIds() {
        val session = StoriesSessionState()
        val runtime = runtime(session)
        val store = StoryListStore()
        store.setPaginationEnabled(true)

        val initial = runtime.applyInitial(
            store,
            StoryType.BEST_STORIES,
            StoryFeedResult.Scraped(
                HackerNewsListPage(
                    itemIds = listOf(1, 2),
                    commentIds = listOf(2),
                    nextPageUrl = "page-2",
                ),
            ),
        )

        assertTrue(initial.applied)
        assertEquals(listOf(1, 2), store.stories.map(Story::id))
        assertTrue(store.stories[1].isComment)
        assertEquals("page-2", runtime.beginNextScrapedPage(store, StoryType.BEST_STORIES))

        val next = runtime.applyNextScrapedPage(
            store,
            StoryType.BEST_STORIES,
            HackerNewsListPage(
                itemIds = listOf(2, 3),
                commentIds = emptyList(),
                nextPageUrl = null,
            ),
        )

        assertTrue(next.applied)
        assertEquals(listOf(1, 2, 3), store.stories.map(Story::id))
        assertFalse(store.state.value.canLoadMore)
        assertEquals(null, session.scrapedFrontpageNextPageUrl)
    }

    private fun runtime(session: StoriesSessionState) = StoryFeedRuntime(
        sessionState = session,
        clickedStoryIds = { emptySet() },
        shouldHideClickedStories = { false },
        hydrateCachedStory = { false },
        shouldHideHydratedStory = { false },
    )
}
