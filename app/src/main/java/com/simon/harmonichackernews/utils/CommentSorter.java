package com.simon.harmonichackernews.utils;

import android.content.Context;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CommentSorter {

    public static void sort(Context ctx, List<Comment> comments) {
        String sortType = SettingsUtils.getPreferredCommentSorting(ctx);
        switch (sortType) {
            case "Reply count":
                for (int i = 1; i < comments.size(); i++) {
                    comments.get(i).totalReplies = numChildren(comments, i);
                }
                sortComments(comments, Comparator.comparingInt(c -> -c.totalReplies));
                break;
            case "Newest first":
                sortComments(comments, Comparator.comparingInt(c -> -c.time));
                break;
            case "Oldest first":
                sortComments(comments, Comparator.comparingInt(c -> c.time));
                break;
        }
    }

    private static void sortComments(List<Comment> comments, Comparator<Comment> comparator) {
        // Create comment hierarchy tree in commentsWithChildren field
        List<Comment> commentsWithChildren = populateChildComments(comments);

        // Sort for each depth (recursively on commentsWithChildren)
        sortCommentsRecursive(commentsWithChildren, comparator);

        // Flatten the tree to one array, and extract the sort order - set in sortOrder field
        setSortOrder(commentsWithChildren);

        // Handle special case of first header comment
        comments.get(0).sortOrder = -1;

        // Sort according to sortOrder from flattenCommentsWithChildren step - from sortOrder field
        comments.sort(Comparator.comparingInt(e -> e.sortOrder));
    }

    private static void sortCommentsRecursive(List<Comment> commentsWithChildren, Comparator<Comment> comparator) {
        // Sort top level (in place)
        commentsWithChildren.sort(comparator);

        for (Comment c : commentsWithChildren) {
            // Sort children level (in place)
            sortCommentsRecursive(c.childComments, comparator);
        }
    }


    private static List<Comment> populateChildComments(List<Comment> comments) {
        // Define top level (depth 0)  array
        List<Comment> commentsWithChildren = new ArrayList<>();

        // Top level, start at index 1 (special case of header comment)
        for (int i = 1; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            if (comment.depth == 0) {
                // Add to top level array
                commentsWithChildren.add(comment);
                // Add childComment field recursively (all comments after this one)
                populateChildComments(comments, comment, i);
            }
        }
        return commentsWithChildren;
    }

    private static void populateChildComments(List<Comment> comments, Comment comment, int startIndex) {
        int targetDepth = comment.depth + 1;
        comment.childComments = new ArrayList<Comment>();
        // Starting off after the comment, we don't go outside the array of course and stop if we reach a comment with depth smaller than the targetDepth
        for (int i = startIndex + 1; i < comments.size() && comments.get(i).depth >= targetDepth; i++) {
            // If we find something which has the target depth
            if (comments.get(i).depth == targetDepth) {
                // It should go in the childComments list
                comment.childComments.add(comments.get(i));
                // And we are responsible for doing the recursive action here
                populateChildComments(comments, comments.get(i), i);
            }
        }
    }


    private static void setSortOrder(List<Comment> commentsWithChildren) {
        // Define flat array
        List<Comment> flatComments = new ArrayList<>();
        // Flatten recursively
        flattenComments(commentsWithChildren, flatComments);

        // Set sort order in flat array
        for (int i = 0; i < flatComments.size(); i++) {
            flatComments.get(i).sortOrder = i;

            // Free memory as childComments are no longer used
            //flatComments.get(i).childComments = null;
        }
    }

    private static void flattenComments(List<Comment> comments, List<Comment> flatComments) {
        for (Comment comment : comments) {
            // Add this comment
            flatComments.add(comment);
            // Add children after
            if (comment.childComments != null) {
                flattenComments(comment.childComments, flatComments);
            }
        }
    }

    private static int numChildren(List<Comment> comments, int startIndex) {
        int count = 0;
        for (int i = startIndex + 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                break;
            } else {
                count++;
            }
        }
        return count;
    }

}