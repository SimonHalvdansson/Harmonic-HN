package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class CommentThreadRepositoryOrderingTest {
    @Test
    fun cancelledTransportCancelsTheCoordinatorInsteadOfLeavingItWaiting() = runTest {
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also { it.kids = intArrayOf(1) }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getComment(id: Int): Comment = throw CancellationException("Request cancelled")
        }
        assertFailsWith<CancellationException> {
            OfficialCommentThreadLoader(repository).load(42, emptySet(), false)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun wideForestKeepsOnlyFixedWorkersAndCancellationReleasesEveryRequest() = runTest {
        var active = 0
        var requested = 0
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also { it.kids = IntArray(2000) { it + 1 } }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getComment(id: Int): Comment? {
                active++
                requested++
                try { awaitCancellation() } finally { active-- }
            }
        }
        val loading = async { OfficialCommentThreadLoader(repository).load(42, emptySet(), false) }
        runCurrent()
        fun descendants(job: Job): Int = job.children.sumOf { 1 + descendants(it) }
        assertEquals(8, active)
        assertTrue(descendants(loading) <= 10, "Queued IDs must not retain their own coroutines")
        loading.cancelAndJoin()
        assertEquals(0, active)
        assertEquals(8, requested)
    }

    @Test
    fun simultaneousForestsShareTheRequestLimit() = runTest {
        var active = 0
        var peak = 0
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also { it.kids = IntArray(16) { it + 1 } }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getComment(id: Int): Comment {
                active++
                peak = maxOf(peak, active)
                try { delay(10) } finally { active-- }
                return Comment().also { it.id = id; it.by = "reader" }
            }
        }
        val loader = OfficialCommentThreadLoader(repository)
        val first = async { loader.load(42, emptySet(), false) }
        val second = async { loader.load(43, emptySet(), false) }
        assertEquals((1..16).toList(), assertIs<CommentThreadLoadResult.Official>(first.await()).comments.map { it.id })
        assertEquals((1..16).toList(), assertIs<CommentThreadLoadResult.Official>(second.await()).comments.map { it.id })
        assertEquals(8, peak)
        assertEquals(0, active)
    }

    @Test
    fun officialForestSkipsFailedAndFilteredBranchesWithoutReorderingSiblings() = runTest {
        val requested = mutableListOf<Int>()
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also { it.kids = intArrayOf(1, 2, 3, 4) }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getComment(id: Int): Comment? {
                requested.add(id)
                if (id == 2) error("Unavailable")
                return Comment().also {
                    it.id = id
                    it.by = if (id == 3) "BLOCKED" else "reader"
                    it.kidsIds = if (id < 5) intArrayOf(id + 10) else intArrayOf()
                }
            }
        }
        val result = assertIs<CommentThreadLoadResult.Official>(
            OfficialCommentThreadLoader(repository).load(42, setOf("blocked"), true),
        )
        assertEquals(listOf(1, 11, 4, 14), result.comments.map { it.id })
        assertEquals(listOf(0, 1, 0, 1), result.comments.map { it.depth })
        assertEquals(setOf(1, 2, 3, 4, 11, 14), requested.toSet())
        assertEquals(true, result.usedAsFallback)
    }

    @Test
    fun officialDeepBranchFlattensInOrder() = runTest {
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also { it.kids = intArrayOf(1) }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getComment(id: Int) = Comment().also {
                it.id = id; it.by = "reader"
                it.kidsIds = if (id < 500) intArrayOf(id + 1) else intArrayOf()
            }
        }
        val result = assertIs<CommentThreadLoadResult.Official>(
            OfficialCommentThreadLoader(repository).load(42, emptySet(), false),
        )
        assertEquals((1..500).toList(), result.comments.map { it.id })
        assertEquals((0..499).toList(), result.comments.map { it.depth })
    }

    @Test
    fun officialForestBoundsRequestsAndRetainsDepthFirstOrder() = runTest {
        var active = 0
        var peak = 0
        val repository = object : HackerNewsRepository {
            override suspend fun getStory(id: Int) = Story().also {
                it.id = id
                it.kids = IntArray(16) { it + 1 }
            }
            override suspend fun getComment(id: Int): Comment {
                active++
                peak = maxOf(peak, active)
                try { delay(if (id % 2 == 0) 150 else 50) } finally { active-- }
                return Comment().also {
                    it.id = id
                    it.by = "author"
                    it.kidsIds = if (id < 100) intArrayOf(id + 100) else intArrayOf()
                }
            }
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
        }
        val result = assertIs<CommentThreadLoadResult.Official>(
            OfficialCommentThreadLoader(repository).load(42, emptySet(), false),
        )
        assertEquals(8, peak)
        assertEquals((1..16).flatMap { listOf(it, it + 100) }, result.comments.map { it.id })
        assertEquals((1..16).flatMap { listOf(0, 1) }, result.comments.map { it.depth })
    }

    @Test
    fun seedlessAlgoliaLoadUsesOfficialTopLevelOrder() = runTest {
        val algolia = object : AlgoliaRepository {
            override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage =
                error("Not used")

            override suspend fun search(url: String): AlgoliaSearchPage = error("Not used")

            override suspend fun getItemJson(id: Int): String =
                """
                {
                  "id": 49554643,
                  "children": [
                    {"id": 1, "parent_id": 49554643, "author": "one", "text": "One"},
                    {"id": 2, "parent_id": 49554643, "author": "two", "text": "Two"},
                    {"id": 3, "parent_id": 49554643, "author": "three", "text": "Three"}
                  ]
                }
                """.trimIndent()
        }
        val hackerNews = object : HackerNewsRepository {
            override suspend fun getStory(id: Int): Story = Story().also {
                it.id = id
                it.kids = intArrayOf(3, 1, 2)
            }

            override suspend fun getComment(id: Int): Comment? = error("Not used")
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
        }
        val repository = CommentThreadRepository(algolia, hackerNews)

        val result = assertIs<CommentThreadLoadResult.Algolia>(
            repository.load(storyId = 49554643, useAlgolia = true),
        )

        assertEquals(listOf(3, 1, 2), result.parsed.comments.map(Comment::id))
    }
}
