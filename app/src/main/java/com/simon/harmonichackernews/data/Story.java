package com.simon.harmonichackernews.data;

import android.os.Bundle;
import android.text.TextUtils;

import com.simon.harmonichackernews.CommentsFragment;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;

public class Story {
    public String by;
    public int descendants;
    public int id;
    public int score;
    public int time;
    public String title;
    public String pdfTitle;
    public String url;
    public int[] kids;
    public int[] pollOptions;
    public ArrayList<PollOption> pollOptionArrayList;
    public boolean loaded;
    public boolean clicked;
    public String text;
    public RepoInfo repoInfo;
    public ArxivInfo arxivInfo;
    public WikipediaInfo wikiInfo;
    public boolean isLink;
    public boolean isJob = false;
    public boolean loadingFailed = false;
    public boolean isComment = false;
    public String commentMasterTitle;
    public int commentMasterId;
    public String commentMasterUrl;

    public Story() {}

    public Story(String title, int id, boolean loaded, boolean clicked) {
        this.title = title;
        this.id = id;
        this.loaded = loaded;
        this.clicked = clicked;
    }

    public void update(String by, int id, int score, int time, String title) {
        this.by = by;
        this.id = id;
        this.score = score;
        this.time = time;
        this.title = title;
    }

    public String getTimeFormatted() {
        return Utils.getTimeAgo(this.time);
    }

    @Override
    public String toString() {
        return title;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(CommentsFragment.EXTRA_TITLE, title);
        bundle.putString(CommentsFragment.EXTRA_PDF_TITLE, pdfTitle);
        bundle.putString(CommentsFragment.EXTRA_BY, by);
        bundle.putString(CommentsFragment.EXTRA_URL, url);
        bundle.putInt(CommentsFragment.EXTRA_TIME, time);
        bundle.putIntArray(CommentsFragment.EXTRA_KIDS, kids);
        bundle.putIntArray(CommentsFragment.EXTRA_POLL_OPTIONS, pollOptions);
        bundle.putInt(CommentsFragment.EXTRA_DESCENDANTS, descendants);
        bundle.putInt(CommentsFragment.EXTRA_ID, id);
        bundle.putInt(CommentsFragment.EXTRA_SCORE, score);
        bundle.putString(CommentsFragment.EXTRA_TEXT, text);
        bundle.putBoolean(CommentsFragment.EXTRA_IS_LINK, isLink);
        bundle.putBoolean(CommentsFragment.EXTRA_IS_COMMENT, isComment);

        return bundle;
    }

    public boolean hasExtraInfo() {
        return arxivInfo != null || repoInfo != null || wikiInfo != null;
    }

}