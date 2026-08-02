package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import kotlin.math.min

class CommentTreeBuilder(private val topLevelIds: IntArray) {
    private val commentMap: MutableMap<Int, Comment> = HashMap()
    private val childrenMap: MutableMap<Int, MutableList<Comment>> = HashMap()

    fun addComment(comment: Comment) {
        commentMap.put(comment.id, comment)


        // Add to children map if it has a parent
        if (comment.parent != 0) {
            var list = childrenMap.get(comment.parent)
            if (list == null) {
                list = ArrayList()
                childrenMap.put(comment.parent, list)
            }
            list.add(comment)
        }
    }

    fun buildOrderedTree(): MutableList<Comment> {
        val orderedComments: MutableList<Comment> = ArrayList()


        // Process top-level comments in the order specified by topLevelIds
        for (topLevelId in topLevelIds) {
            val topComment = commentMap.get(topLevelId)
            if (topComment != null) {
                topComment.depth = 0
                orderedComments.add(topComment)
                addChildrenToList(orderedComments, topComment, 1)
            }
        }

        return orderedComments
    }

    private fun addChildrenToList(
        orderedComments: MutableList<Comment>,
        parent: Comment,
        depth: Int
    ) {
        var children = childrenMap.get(parent.id)
        if (children == null) return


        // Sort children by the order they appear in parent's kidsIds if available
        if (parent.kidsIds != null && parent.kidsIds!!.size > 0) {
            val childrenById: MutableMap<Int, Comment> = HashMap(children.size)
            for (child in children) {
                // Match the previous nested search, which selected the first child
                // with a given ID if duplicate objects were ever added.
                if (!childrenById.containsKey(child.id)) {
                    childrenById.put(child.id, child)
                }
            }

            val orderedChildren: MutableList<Comment> =
                ArrayList<Comment>(min(children.size, parent.kidsIds!!.size))
            for (childId in parent.kidsIds) {
                val child = childrenById.get(childId)
                if (child != null) {
                    orderedChildren.add(child)
                }
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
