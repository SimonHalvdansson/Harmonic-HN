package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommentThreadRepositoryOrderingTest {
    @Test
    fun seedlessAlgoliaLoadUsesOfficialTopLevelOrder() = runTest {
        val algolia = object : AlgoliaRepository {
            override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
                error("Not used")

            override suspend fun search(url: String): List<Story> = error("Not used")

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
