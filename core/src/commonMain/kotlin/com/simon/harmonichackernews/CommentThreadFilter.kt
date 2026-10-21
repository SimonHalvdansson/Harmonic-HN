package com.simon.harmonichackernews

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story

object CommentThreadFilter {
    fun hasCommentsByOp(story: Story?, sourceComments: List<Comment>?): Boolean =
        hasCommentsBy(story?.by, sourceComments)

    fun buildCommentsByOpThreadList(
        story: Story?,
        sourceComments: List<Comment>?
    ): MutableList<Comment> = buildThreadListFor(story?.by, sourceComments)

    /** Whether [author] wrote any comment after the header at index 0. */
    fun hasCommentsBy(author: String?, sourceComments: List<Comment>?): Boolean {
        if (author.isNullOrEmpty() || sourceComments == null) {
            return false
        }

        for (i in 1..<sourceComments.size) {
            if (author == sourceComments[i].by) {
                return true
            }
        }
        return false
    }

    /** Keeps the header and every thread [author] took part in, with ancestors and replies. */
    fun buildThreadListFor(
        author: String?,
        sourceComments: List<Comment>?
    ): MutableList<Comment> {
        if (author.isNullOrEmpty() || sourceComments.isNullOrEmpty()) {
            return mutableListOf()
        }

        val filteredComments = ArrayList<Comment>()
        filteredComments.add(sourceComments[0])

        val commentsById = HashMap<Int, Comment>()
        for (i in 1..<sourceComments.size) {
            val comment = sourceComments[i]
            commentsById[comment.id] = comment
        }

        val includedCommentIds = HashSet<Int>()
        for (i in 1..<sourceComments.size) {
            val comment = sourceComments[i]
            if (author != comment.by) {
                continue
            }
            if (comment.id in includedCommentIds) {
                continue
            }

            includeCommentAndAncestors(
                comment,
                commentsById,
                includedCommentIds,
                sourceComments.size
            )
            val authorCommentDepth = comment.depth
            for (j in i + 1..<sourceComments.size) {
                val candidate = sourceComments[j]
                if (candidate.depth <= authorCommentDepth) {
                    break
                }
                includedCommentIds.add(candidate.id)
            }
        }

        for (i in 1..<sourceComments.size) {
            val comment = sourceComments[i]
            if (comment.id in includedCommentIds) {
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
            if (!includedCommentIds.add(current.id)) break
            current = commentsById[current.parent]
        }
    }
}
