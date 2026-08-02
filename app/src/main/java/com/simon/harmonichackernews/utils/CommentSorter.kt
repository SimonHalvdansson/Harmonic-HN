package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.Comment
import java.util.Collections
import java.util.Comparator
import kotlin.math.max

object CommentSorter {
    fun sort(ctx: Context, comments: MutableList<Comment>) {
        sort(comments, SettingsUtils.getPreferredCommentSorting(ctx))
    }

    fun sort(comments: MutableList<Comment>, sortType: String) {
        when (sortType) {
            "Default" -> {
                if (isInDefaultOrder(comments)) {
                    return
                }
                sortComments(comments, object : Comparator<Comment> {
                    override fun compare(c1: Comment, c2: Comment): Int {
                        return Integer.compare(c1.sortOrder, c2.sortOrder)
                    }
                }, false)
            }

            "Reply count" -> sortComments(comments, object : Comparator<Comment> {
                override fun compare(c1: Comment, c2: Comment): Int {
                    return Integer.compare(c2.totalReplies, c1.totalReplies)
                }
            }, true)

            "Newest first" -> sortComments(comments, object : Comparator<Comment> {
                override fun compare(c1: Comment, c2: Comment): Int {
                    return Integer.compare(c2.time, c1.time)
                }
            }, false)

            "Oldest first" -> sortComments(comments, object : Comparator<Comment> {
                override fun compare(c1: Comment, c2: Comment): Int {
                    return Integer.compare(c1.time, c2.time)
                }
            }, false)
        }
    }

    private fun isInDefaultOrder(comments: MutableList<Comment>): Boolean {
        val size = comments.size
        if (size < 3) {
            return true
        }

        var previousSortOrder = comments.get(1).sortOrder
        for (i in 2..<size) {
            val currentSortOrder = comments.get(i).sortOrder
            if (previousSortOrder > currentSortOrder) {
                return false
            }
            previousSortOrder = currentSortOrder
        }
        return true
    }

    private fun sortComments(
        comments: MutableList<Comment>,
        comparator: Comparator<Comment>,
        updateReplyCounts: Boolean
    ) {
        if (comments.size <= 1) {
            return
        }

        val header: Comment? = comments.get(0)
        val commentsWithChildren = buildCommentTree(comments)

        if (updateReplyCounts) {
            updateTotalReplies(commentsWithChildren)
        }

        sortCommentsRecursive(commentsWithChildren, comparator)

        comments.clear()
        comments.add(header!!)
        flattenComments(commentsWithChildren, comments)
    }

    private fun sortCommentsRecursive(
        commentsWithChildren: MutableList<Comment>,
        comparator: Comparator<Comment>
    ) {
        commentsWithChildren.sortWith(comparator)

        for (c in commentsWithChildren) {
            CommentSorter.sortCommentsRecursive(c.childComments, comparator)
        }
    }


    private fun buildCommentTree(comments: MutableList<Comment>): MutableList<Comment> {
        val commentsWithChildren: MutableList<Comment> = ArrayList<Comment>()
        val parentsByDepth: MutableList<Comment?> = ArrayList<Comment?>()

        for (i in 1..<comments.size) {
            val comment = comments.get(i)
            comment.childComments = ArrayList()
            val depth = max(0, comment.depth)

            while (parentsByDepth.size > depth) {
                parentsByDepth.removeAt(parentsByDepth.size - 1)
            }

            if (depth == 0 || parentsByDepth.isEmpty()) {
                commentsWithChildren.add(comment)
            } else {
                parentsByDepth.get(parentsByDepth.size - 1)!!.childComments.add(comment)
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
            if (!comment.childComments.isEmpty()) {
                CommentSorter.flattenComments(comment.childComments, flatComments)
            }
        }
    }

    private fun updateTotalReplies(comments: MutableList<Comment>) {
        for (comment in comments) {
            updateTotalReplies(comment)
        }
    }

    private fun updateTotalReplies(comment: Comment): Int {
        var count = 0
        for (child in comment.childComments) {
            count += 1 + CommentSorter.updateTotalReplies(child)
        }
        comment.totalReplies = count
        return count
    }
}
