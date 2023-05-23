package com.simon.harmonichackernews.data;

import com.simon.harmonichackernews.utils.Utils;

public class Comment {
    public String by;
    public int id;
    public int parent;
    public String text;
    public int time;
    public boolean expanded;
    public int depth;
    public int children;

    public Comment() {

    }

    public String getTimeFormatted() {
        return Utils.getTimeAgo(this.time);
    }

}
