package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.data.Comment
internal class CommentSubtreeIndex {
    private var lastChildIndexByPosition: IntArray? = null

    fun getLastChildIndex(comments: MutableList<Comment>, position: Int): Int {
        if (lastChildIndexByPosition == null
            || lastChildIndexByPosition!!.size != comments.size
        ) {
            rebuild(comments)
        }
        return lastChildIndexByPosition!![position]
    }

    fun invalidate() {
        lastChildIndexByPosition = null
    }

    private fun rebuild(comments: MutableList<Comment>) {
        val commentCount = comments.size
        val lastChildIndexes = IntArray(commentCount)
        if (commentCount <= 1) {
            lastChildIndexByPosition = lastChildIndexes
            return
        }

        val openCommentPositions = IntArray(commentCount - 1)
        var openCommentCount = 0
        for (position in 1..<commentCount) {
            lastChildIndexes[position] = position
            val depth = comments[position].depth
            while (openCommentCount > 0
                && comments[openCommentPositions[openCommentCount - 1]].depth >= depth
            ) {
                lastChildIndexes[openCommentPositions[--openCommentCount]] = position - 1
            }
            openCommentPositions[openCommentCount++] = position
        }

        while (openCommentCount > 0) {
            lastChildIndexes[openCommentPositions[--openCommentCount]] = commentCount - 1
        }
        lastChildIndexByPosition = lastChildIndexes
    }
}
