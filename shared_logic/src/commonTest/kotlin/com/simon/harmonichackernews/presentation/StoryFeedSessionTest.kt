package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoryFeedSessionTest {
    @Test
    fun visibilityPolicyNormalizesFiltersAndHidesJobsOutsideJobsFeed() {
        val policy = StoryVisibilityPolicy(
            StoryVisibilityConfig(
                filteredWords = listOf("  Crypto  "),
                filteredDomains = listOf("Example.COM"),
                filteredUsers = setOf(" Spammer "),
                hideJobs = true,
            ),
        )

        assertTrue(policy.shouldHide(story(title = "Crypto news"), StoryType.TOP_STORIES))
        assertTrue(
            policy.shouldHide(
                story(title = "Ordinary story", url = "https://news.example.com/post"),
                StoryType.TOP_STORIES,
            ),
        )
        assertTrue(policy.shouldHide(story(title = "Post", by = "SPAMMER"), StoryType.TOP_STORIES))
        assertTrue(policy.shouldHide(story(title = "Hiring", isJob = true), StoryType.TOP_STORIES))
        assertFalse(policy.shouldHide(story(title = "Hiring", isJob = true), StoryType.HN_JOBS))
    }

    @Test
    fun aNewGenerationInvalidatesLoadsFromTheOldGeneration() {
        val session = StoryFeedLoadSession(staleLoadMillis = 1_000)
        val firstGeneration = session.beginGeneration()
        val startedAt = session.markStoryStarted(storyId = 1, nowMillis = 10)

        assertTrue(session.isCurrent(firstGeneration))
        assertTrue(session.isCurrentStoryLoad(1, startedAt))

        val nextGeneration = session.beginGeneration()

        assertFalse(session.isCurrent(firstGeneration))
        assertTrue(session.isCurrent(nextGeneration))
        assertFalse(session.isCurrentStoryLoad(1, startedAt))
    }

    @Test
    fun staleStoryLoadsCanBeRetried() {
        val session = StoryFeedLoadSession(staleLoadMillis = 1_000)
        session.beginGeneration()
        session.markStoryStarted(storyId = 7, nowMillis = 1_000)

        assertTrue(session.isStoryInProgress(7, nowMillis = 2_000))
        assertFalse(session.isStoryInProgress(7, nowMillis = 2_001))
        assertFalse(session.isStoryInProgress(7, nowMillis = 2_002))
    }

    @Test
    fun prefetchRangeExpandsPastTheViewportButHonoursPagination() {
        val planner = PreviewPrefetchPlanner(batchSize = 3, visibleThreshold = 4)

        assertEquals(5..11, planner.prefetchRange(20, 8, 5, 7, null))
        assertEquals(5..8, planner.prefetchRange(20, 8, 5, 7, 9))
        assertEquals(0..7, planner.prefetchRange(20, 8, -1, -1, null))
        assertNull(planner.prefetchRange(0, 8, 0, 0, null))
    }

    @Test
    fun prefetchPlannerBatchesLoadedStoriesInDisplayOrder() {
        val planner = PreviewPrefetchPlanner(batchSize = 2, visibleThreshold = 4)
        val stories = listOf(
            story(id = 1, loaded = true),
            story(id = 2),
            story(id = 3),
        )
        planner.begin(targetIndex = 2, enabled = true)

        assertEquals(listOf(stories[0]), planner.enqueue(stories[0], stories))
        stories[1].loaded = true
        assertEquals(listOf(stories[1]), planner.enqueue(stories[1], stories))
        assertTrue(planner.requestNextBatchSchedule())
        stories[2].loaded = true
        assertEquals(emptyList(), planner.enqueue(stories[2], stories))

        planner.startNextBatch()

        assertEquals(listOf(stories[2]), planner.drain(stories))
        assertTrue(planner.complete)
    }

    private fun story(
        id: Int = 1,
        title: String = "Story",
        by: String? = null,
        url: String? = null,
        isJob: Boolean = false,
        loaded: Boolean = false,
    ) = Story(title, id, loaded, false).also {
        it.by = by
        it.url = url
        it.isJob = isJob
    }
}
