package com.simon.harmonichackernews.data;

import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class Comment {
    public String by;
    public int id;
    public int parent;
    public String text;
    public int time;
    public boolean expanded;
    public int depth;
    public int children;
    public int totalReplies;

    public List<Comment> childComments = new ArrayList<>();
    public int sortOrder;

    public Comment() {

    }

    public String getTimeFormatted() {
        return Utils.getTimeAgo(this.time);
    }

    public int getTime() {
        return this.time;
    }

}