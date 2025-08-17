package com.simon.harmonichackernews.data;

import com.simon.harmonichackernews.utils.Utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Comment implements Serializable {
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
    public int[] kidsIds; // For official HN API fallback - stores child comment IDs

    public Comment() {

    }

    public String getTimeFormatted() {
        return Utils.getTimeAgo(this.time);
    }

    public int getTime() {
        return this.time;
    }

}
