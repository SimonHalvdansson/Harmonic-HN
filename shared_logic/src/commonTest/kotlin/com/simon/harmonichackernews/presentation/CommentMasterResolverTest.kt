package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentMasterResolverTest {
    @Test
    fun commentParentIsLoadedAndMergedIntoTheSourceRow() = runTest {
        val loadedParent = Story("Parent", 42, true, false).also { it.score = 9 }
        val repository = FakeRepository(loadedParent)
        val source = Story("Comment", 7, true, false).also {
            it.isComment = true
            it.commentMasterId = 42
        }

        val resolved = CommentMasterResolver(repository).resolve(source)

        assertEquals(42, resolved.id)
        assertEquals("Parent", resolved.title)
        assertEquals(9, resolved.score)
        assertTrue(resolved.loaded)
        assertEquals(listOf(42), repository.requestedIds)
    }

    private class FakeRepository(private val story: Story) : HackerNewsRepository {
        val requestedIds = mutableListOf<Int>()

        override suspend fun getStory(id: Int): Story? {
            requestedIds += id
            return story
        }

        override suspend fun getComment(id: Int): Comment? = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }
}
