package com.simon.harmonichackernews.data;

import android.text.Spanned;

import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.Utils;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class Comment implements Serializable {
    public String by;
    public int id;
    public int parent;
    public String text;
    private transient String cachedExpandedAnchorTextSource;
    private transient String cachedExpandedAnchorText;
    public transient Spanned spannedText;
    public transient String collectedReferenceLinksSource;
    public transient CollectedReferenceLinks.Result collectedReferenceLinks;
    public transient Spanned collectedReferenceLinksSpannedText;
    public int time;
    public boolean expanded;
    public int depth;
    public int children;
    public int totalReplies;

    public List<Comment> childComments = Collections.emptyList();
    public int sortOrder;
    public int[] kidsIds; // For official HN API fallback - stores child comment IDs

    public Comment() {

    }

    public String getTimeFormatted() {
        return Utils.getTimeAgo(this.time);
    }

    public String getExpandedAnchorText() {
        String currentText = text;
        if (currentText == cachedExpandedAnchorTextSource
                || (currentText != null
                && currentText.equals(cachedExpandedAnchorTextSource))) {
            return cachedExpandedAnchorText;
        }

        String expandedText = Utils.expandShortenedAnchorText(currentText);
        cachedExpandedAnchorTextSource = currentText;
        cachedExpandedAnchorText = expandedText;
        return expandedText;
    }

    public int getTime() {
        return this.time;
    }

}
