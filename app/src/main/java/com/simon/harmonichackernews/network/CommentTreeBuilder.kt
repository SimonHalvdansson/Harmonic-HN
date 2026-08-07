package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment

class CommentTreeBuilder(private val topLevelIds: IntArray) {
    private val commentMap = mutableMapOf<Int, Comment>()
    private val childrenMap = mutableMapOf<Int, MutableList<Comment>>()

    fun addComment(comment: Comment) {
        commentMap[comment.id] = comment

        // Add to children map if it has a parent
        if (comment.parent != 0) {
            childrenMap.getOrPut(comment.parent) { mutableListOf() }.add(comment)
        }
    }

    fun buildOrderedTree(): MutableList<Comment> {
        val orderedComments = mutableListOf<Comment>()

        // Process top-level comments in the order specified by topLevelIds
        for (topLevelId in topLevelIds) {
            val topComment = commentMap[topLevelId] ?: continue
            topComment.depth = 0
            orderedComments.add(topComment)
            addChildrenToList(orderedComments, topComment, 1)
        }

        return orderedComments
    }

    private fun addChildrenToList(
        orderedComments: MutableList<Comment>,
        parent: Comment,
        depth: Int
    ) {
        var children = childrenMap[parent.id] ?: return

        // Sort children by the order they appear in parent's kidsIds if available
        val childIds = parent.kidsIds
        if (childIds != null && childIds.isNotEmpty()) {
            val childrenById = HashMap<Int, Comment>(children.size)
            for (child in children) {
                // Match the previous nested search, which selected the first child
                // with a given ID if duplicate objects were ever added.
                childrenById.putIfAbsent(child.id, child)
            }

            val orderedChildren = ArrayList<Comment>(minOf(children.size, childIds.size))
            for (childId in childIds) {
                childrenById[childId]?.let(orderedChildren::add)
            }
            children = orderedChildren
        }

        // Add children and their subtrees
        for (child in children) {
            child.depth = depth
            orderedComments.add(child)
            addChildrenToList(orderedComments, child, depth + 1)
        }
    }

    val totalComments: Int
        get() = commentMap.size

    fun clear() {
        commentMap.clear()
        childrenMap.clear()
    }
}
