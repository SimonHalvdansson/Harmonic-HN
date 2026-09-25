package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommentThreadRepositoryOrderingTest {
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
