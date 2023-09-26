package com.simon.harmonichackernews.utils;

import android.content.Context;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.List;

public class CommentSorter {

    public static List<Comment> sort(Context ctx, List<Comment> comments) {
        String sortType = SettingsUtils.getPreferredCommentSorting(ctx);
        switch (sortType) {
            case "Default":
                return comments;
            case "Replies":
                return sortByReplyCount(comments);
            case "Recent":
                return sortByRecent(comments);
            case "Response by OP":
                return sortByOp(comments);
            default:
                return comments;
        }
    }

    private static List<Comment> sortByOp(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>();


        return sorted;
    }

    private static List<Comment> sortByRecent(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>();


        return sorted;
    }

    private static List<Comment> sortByReplyCount(List<Comment> comments) {
        List<Comment> sorted = new ArrayList<>();


        return sorted;
    }

}
