package com.simon.harmonichackernews

import com.simon.harmonichackernews.data.Comment

object CommentListDiff {
    fun updateExistingComment(existingComment: Comment, parsedComment: Comment) {
        existingComment.parent = parsedComment.parent
        existingComment.by = parsedComment.by
        existingComment.updateTextFrom(parsedComment)
        existingComment.time = parsedComment.time
        existingComment.depth = parsedComment.depth
        existingComment.children = parsedComment.children
        existingComment.childComments = parsedComment.childComments
    }
}
