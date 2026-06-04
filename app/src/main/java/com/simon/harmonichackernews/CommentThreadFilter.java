package com.simon.harmonichackernews;

import android.text.TextUtils;

import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CommentThreadFilter {
    private CommentThreadFilter() {
    }

    static boolean hasCommentsByOp(Story story, List<Comment> sourceComments) {
        if (story == null || TextUtils.isEmpty(story.by) || sourceComments == null) {
            return false;
        }

        for (int i = 1; i < sourceComments.size(); i++) {
            if (TextUtils.equals(story.by, sourceComments.get(i).by)) {
                return true;
            }
        }
        return false;
    }

    static List<Comment> buildCommentsByOpThreadList(Story story, List<Comment> sourceComments) {
        List<Comment> filteredComments = new ArrayList<>();
        if (story == null || TextUtils.isEmpty(story.by) || sourceComments == null || sourceComments.isEmpty()) {
            return filteredComments;
        }

        filteredComments.add(sourceComments.get(0));

        Map<Integer, Comment> commentsById = new HashMap<>();
        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            commentsById.put(comment.id, comment);
        }

        Set<Integer> includedCommentIds = new HashSet<>();
        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            if (!TextUtils.equals(story.by, comment.by)) {
                continue;
            }

            includeCommentAndAncestors(comment, commentsById, includedCommentIds, sourceComments.size());
            int opCommentDepth = comment.depth;
            for (int j = i + 1; j < sourceComments.size(); j++) {
                Comment candidate = sourceComments.get(j);
                if (candidate.depth <= opCommentDepth) {
                    break;
                }
                includedCommentIds.add(candidate.id);
            }
        }

        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            if (includedCommentIds.contains(comment.id)) {
                filteredComments.add(comment);
            }
        }
        return filteredComments;
    }

    private static void includeCommentAndAncestors(Comment comment, Map<Integer, Comment> commentsById,
                                                  Set<Integer> includedCommentIds, int maxDepth) {
        Comment current = comment;
        int guard = 0;
        while (current != null && guard++ < maxDepth) {
            includedCommentIds.add(current.id);
            current = commentsById.get(current.parent);
        }
    }
}
