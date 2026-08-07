package com.simon.harmonichackernews.adapters

import com.simon.harmonichackernews.data.Comment
internal class CommentSubtreeIndex {
    private var lastChildIndexByPosition: IntArray? = null

    fun getLastChildIndex(comments: List<Comment>, position: Int): Int {
        val cachedIndexes = lastChildIndexByPosition
        if (cachedIndexes != null && cachedIndexes.size == comments.size) {
            return cachedIndexes[position]
        }
        return rebuild(comments)[position]
    }

    fun invalidate() {
        lastChildIndexByPosition = null
    }

    private fun rebuild(comments: List<Comment>): IntArray {
        val commentCount = comments.size
        val lastChildIndexes = IntArray(commentCount)
        if (commentCount <= 1) {
            lastChildIndexByPosition = lastChildIndexes
            return lastChildIndexes
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
        return lastChildIndexes
    }
}
