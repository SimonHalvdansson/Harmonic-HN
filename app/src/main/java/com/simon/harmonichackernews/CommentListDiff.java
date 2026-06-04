package com.simon.harmonichackernews;

import android.text.TextUtils;

import androidx.recyclerview.widget.DiffUtil;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.List;

final class CommentListDiff {
    private CommentListDiff() {
    }

    static DiffUtil.DiffResult calculateDiff(List<Comment> oldComments, List<Comment> nextComments) {
        return DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldComments.size();
            }

            @Override
            public int getNewListSize() {
                return nextComments.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 || newItemPosition == 0) {
                    return oldItemPosition == 0 && newItemPosition == 0;
                }
                return oldComments.get(oldItemPosition).id == nextComments.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 && newItemPosition == 0) {
                    return true;
                }
                return areContentsSame(oldComments.get(oldItemPosition), nextComments.get(newItemPosition));
            }
        }, false);
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

    private static boolean areContentsSame(Comment oldComment, Comment newComment) {
        return oldComment.id == newComment.id
                && oldComment.parent == newComment.parent
                && oldComment.time == newComment.time
                && oldComment.expanded == newComment.expanded
                && oldComment.depth == newComment.depth
                && oldComment.children == newComment.children
                && TextUtils.equals(oldComment.by, newComment.by)
                && TextUtils.equals(oldComment.text, newComment.text);
    }
}
