package com.simon.harmonichackernews.utils;

import android.content.Context;

import com.simon.harmonichackernews.data.Comment;

import java.util.ArrayList;
import java.util.List;

public class CommentSorter {

    public static void sort(Context ctx, List<Comment> comments) {
        String sortType = SettingsUtils.getPreferredCommentSorting(ctx);
        switch (sortType) {
            case "Reply count":
                sortByReplyCount(comments);
                Utils.toast("test", ctx);
            case "Recent":
                sortByRecent(comments);
        }
    }

    private static void sortByRecent(List<Comment> comments) {
        List<Comment> zeroDepthElements = new ArrayList<>();

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                zeroDepthElements.add(comments.get(i));
            }
        }

        zeroDepthElements.sort((e1, e2) -> Integer.compare(e2.time, e1.time));

        int targetIdx = 0;
        for (Comment c : zeroDepthElements) {
            int originalIdx = comments.indexOf(c);

            int count = numChildren(comments, comments.indexOf(c)) + 1;//+1 to include parent
            moveSubList(comments, originalIdx, count, targetIdx);
            targetIdx += count;
        }
    }

    private static void sortByReplyCount(List<Comment> comments) {
        List<Comment> zeroDepthElements = new ArrayList<>();

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                comments.get(i).totalReplies = numChildren(comments, i);
            }
        }

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                zeroDepthElements.add(comments.get(i));
            }
        }

        zeroDepthElements.sort((e1, e2) -> Integer.compare(e2.totalReplies, e1.totalReplies));

        int targetIdx = 0;
        for (Comment c : zeroDepthElements) {
            int originalIdx = comments.indexOf(c);

            int count = numChildren(comments, comments.indexOf(c)) + 1;//+1 to include parent
            moveSubList(comments, originalIdx, count, targetIdx);
            targetIdx += count;
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

    private static <T> void moveSubList(List<T> list, int startIdx, int count, int targetIdx) {
        if (startIdx < 0 || startIdx + count > list.size() || targetIdx < 0 || targetIdx > list.size()) {
            throw new IllegalArgumentException("Invalid index or count");
        }

        List<T> sublist = new ArrayList<>(list.subList(startIdx, startIdx + count));
        list.subList(startIdx, startIdx + count).clear();  // Remove elements from original positions

        // Adjust targetIdx if elements were removed before it
        if (startIdx < targetIdx) {
            targetIdx -= count;
        }

        list.addAll(targetIdx, sublist);  // Add elements at the new position
    }

}
