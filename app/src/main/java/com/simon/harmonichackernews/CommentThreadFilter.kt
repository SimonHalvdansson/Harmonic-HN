package com.simon.harmonichackernews

import android.text.TextUtils
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.List
import java.util.Map
import java.util.Set

internal object CommentThreadFilter {
    fun hasCommentsByOp(story: Story?, sourceComments: MutableList<Comment>?): Boolean {
        if (story == null || TextUtils.isEmpty(story.by) || sourceComments == null) {
            return false
        }

        for (i in 1..<sourceComments.size) {
            if (TextUtils.equals(story.by, sourceComments[i].by)) {
                return true
            }
        }
        return false
    }

    fun buildCommentsByOpThreadList(
        story: Story?,
        sourceComments: MutableList<Comment>?
    ): MutableList<Comment> {
        val filteredComments: MutableList<Comment> = ArrayList()
        if (story == null || TextUtils.isEmpty(story.by) || sourceComments == null || sourceComments.isEmpty()) {
            return filteredComments
        }

        filteredComments.add(sourceComments.get(0))

        val commentsById: MutableMap<Int, Comment> = HashMap()
        for (i in 1..<sourceComments.size) {
            val comment = sourceComments.get(i)
            commentsById.put(comment.id, comment)
        }

        val includedCommentIds: MutableSet<Int> = HashSet()
        for (i in 1..<sourceComments.size) {
            val comment = sourceComments.get(i)
            if (!TextUtils.equals(story.by, comment.by)) {
                continue
            }

            includeCommentAndAncestors(
                comment,
                commentsById,
                includedCommentIds,
                sourceComments.size
            )
            val opCommentDepth = comment.depth
            for (j in i + 1..<sourceComments.size) {
                val candidate = sourceComments.get(j)
                if (candidate.depth <= opCommentDepth) {
                    break
                }
                includedCommentIds.add(candidate.id)
            }
        }

        for (i in 1..<sourceComments.size) {
            val comment = sourceComments.get(i)
            if (includedCommentIds.contains(comment.id)) {
                filteredComments.add(comment)
            }
        }
        return filteredComments
    }

    private fun includeCommentAndAncestors(
        comment: Comment?, commentsById: MutableMap<Int, Comment>,
        includedCommentIds: MutableSet<Int>, maxDepth: Int
    ) {
        var current = comment
        var guard = 0
        while (current != null && guard++ < maxDepth) {
            includedCommentIds.add(current.id)
            current = commentsById.get(current.parent)
        }
    }
}
