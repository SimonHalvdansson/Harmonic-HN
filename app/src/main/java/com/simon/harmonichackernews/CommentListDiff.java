package com.simon.harmonichackernews;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.List;

final class CommentListDiff {
    private CommentListDiff() {
    }

    static List<Comment> copyForDiff(List<Comment> source) {
        List<Comment> copy = new ArrayList<>(source.size());
        for (Comment comment : source) {
            Comment commentCopy = new Comment();
            commentCopy.id = comment.id;
            commentCopy.parent = comment.parent;
            commentCopy.time = comment.time;
            commentCopy.expanded = comment.expanded;
            commentCopy.depth = comment.depth;
            commentCopy.children = comment.children;
            commentCopy.by = comment.by;
            commentCopy.text = comment.text;
            copy.add(commentCopy);
        }
        return copy;
    }

    static void updateExistingComment(Comment existingComment, Comment parsedComment) {
        existingComment.parent = parsedComment.parent;
        existingComment.by = parsedComment.by;
        existingComment.text = parsedComment.text;
        existingComment.time = parsedComment.time;
        existingComment.depth = parsedComment.depth;
        existingComment.children = parsedComment.children;
        existingComment.childComments = parsedComment.childComments;
    }
}
