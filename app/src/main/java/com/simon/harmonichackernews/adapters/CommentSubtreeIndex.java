package com.simon.harmonichackernews.adapters;

import com.simon.harmonichackernews.data.Comment;

import java.util.List;

final class CommentSubtreeIndex {
    private int[] lastChildIndexByPosition;

    int getLastChildIndex(List<Comment> comments, int position) {
        if (lastChildIndexByPosition == null
                || lastChildIndexByPosition.length != comments.size()) {
            rebuild(comments);
        }
        return lastChildIndexByPosition[position];
    }

    void invalidate() {
        lastChildIndexByPosition = null;
    }

    private void rebuild(List<Comment> comments) {
        int commentCount = comments.size();
        int[] lastChildIndexes = new int[commentCount];
        if (commentCount <= 1) {
            lastChildIndexByPosition = lastChildIndexes;
            return;
        }

        int[] openCommentPositions = new int[commentCount - 1];
        int openCommentCount = 0;
        for (int position = 1; position < commentCount; position++) {
            lastChildIndexes[position] = position;
            int depth = comments.get(position).depth;
            while (openCommentCount > 0
                    && comments.get(openCommentPositions[openCommentCount - 1]).depth >= depth) {
                lastChildIndexes[openCommentPositions[--openCommentCount]] = position - 1;
            }
            openCommentPositions[openCommentCount++] = position;
        }

        while (openCommentCount > 0) {
            lastChildIndexes[openCommentPositions[--openCommentCount]] = commentCount - 1;
        }
        lastChildIndexByPosition = lastChildIndexes;
    }
}
