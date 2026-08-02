package com.simon.harmonichackernews

import com.simon.harmonichackernews.data.Comment
import java.util.ArrayList
import java.util.List

internal object CommentListDiff {
    fun copyForDiff(source: MutableList<Comment>): MutableList<Comment> {
        val copy: MutableList<Comment> = ArrayList(source.size)
        for (comment in source) {
            val commentCopy = Comment()
            commentCopy.id = comment.id
            commentCopy.parent = comment.parent
            commentCopy.time = comment.time
            commentCopy.expanded = comment.expanded
            commentCopy.depth = comment.depth
            commentCopy.children = comment.children
            commentCopy.by = comment.by
            commentCopy.text = comment.text
            copy.add(commentCopy)
        }
        return copy
    }

    fun updateExistingComment(existingComment: Comment, parsedComment: Comment) {
        existingComment.parent = parsedComment.parent
        existingComment.by = parsedComment.by
        existingComment.text = parsedComment.text
        existingComment.time = parsedComment.time
        existingComment.depth = parsedComment.depth
        existingComment.children = parsedComment.children
        existingComment.childComments = parsedComment.childComments
    }
}
