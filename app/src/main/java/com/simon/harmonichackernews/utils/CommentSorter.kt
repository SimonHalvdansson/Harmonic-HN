package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.Comment
import kotlin.math.max

object CommentSorter {
    fun sort(ctx: Context, comments: MutableList<Comment>) {
        sort(comments, SettingsUtils.getPreferredCommentSorting(ctx))
    }

    fun sort(comments: MutableList<Comment>, sortType: String) {
        when (sortType) {
            "Default" -> {
                if (isInDefaultOrder(comments)) return
                sortComments(comments, compareBy(Comment::sortOrder), false)
            }

            "Reply count" -> sortComments(
                comments,
                compareByDescending(Comment::totalReplies),
                true,
            )

            "Newest first" -> sortComments(
                comments,
                compareByDescending(Comment::time),
                false,
            )

            "Oldest first" -> sortComments(comments, compareBy(Comment::time), false)
        }
    }

    private fun isInDefaultOrder(comments: List<Comment>): Boolean =
        comments.size < 3 || comments.subList(1, comments.size).isSortedBy(Comment::sortOrder)

    private fun sortComments(
        comments: MutableList<Comment>,
        comparator: Comparator<Comment>,
        updateReplyCounts: Boolean
    ) {
        if (comments.size <= 1) return

        val header = comments.first()
        val commentsWithChildren = buildCommentTree(comments)

        if (updateReplyCounts) {
            updateTotalReplies(commentsWithChildren)
        }

        sortCommentsRecursive(commentsWithChildren, comparator)

        comments.clear()
        comments.add(header)
        flattenComments(commentsWithChildren, comments)
    }

    private fun sortCommentsRecursive(
        commentsWithChildren: MutableList<Comment>,
        comparator: Comparator<Comment>
    ) {
        commentsWithChildren.sortWith(comparator)

        for (comment in commentsWithChildren) {
            sortCommentsRecursive(comment.childComments, comparator)
        }
    }

    private fun buildCommentTree(comments: List<Comment>): MutableList<Comment> {
        val commentsWithChildren = mutableListOf<Comment>()
        val parentsByDepth = mutableListOf<Comment>()

        for (i in 1..<comments.size) {
            val comment = comments[i]
            comment.childComments = mutableListOf()
            val depth = max(0, comment.depth)

            while (parentsByDepth.size > depth) {
                parentsByDepth.removeAt(parentsByDepth.lastIndex)
            }

            if (depth == 0 || parentsByDepth.isEmpty()) {
                commentsWithChildren.add(comment)
            } else {
                parentsByDepth.last().childComments.add(comment)
            }

            parentsByDepth.add(comment)
        }

        return commentsWithChildren
    }

    private fun flattenComments(
        comments: MutableList<Comment>,
        flatComments: MutableList<Comment>
    ) {
        for (comment in comments) {
            flatComments.add(comment)
            if (comment.childComments.isNotEmpty()) {
                flattenComments(comment.childComments, flatComments)
            }
        }
    }

    private fun updateTotalReplies(comments: MutableList<Comment>) {
        for (comment in comments) {
            updateTotalReplies(comment)
        }
    }

    private fun updateTotalReplies(comment: Comment): Int {
        val count = comment.childComments.sumOf { child -> 1 + updateTotalReplies(child) }
        comment.totalReplies = count
        return count
    }
}
