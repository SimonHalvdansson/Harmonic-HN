package com.simon.harmonichackernews.utils;

import android.content.Context;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CommentSorter {

    public static void sort(Context ctx, List<Comment> comments) {
        sort(comments, SettingsUtils.getPreferredCommentSorting(ctx));
    }

    public static void sort(List<Comment> comments, String sortType) {
        switch (sortType) {
            case "Default":
                sortComments(comments, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment c1, Comment c2) {
                        return Integer.compare(c1.sortOrder, c2.sortOrder);
                    }
                }, false);
                break;
            case "Reply count":
                sortComments(comments, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment c1, Comment c2) {
                        return Integer.compare(c2.totalReplies, c1.totalReplies);
                    }
                }, true);
                break;
            case "Newest first":
                sortComments(comments, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment c1, Comment c2) {
                        return Integer.compare(c2.time, c1.time);
                    }
                }, false);
                break;
            case "Oldest first":
                sortComments(comments, new Comparator<Comment>() {
                    @Override
                    public int compare(Comment c1, Comment c2) {
                        return Integer.compare(c1.time, c2.time);
                    }
                }, false);
                break;
        }
    }

    private static void sortComments(List<Comment> comments, Comparator<Comment> comparator, boolean updateReplyCounts) {
        if (comments.size() <= 1) {
            return;
        }

        Comment header = comments.get(0);
        List<Comment> commentsWithChildren = buildCommentTree(comments);

        if (updateReplyCounts) {
            updateTotalReplies(commentsWithChildren);
        }

        sortCommentsRecursive(commentsWithChildren, comparator);

        comments.clear();
        comments.add(header);
        flattenComments(commentsWithChildren, comments);
    }

    private static void sortCommentsRecursive(List<Comment> commentsWithChildren, Comparator<Comment> comparator) {
        Collections.sort(commentsWithChildren, comparator);

        for (Comment c : commentsWithChildren) {
            sortCommentsRecursive(c.childComments, comparator);
        }
    }


    private static List<Comment> buildCommentTree(List<Comment> comments) {
        List<Comment> commentsWithChildren = new ArrayList<>();
        List<Comment> parentsByDepth = new ArrayList<>();

        for (int i = 1; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            comment.childComments = new ArrayList<>();
            int depth = Math.max(0, comment.depth);

            while (parentsByDepth.size() > depth) {
                parentsByDepth.remove(parentsByDepth.size() - 1);
            }

            if (depth == 0 || parentsByDepth.isEmpty()) {
                commentsWithChildren.add(comment);
            } else {
                parentsByDepth.get(parentsByDepth.size() - 1).childComments.add(comment);
            }

            parentsByDepth.add(comment);
        }

        return commentsWithChildren;
    }

    private static void flattenComments(List<Comment> comments, List<Comment> flatComments) {
        for (Comment comment : comments) {
            flatComments.add(comment);
            if (!comment.childComments.isEmpty()) {
                flattenComments(comment.childComments, flatComments);
            }
        }
    }

    private static void updateTotalReplies(List<Comment> comments) {
        for (Comment comment : comments) {
            updateTotalReplies(comment);
        }
    }

    private static int updateTotalReplies(Comment comment) {
        int count = 0;
        for (Comment child : comment.childComments) {
            count += 1 + updateTotalReplies(child);
        }
        comment.totalReplies = count;
        return count;
    }

}
