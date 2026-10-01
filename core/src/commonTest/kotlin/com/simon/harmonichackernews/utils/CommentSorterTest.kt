package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.CommentListDiff
import com.simon.harmonichackernews.data.Comment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CommentSorterTest {
    @Test fun childListsRemainIndependentMutableAndReplaceable() {
        val first = Comment()
        val second = Comment()
        val child = Comment()
        val children = first.childComments
        children.add(child)
        assertSame(children, first.childComments)
        assertEquals(emptyList(), second.childComments)
        val replacement = mutableListOf(second)
        first.childComments = replacement
        assertSame(replacement, first.childComments)
        CommentListDiff.updateExistingComment(child, first)
        assertSame(replacement, child.childComments)
    }

    @Test fun switchingSortOrderRebuildsChildrenAndKeepsTiesStable() {
        fun comment(id: Int, depth: Int, time: Int) = Comment().apply {
            this.id = id; this.depth = depth; this.time = time; sortOrder = id
        }
        val comments = mutableListOf(comment(0, -1, 0), comment(1, 0, 5),
            comment(2, 1, 10), comment(3, 1, 10), comment(4, 0, 20))
        CommentSorter.sort(comments, CommentSorter.NEWEST_FIRST)
        assertEquals(listOf(0, 4, 1, 2, 3), comments.map(Comment::id))
        CommentSorter.sort(comments, CommentSorter.REPLY_COUNT)
        assertEquals(listOf(0, 1, 2, 3, 4), comments.map(Comment::id))
        assertEquals(listOf(2, 3), comments[1].childComments.map(Comment::id))
        assertEquals(listOf(0, 2, 0, 0, 0), comments.map(Comment::totalReplies))
        repeat(3) { CommentSorter.sort(comments, CommentSorter.OLDEST_FIRST) }
        assertEquals(listOf(0, 1, 2, 3, 4), comments.map(Comment::id))
        assertEquals(listOf(2, 3), comments[1].childComments.map(Comment::id))
    }
}
