package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.data.Comment
import kotlin.math.max

object CommentSorter {
    const val DEFAULT = "Default"
    const val REPLY_COUNT = "Reply count"
    const val NEWEST_FIRST = "Newest first"
    const val OLDEST_FIRST = "Oldest first"

    fun sort(comments: MutableList<Comment>, sortType: String) {
        when (sortType) {
            DEFAULT -> {
                if (isInDefaultOrder(comments)) return
                sortComments(comments, compareBy(Comment::sortOrder), false)
            }

            REPLY_COUNT -> sortComments(
                comments,
                compareByDescending(Comment::totalReplies),
                true,
            )

            NEWEST_FIRST -> sortComments(
                comments,
                compareByDescending(Comment::time),
                false,
            )

            OLDEST_FIRST -> sortComments(comments, compareBy(Comment::time), false)
        }
    }

    private fun isInDefaultOrder(comments: List<Comment>): Boolean =
        comments.size < 3 || comments.subList(1, comments.size).isSortedBy(Comment::sortOrder)

    private fun sortComments(
        comments: MutableList<Comment>,
        comparator: Comparator<Comment>,
        updateReplyCounts: Boolean,
    ) {
        if (comments.size <= 1) return

        val header = comments.first()
        val commentsWithChildren = buildCommentTree(comments)
        if (updateReplyCounts) updateTotalReplies(commentsWithChildren)
        sortCommentsRecursive(commentsWithChildren, comparator)

        comments.clear()
        comments.add(header)
        flattenComments(commentsWithChildren, comments)
    }

    private fun sortCommentsRecursive(
        commentsWithChildren: MutableList<Comment>,
        comparator: Comparator<Comment>,
    ) {
        commentsWithChildren.sortWith(comparator)
        commentsWithChildren.forEach { sortCommentsRecursive(it.childComments, comparator) }
    }

    private fun buildCommentTree(comments: List<Comment>): MutableList<Comment> {
        val roots = mutableListOf<Comment>()
        val parentsByDepth = mutableListOf<Comment>()
        for (index in 1..<comments.size) {
            val comment = comments[index]
            comment.childComments = mutableListOf()
            val depth = max(0, comment.depth)
            while (parentsByDepth.size > depth) parentsByDepth.removeAt(parentsByDepth.lastIndex)
            if (depth == 0 || parentsByDepth.isEmpty()) {
                roots.add(comment)
            } else {
                parentsByDepth.last().childComments.add(comment)
            }
            parentsByDepth.add(comment)
        }
        return roots
    }

    private fun flattenComments(comments: List<Comment>, destination: MutableList<Comment>) {
        comments.forEach { comment ->
            destination.add(comment)
            flattenComments(comment.childComments, destination)
        }
    }

    private fun updateTotalReplies(comments: List<Comment>) {
        comments.forEach(::updateTotalReplies)
    }

    private fun updateTotalReplies(comment: Comment): Int {
        val count = comment.childComments.sumOf { child -> 1 + updateTotalReplies(child) }
        comment.totalReplies = count
        return count
    }
}
