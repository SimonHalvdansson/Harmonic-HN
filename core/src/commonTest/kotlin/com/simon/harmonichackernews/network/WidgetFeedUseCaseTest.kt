package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest

class WidgetFeedUseCaseTest {
    @Test
    fun loadsUpToVisibleCountAndKeepsPartialSuccesses() = runTest {
        val repository = FakeRepository(
            ids = listOf(1, 2, 3, 4),
            stories = mapOf(1 to story(1), 3 to story(3), 4 to story(4)),
        )

        val result = WidgetFeedUseCase(repository).load(
            WidgetFeedRequest(
                storyType = StoryType.TOP_STORIES,
                fetchCount = 4,
                visibleCount = 2,
            ),
        )

        val loaded = assertIs<WidgetFeedResult.Loaded>(result)
        assertEquals(listOf(1, 3), loaded.stories.map(Story::id))
        assertEquals(4, loaded.availableStoryCount)
        assertEquals(1, loaded.failedStoryCount)
        assertFalse(loaded.timedOut)
    }

    @Test
    fun reportsFailureWhenNoStoryCanBeLoaded() = runTest {
        val result = WidgetFeedUseCase(
            FakeRepository(ids = listOf(1), stories = emptyMap()),
        ).load(
            WidgetFeedRequest(
                storyType = StoryType.NEW_STORIES,
                fetchCount = 1,
                visibleCount = 1,
            ),
        )

        assertIs<WidgetFeedResult.Failed>(result)
    }

    @Test
    fun mapsPersistedWidgetUrlsToTypedFeeds() {
        assertEquals(
            StoryType.BEST_STORIES,
            widgetStoryTypeForUrl(StoryType.BEST_STORIES.hackerNewsUrl),
        )
        assertEquals(StoryType.TOP_STORIES, widgetStoryTypeForUrl("legacy-or-invalid"))
    }

    private class FakeRepository(
        private val ids: List<Int>,
        private val stories: Map<Int, Story>,
    ) : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = stories[id]

        override suspend fun getComment(id: Int): Comment? = null

        override suspend fun getStoryIds(type: StoryType): List<Int> = ids
    }

    private companion object {
        fun story(id: Int) = Story().apply {
            this.id = id
            title = "Story $id"
            loaded = true
        }
    }
}
