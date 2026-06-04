package com.simon.harmonichackernews.utils;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.annotation.Nullable;

public final class StoryPreviewImageMemoryCache {

    private static final int MAX_ENTRIES = 48;
    private static final LruCache<String, Drawable.ConstantState> CACHE =
            new LruCache<>(MAX_ENTRIES);

    private StoryPreviewImageMemoryCache() {
    }

    public static void put(int storyId, String imageUrl, @Nullable Drawable drawable) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl) || drawable == null) {
            return;
        }

        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            synchronized (CACHE) {
                CACHE.put(getKey(storyId, imageUrl), constantState);
            }
        }
    }

    @Nullable
    public static Drawable get(int storyId, String imageUrl) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null;
        }

        Drawable.ConstantState constantState;
        synchronized (CACHE) {
            constantState = CACHE.get(getKey(storyId, imageUrl));
        }
        return constantState == null ? null : constantState.newDrawable();
    }

    private static String getKey(int storyId, String imageUrl) {
        return storyId + ":" + imageUrl;
    }
}
