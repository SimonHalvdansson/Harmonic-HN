package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenSessionRegistryTest {
    @Test
    fun pendingPreviewActionsSurviveRuntimeOwnerReplacement() {
        val session = StoriesSessionState()
        assertTrue(session.beginPreviewAction(42, StoryPreviewActionKind.Vote))
        val replacementOwnerState = session.previewActionState

        assertEquals(setOf(42), replacementOwnerState.value.voteLoadingIds)
        assertFalse(session.beginPreviewAction(42, StoryPreviewActionKind.Vote))
        session.finishPreviewAction(42, StoryPreviewActionKind.Vote)

        assertTrue(replacementOwnerState.value.voteLoadingIds.isEmpty())
    }

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

    @Test
    fun commentScrollProgressRetentionIsLruBounded() {
        val registry = ScreenSessionRegistry(maxRetainedCommentScrollProgresses = 2)
        val first = registry.commentsStateFor(key = 1, storyId = 1).scrollProgress
        val second = registry.commentsStateFor(key = 2, storyId = 2).scrollProgress

        assertSame(first, registry.commentsStateFor(key = 3, storyId = 1).scrollProgress)
        registry.commentsStateFor(key = 4, storyId = 3)

        assertNotSame(second, registry.commentsStateFor(key = 5, storyId = 2).scrollProgress)
    }

    private class FakeAlgoliaRepository : AlgoliaRepository {
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> = emptyList()
        override suspend fun search(url: String): List<Story> = emptyList()
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }
}
