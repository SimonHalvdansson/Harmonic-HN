package com.simon.harmonichackernews.debug

import com.simon.harmonichackernews.data.InMemoryStoryCacheFileStore
import com.simon.harmonichackernews.data.InMemoryStoryCacheMetadataStore
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DebugCachedPostFixtureTest {
    @Test
    fun fixtureSeedsAHydratableStoryAndNestedCommentThread() = runTest {
        val repository = StoryCacheRepository(
            files = InMemoryStoryCacheFileStore(),
            metadata = InMemoryStoryCacheMetadataStore(),
        )

        assertTrue(DebugCachedPostFixture.seed(repository, cachedAtMillis = 1_000L))

        val story = Story().apply { id = DebugCachedPostFixture.storyId }
        assertTrue(repository.hydrateStory(story))
        assertEquals("A cached post for offline UI testing", story.title)
        assertEquals(123, story.score)

        val parsed = AlgoliaCommentsParser(
            parsingDispatcher = UnconfinedTestDispatcher(testScheduler),
        ).parse(repository.loadStoryPayload(DebugCachedPostFixture.storyId))
        assertEquals(DebugCachedPostFixture.storyId, parsed.id)
        assertEquals(6, parsed.comments.size)
        assertEquals(listOf(0, 1, 0, 1, 1, 0), parsed.comments.map { it.depth })
    }
}
