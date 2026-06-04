package com.simon.harmonichackernews.utils;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.annotation.Nullable;

public final class StoryPreviewImageMemoryCache {

    private static final int MAX_ENTRIES = 48;
    private static final LruCache<String, Drawable.ConstantState> CACHE =
            new LruCache<>(MAX_ENTRIES);
    private static final LruCache<String, Integer> TINT_CACHE =
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

    public static void putTintColor(int storyId, String imageUrl, int baseColor, int tintColor) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return;
        }

        synchronized (TINT_CACHE) {
            TINT_CACHE.put(getTintKey(storyId, imageUrl, baseColor), tintColor);
        }
    }

    @Nullable
    public static Integer getTintColor(int storyId, String imageUrl, int baseColor) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null;
        }

        synchronized (TINT_CACHE) {
            return TINT_CACHE.get(getTintKey(storyId, imageUrl, baseColor));
        }
    }

    private static String getKey(int storyId, String imageUrl) {
        return storyId + ":" + imageUrl;
    }

    private static String getTintKey(int storyId, String imageUrl, int baseColor) {
        return getKey(storyId, imageUrl) + ":" + baseColor;
    }
}
