package com.simon.harmonichackernews;

import android.content.res.Resources;
import android.text.TextUtils;

import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;

enum StoryType {
    TOP_STORIES("Top Stories"),
    LAST_24_HOURS("Last 24 hours"),
    LAST_48_HOURS("Last 48 hours"),
    LAST_WEEK("Last week"),
    NEW_STORIES("New Stories"),
    BEST_STORIES("Best Stories"),
    ASK_HN("Ask HN"),
    SHOW_HN("Show HN"),
    HN_JOBS("HN Jobs"),
    BOOKMARKS("Bookmarks"),
    FAVORITES(SettingsUtils.FAVORITES_LABEL),
    UPVOTED(SettingsUtils.UPVOTED_LABEL),
    HISTORY("History"),
    UNKNOWN("");

    private final String label;

    StoryType(String label) {
        this.label = label;
    }

    static ArrayList<CharSequence> buildAdapterLabels(Resources resources, boolean showUserItemLists) {
        String[] sortingOptions = resources.getStringArray(R.array.sorting_options);
        ArrayList<CharSequence> labels = new ArrayList<>(Arrays.asList(sortingOptions));
        if (showUserItemLists) {
            int favoritesIndex = Math.min(SettingsUtils.getBookmarksIndex(resources) + 1, labels.size());
            labels.add(favoritesIndex, SettingsUtils.FAVORITES_LABEL);
            labels.add(SettingsUtils.UPVOTED_LABEL);
        }
        return labels;
    }

    static StoryType fromLabel(CharSequence label) {
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

    boolean isAlgolia() {
        return this == LAST_24_HOURS || this == LAST_48_HOURS || this == LAST_WEEK;
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
        return isBookmarks() || isUserItemList();
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
