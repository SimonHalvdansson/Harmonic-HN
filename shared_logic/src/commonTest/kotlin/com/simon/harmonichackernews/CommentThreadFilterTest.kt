package com.simon.harmonichackernews

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentThreadFilterTest {
    @Test
    fun nestedAndSeparateOpSubtreesKeepAncestorsAndDescendantsInSourceOrder() {
        val story = Story("Story", 99, true, false).also { it.by = "op" }
        val header = comment(0, 0, 0, null)
        val comments = listOf(
            header,
            comment(1, -1, 0, "someone"),
            comment(2, 1, 1, "op"),
            comment(3, 2, 2, "someone"),
            comment(4, 3, 3, "op"),
            comment(5, -1, 0, "someone"),
            comment(6, 5, 1, "op"),
            comment(7, 6, 2, "someone"),
            comment(8, -1, 0, "someone"),
        )

        assertEquals(
            listOf(0, 1, 2, 3, 4, 5, 6, 7),
            CommentThreadFilter.buildCommentsByOpThreadList(story, comments).map(Comment::id),
        )
    }

    private fun comment(id: Int, parent: Int, depth: Int, author: String?) = Comment().also {
        it.id = id
        it.parent = parent
        it.depth = depth
        it.by = author
    }
}
