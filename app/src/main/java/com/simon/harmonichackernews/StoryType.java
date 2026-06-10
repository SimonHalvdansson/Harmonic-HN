package com.simon.harmonichackernews;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;

import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public enum StoryType {
    TOP_STORIES("Top Stories"),
    LAST_24_HOURS("Last 24 hours"),
    LAST_48_HOURS("Last 48 hours"),
    LAST_WEEK("Last week"),
    NEW_STORIES("New Stories"),
    BEST_STORIES("Best Stories"),
    ASK_HN("Ask HN"),
    SHOW_HN("Show HN"),
    HN_JOBS("HN Jobs"),
    CLASSIC(SettingsUtils.FRONT_PAGE_CLASSIC, "classic", true, false, false),
    BEST_COMMENTS(SettingsUtils.FRONT_PAGE_BEST_COMMENTS, "bestcomments", true, true, false),
    HIGHLIGHTS(SettingsUtils.FRONT_PAGE_HIGHLIGHTS, "highlights", true, true, false),
    ACTIVE(SettingsUtils.FRONT_PAGE_ACTIVE, "active", true, false, false),
    FRONT(SettingsUtils.FRONT_PAGE_FRONT, "front", true, false, false),
    BOOKMARKS("Bookmarks"),
    FAVORITES(SettingsUtils.FAVORITES_LABEL),
    UPVOTED(SettingsUtils.UPVOTED_LABEL),
    HISTORY("History"),
    UNKNOWN("");

    private final String label;
    private final String hackerNewsPath;
    private final boolean additionalFrontpage;
    private final boolean commentRows;
    private final boolean frontpageLinkList;

    StoryType(String label) {
        this(label, null, false, false, false);
    }

    StoryType(String label,
              String hackerNewsPath,
              boolean additionalFrontpage,
              boolean commentRows,
              boolean frontpageLinkList) {
        this.label = label;
        this.hackerNewsPath = hackerNewsPath;
        this.additionalFrontpage = additionalFrontpage;
        this.commentRows = commentRows;
        this.frontpageLinkList = frontpageLinkList;
    }

    static ArrayList<CharSequence> buildAdapterLabels(Resources resources, Context ctx, boolean showUserItemLists) {
        String[] sortingOptions = resources.getStringArray(R.array.sorting_options);
        ArrayList<CharSequence> labels = new ArrayList<>(Arrays.asList(sortingOptions));
        int additionalFrontpageIndex = getLabelIndex(labels, BOOKMARKS.label);
        if (additionalFrontpageIndex < 0) {
            additionalFrontpageIndex = Math.min(SettingsUtils.getJobsIndex(resources) + 1, labels.size());
        }
        for (StoryType type : getAdditionalFrontpages()) {
            if (type.isEnabledAdditionalFrontpage(ctx)) {
                labels.add(additionalFrontpageIndex, type.label);
                additionalFrontpageIndex++;
            }
        }
        if (showUserItemLists) {
            int bookmarksIndex = getLabelIndex(labels, BOOKMARKS.label);
            int favoritesIndex = bookmarksIndex >= 0 ? bookmarksIndex + 1 : Math.min(SettingsUtils.getBookmarksIndex(resources) + 1, labels.size());
            labels.add(favoritesIndex, SettingsUtils.FAVORITES_LABEL);
            labels.add(SettingsUtils.UPVOTED_LABEL);
        }
        return labels;
    }

    public static ArrayList<CharSequence> buildStartingPageLabels(Resources resources, Context ctx) {
        return buildStartingPageLabels(resources, SettingsUtils.getEnabledAdditionalFrontpages(ctx));
    }

    public static ArrayList<CharSequence> buildStartingPageLabels(Resources resources, Set<String> enabledFrontpages) {
        String[] startingPageOptions = resources.getStringArray(R.array.starting_page_options);
        ArrayList<CharSequence> labels = new ArrayList<>(Arrays.asList(startingPageOptions));
        for (StoryType type : getAdditionalFrontpages()) {
            if (enabledFrontpages != null && enabledFrontpages.contains(type.label)) {
                labels.add(type.label);
            }
        }
        return labels;
    }

    private static StoryType[] getAdditionalFrontpages() {
        return new StoryType[] {CLASSIC, BEST_COMMENTS, HIGHLIGHTS, ACTIVE, FRONT};
    }

    private static int getLabelIndex(ArrayList<CharSequence> labels, String label) {
        for (int i = 0; i < labels.size(); i++) {
            if (TextUtils.equals(labels.get(i), label)) {
                return i;
            }
        }
        return -1;
    }

    public static StoryType fromLabel(CharSequence label) {
        if (label == null) {
            return UNKNOWN;
        }

        for (StoryType type : values()) {
            if (TextUtils.equals(type.label, label)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public boolean isAlgolia() {
        return this == LAST_24_HOURS || this == LAST_48_HOURS || this == LAST_WEEK;
    }

    boolean isActive() {
        return this == ACTIVE;
    }

    boolean isFront() {
        return this == FRONT;
    }

    boolean isBookmarks() {
        return this == BOOKMARKS;
    }

    boolean isHistory() {
        return this == HISTORY;
    }

    boolean isFavorites() {
        return this == FAVORITES;
    }

    boolean isUpvoted() {
        return this == UPVOTED;
    }

    boolean isUserItemList() {
        return isFavorites() || isUpvoted();
    }

    boolean usesSavedItemFilter() {
        return isBookmarks() || isUserItemList();
    }

    boolean usesCommentRows() {
        return isBookmarks() || isUserItemList() || commentRows;
    }

    boolean isScrapedFrontpage() {
        return additionalFrontpage && !frontpageLinkList;
    }

    boolean isFrontpageLinkList() {
        return frontpageLinkList;
    }

    public String getHackerNewsPath() {
        return hackerNewsPath;
    }

    String getLabel() {
        return label;
    }

    private boolean isEnabledAdditionalFrontpage(Context ctx) {
        return !additionalFrontpage || (ctx != null && SettingsUtils.isAdditionalFrontpageEnabled(ctx, label));
    }

    String getHackerNewsUrl() {
        switch (this) {
            case TOP_STORIES:
                return Utils.URL_TOP;
            case NEW_STORIES:
                return Utils.URL_NEW;
            case BEST_STORIES:
                return Utils.URL_BEST;
            case ASK_HN:
                return Utils.URL_ASK;
            case SHOW_HN:
                return Utils.URL_SHOW;
            case HN_JOBS:
                return Utils.URL_JOBS;
            default:
                return null;
        }
    }
}
