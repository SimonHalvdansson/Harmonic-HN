package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ScreenSessionRegistryTest {
    @Test
    fun commentsSessionChangesWithNavigationKeyAndRetainsPerStoryScrollProgress() {
        val registry = ScreenSessionRegistry()

        val first = registry.commentsStateFor(key = 1, storyId = 42)
        first.scrollProgress.topCommentId = 7
        assertSame(first, registry.commentsStateFor(key = 1, storyId = 99))

        val replacement = registry.commentsStateFor(key = 2, storyId = 42)

        assertNotSame(first, replacement)
        assertSame(first.scrollProgress, replacement.scrollProgress)
    }

    @Test
    fun submissionsSessionChangesWithNavigationKeyOrUsername() {
        val registry = ScreenSessionRegistry()
        val repository = FakeAlgoliaRepository()

        val first = registry.submissionsStateFor(1, "alice", repository)
        assertSame(first, registry.submissionsStateFor(1, "alice", repository))
        val replacement = registry.submissionsStateFor(2, "alice", repository)
        assertNotSame(first, replacement)

        val secondUser = registry.submissionsStateFor(2, "bob", repository)
        assertNotSame(replacement, secondUser)
    }

    @Test
    fun storiesSessionIsStableForTheRegistryLifetime() {
        val registry = ScreenSessionRegistry()

        registry.stories.lastSearch = "kmp"

        assertSame(registry.stories, registry.stories)
        kotlin.test.assertEquals("kmp", registry.stories.lastSearch)
    }

    private class FakeAlgoliaRepository : AlgoliaRepository {
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> = emptyList()
        override suspend fun search(url: String): List<Story> = emptyList()
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }
}
