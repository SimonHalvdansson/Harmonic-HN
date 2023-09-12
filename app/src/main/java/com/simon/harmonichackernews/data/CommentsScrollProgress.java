package com.simon.harmonichackernews.data;

import java.util.HashSet;

public class CommentsScrollProgress {

    public CommentsScrollProgress() {
        collapsedIDs = new HashSet<>();
    }

    public int storyId;
    public int topCommentId;
    public int topCommentOffset;
    public HashSet<Integer> collapsedIDs;

}
