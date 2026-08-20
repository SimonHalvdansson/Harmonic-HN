package com.simon.harmonichackernews.utils;

import android.content.Context;

import java.util.Set;

/** Stores story IDs dismissed from the main story feed. */
public final class DismissedStoryStore {
    private static final String KEY_DISMISSED_STORY_IDS =
            "com.simon.harmonichackernews.KEY_DISMISSED_STORY_IDS";

    private DismissedStoryStore() {
    }

    public static boolean isDismissed(Context context, int storyId) {
        return getDismissedIds(context).contains(storyId);
    }

    public static void dismiss(Context context, int storyId) {
        Set<Integer> dismissedIds = getDismissedIds(context);
        dismissedIds.add(storyId);
        SettingsUtils.saveIntSetToSharedPreferences(context, KEY_DISMISSED_STORY_IDS, dismissedIds);
    }

    public static void restore(Context context, int storyId) {
        Set<Integer> dismissedIds = getDismissedIds(context);
        if (dismissedIds.remove(storyId)) {
            SettingsUtils.saveIntSetToSharedPreferences(context, KEY_DISMISSED_STORY_IDS, dismissedIds);
        }
    }

    private static Set<Integer> getDismissedIds(Context context) {
        return SettingsUtils.readIntSetFromSharedPreferences(context, KEY_DISMISSED_STORY_IDS);
    }
}
