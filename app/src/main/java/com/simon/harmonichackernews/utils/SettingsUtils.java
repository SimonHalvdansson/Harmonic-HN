package com.simon.harmonichackernews.utils;

import static com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.BatteryManager;

import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.util.HashSet;
import java.util.Set;

public class SettingsUtils {

    public static final String PREF_COMMENT_DEPTH_INDICATORS = "pref_comment_depth_indicators";
    public static final String PREF_MONOCHROME_COMMENT_DEPTH = "pref_monochrome_comment_depth";
    public static final String PREF_STORY_DISPLAY_STYLE = "pref_story_display_style";
    public static final String PREF_STORY_PREVIEW_IMAGE_MODE = "pref_story_preview_image_mode";
    public static final String PREF_STORY_TEXT_SIZE = "pref_story_text_size";
    public static final String PREF_COMMENT_DISPLAY_STYLE = "pref_comment_display_style";
    public static final String PREF_COMMENT_TEXT_SIZE = "pref_comment_text_size";
    public static final String PREF_BOOKMARKS_ENABLED = "pref_bookmarks_enabled";
    public static final String PREF_GRAY_OUT_CLICKED = "pref_gray_out_clicked";
    public static final String PREF_HIDE_CLICKED = "pref_hide_clicked";
    public static final String PREF_ALWAYS_SHOW_TAP_TO_REFRESH = "pref_always_show_tap_to_refresh";
    public static final String PREF_PRELOAD_WEBVIEW = "pref_preload_webview";
    public static final String PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY = "pref_preload_webview_minimum_battery";
    public static final String PRELOAD_WEBVIEW_ALWAYS = "always";
    public static final String PRELOAD_WEBVIEW_ONLY_WIFI = "onlywifi";
    public static final String PRELOAD_WEBVIEW_NEVER = "never";
    public static final int DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY = 0;
    public static final String STORY_DISPLAY_STYLE_STANDARD = "standard";
    public static final String STORY_DISPLAY_STYLE_CARD = "card";
    public static final String STORY_PREVIEW_IMAGE_OFF = "off";
    public static final String STORY_PREVIEW_IMAGE_SMALL = "small";
    public static final String STORY_PREVIEW_IMAGE_LARGE = "large";
    public static final String COMMENT_DISPLAY_STYLE_STANDARD = STORY_DISPLAY_STYLE_STANDARD;
    public static final String COMMENT_DISPLAY_STYLE_CARD = STORY_DISPLAY_STYLE_CARD;
    public static final float DEFAULT_STORY_TEXT_SIZE = 17.5f;
    public static final float DEFAULT_STORY_META_TEXT_SIZE = 13f;
    public static final int MIN_STORY_TEXT_SIZE_OFFSET = -6;
    public static final int MAX_STORY_TEXT_SIZE_OFFSET = 6;
    public static final float STORY_TEXT_SIZE_OFFSET_STEP = 0.5f;
    public static final float MIN_STORY_TEXT_SIZE = DEFAULT_STORY_TEXT_SIZE
            + MIN_STORY_TEXT_SIZE_OFFSET * STORY_TEXT_SIZE_OFFSET_STEP;
    public static final float MAX_STORY_TEXT_SIZE = DEFAULT_STORY_TEXT_SIZE
            + MAX_STORY_TEXT_SIZE_OFFSET * STORY_TEXT_SIZE_OFFSET_STEP;
    public static final float DEFAULT_COMMENT_TEXT_SIZE = 15f;
    public static final int MIN_COMMENT_TEXT_SIZE_OFFSET = -6;
    public static final int MAX_COMMENT_TEXT_SIZE_OFFSET = 6;
    public static final float COMMENT_TEXT_SIZE_OFFSET_STEP = 0.5f;
    public static final float MIN_COMMENT_TEXT_SIZE = DEFAULT_COMMENT_TEXT_SIZE
            + MIN_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP;
    public static final float MAX_COMMENT_TEXT_SIZE = DEFAULT_COMMENT_TEXT_SIZE
            + MAX_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP;
    public static final String FAVORITES_LABEL = "Favorites";
    public static final String UPVOTED_LABEL = "Upvoted";

    public static Set<Integer> readIntSetFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Set<String> emptyBackup = new HashSet<>();
        Set<String> stringSet = sharedPref.getStringSet(key, emptyBackup);
        if (stringSet == null) {
            stringSet = emptyBackup;
        } else {
            stringSet = new HashSet<>(stringSet);
        }

        Set<Integer> intSet = new HashSet<>(stringSet.size());
        for (String string : stringSet) {
            intSet.add(Integer.parseInt(string));
        }
        return intSet;
    }

    public static void saveIntSetToSharedPreferences(Context ctx, String key, Set<Integer> set) {
        Set<String> stringSet = new HashSet<>(set.size());

        for (Integer integer : set) {
            stringSet.add(integer.toString());
        }

        saveStringSetToSharedPreferences(ctx, key, stringSet);
    }

    public static Set<String> readStringSetFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        Set<String> emptyBackup = new HashSet<>();
        Set<String> stringSet = sharedPref.getStringSet(key, emptyBackup);
        return stringSet == null ? null : new HashSet<>(stringSet);
    }

    public static void saveStringSetToSharedPreferences(Context ctx, String key, Set<String> set) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putStringSet(key, set == null ? null : new HashSet<>(set)).apply();
    }

    public static void saveStringToSharedPreferences(Context ctx, String key, String text) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putString(key, text).apply();
    }

    public static String readStringFromSharedPreferences(Context ctx, String key) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(key, null);
    }

    public static String readStringFromSharedPreferences(Context ctx, String key, String fallback) {
        SharedPreferences sharedPref = ctx.getSharedPreferences(GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(key, fallback);
    }

    public static boolean shouldShowPoints(Context ctx) {
        return getBooleanPref("pref_show_points", true, ctx);
    }

    public static boolean shouldShowCommentsCount(Context ctx) {
        return getBooleanPref("pref_show_comments_count", true, ctx);
    }

    public static boolean shouldUseCompactView(Context ctx) {
        return getBooleanPref("pref_compact_view", false, ctx);
    }

    public static boolean shouldShowThumbnails(Context ctx) {
        return getBooleanPref("pref_thumbnails", true, ctx);
    }

    public static String getPreferredStoryPreviewImageMode(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String mode = prefs.getString(PREF_STORY_PREVIEW_IMAGE_MODE, null);
        if (STORY_PREVIEW_IMAGE_SMALL.equals(mode) || STORY_PREVIEW_IMAGE_LARGE.equals(mode)) {
            return mode;
        }
        return STORY_PREVIEW_IMAGE_OFF;
    }

    public static boolean shouldCollapseParent(Context ctx) {
        return getBooleanPref("pref_collapse_parent", false, ctx);
    }

    public static boolean shouldShowIndex(Context ctx) {
        return getBooleanPref("pref_show_index", true, ctx);
    }

    public static boolean shouldShowNavigationButtons(Context ctx) {
        return getBooleanPref("pref_scroll_navigation", false, ctx);
    }

    public static boolean shouldHideJobs(Context ctx) {
        return getBooleanPref("pref_hide_jobs", false, ctx);
    }

    public static boolean shouldCollapseTopLevel(Context ctx) {
        return getBooleanPref("pref_collapse_top_level", false, ctx);
    }

    public static int getPreferredHotness(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return Integer.parseInt(prefs.getString("pref_hotness", "-1"));
    }

    public static String getPreferredFont(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_font", "productsans");
    }

    public static boolean shouldUseExternalBrowser(Context ctx) {
        return getBooleanPref("pref_external_browser", false, ctx);
    }

    public static String getPreferredCommentDepthIndicatorMode(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (prefs.contains(PREF_COMMENT_DEPTH_INDICATORS)) {
            return CommentDepthIndicatorUtils.sanitizeMode(
                    prefs.getString(PREF_COMMENT_DEPTH_INDICATORS, CommentDepthIndicatorUtils.MODE_THEME_DEFAULT));
        }
        if (getBooleanPref(PREF_MONOCHROME_COMMENT_DEPTH, false, ctx)) {
            return CommentDepthIndicatorUtils.MODE_MONOCHROME;
        }
        return CommentDepthIndicatorUtils.MODE_THEME_DEFAULT;
    }

    public static void setPreferredCommentDepthIndicatorMode(Context ctx, String mode) {
        String sanitizedMode = CommentDepthIndicatorUtils.sanitizeMode(mode);
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREF_COMMENT_DEPTH_INDICATORS, sanitizedMode)
                .putBoolean(PREF_MONOCHROME_COMMENT_DEPTH, CommentDepthIndicatorUtils.MODE_MONOCHROME.equals(sanitizedMode))
                .apply();
    }

    public static boolean shouldUseMonochromeCommentDepthIndicators(Context ctx) {
        return CommentDepthIndicatorUtils.MODE_MONOCHROME.equals(getPreferredCommentDepthIndicatorMode(ctx));
    }

    public static boolean shouldUseIntegratedWebView(Context ctx) {
        return getBooleanPref("pref_webview", true, ctx);
    }

    public static String shouldPreloadWebView(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizePreloadWebViewMode(prefs.getString(PREF_PRELOAD_WEBVIEW, PRELOAD_WEBVIEW_NEVER));
    }

    public static int getPreloadWebViewMinimumBattery(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return clampPercent(prefs.getInt(PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY, DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY));
    }

    public static boolean hasEnoughBatteryForWebViewPreload(Context ctx, int minimumBattery) {
        int clampedMinimumBattery = clampPercent(minimumBattery);
        if (clampedMinimumBattery <= DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY) {
            return true;
        }

        Intent batteryStatus = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryStatus == null) {
            return true;
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return true;
        }

        int batteryPercent = Math.round(level * 100f / scale);
        return batteryPercent >= clampedMinimumBattery;
    }

    public static String sanitizePreloadWebViewMode(String mode) {
        if (PRELOAD_WEBVIEW_ALWAYS.equals(mode) || PRELOAD_WEBVIEW_ONLY_WIFI.equals(mode)) {
            return mode;
        }
        return PRELOAD_WEBVIEW_NEVER;
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static boolean shouldMatchWebViewTheme(Context ctx) {
        return getBooleanPref("pref_webview_match_theme", false, ctx);
    }

    public static boolean shouldCloseWebViewOnBack(Context ctx) {
        return getBooleanPref("pref_close_webview_on_back", false, ctx);
    }

    public static boolean shouldBlockAds(Context ctx) {
        return getBooleanPref("pref_webview_adblock", false, ctx);
    }

    public static boolean shouldDisableCommentsSwipeBack(Context ctx) {
        return getBooleanPref("pref_comments_disable_swipeback", true, ctx);
    }

    public static boolean shouldShowTopLevelDepthIndicator(Context ctx) {
        return getBooleanPref("pref_top_level_thread_indicators", false, ctx);
    }

    public static boolean shouldAlwaysOpenComments(Context ctx) {
        return getBooleanPref("pref_always_open_comments", false, ctx);
    }

    public static boolean shouldUseCompactHeader(Context ctx) {
        return getBooleanPref("pref_compact_header", false, ctx);
    }

    public static boolean shouldUseLeftAlign(Context ctx) {
        return getBooleanPref("pref_left_align", false, ctx);
    }

    public static String getPreferredStoryDisplayStyle(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String style = prefs.getString(PREF_STORY_DISPLAY_STYLE, STORY_DISPLAY_STYLE_STANDARD);
        if (STORY_DISPLAY_STYLE_CARD.equals(style)) {
            return STORY_DISPLAY_STYLE_CARD;
        }
        return STORY_DISPLAY_STYLE_STANDARD;
    }

    public static boolean shouldUseCardStoryDisplayStyle(Context ctx) {
        return STORY_DISPLAY_STYLE_CARD.equals(getPreferredStoryDisplayStyle(ctx));
    }

    public static float getPreferredStoryTextSize(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        try {
            return clampStoryTextSize(Float.parseFloat(
                    prefs.getString(PREF_STORY_TEXT_SIZE, String.valueOf(DEFAULT_STORY_TEXT_SIZE))));
        } catch (ClassCastException e) {
            return clampStoryTextSize(prefs.getFloat(PREF_STORY_TEXT_SIZE, DEFAULT_STORY_TEXT_SIZE));
        } catch (NumberFormatException e) {
            return DEFAULT_STORY_TEXT_SIZE;
        }
    }

    public static float clampStoryTextSize(float textSize) {
        return Math.max(MIN_STORY_TEXT_SIZE, Math.min(MAX_STORY_TEXT_SIZE, textSize));
    }

    public static int getStoryTextSizeOffset(float textSize) {
        return Math.max(
                MIN_STORY_TEXT_SIZE_OFFSET,
                Math.min(
                        MAX_STORY_TEXT_SIZE_OFFSET,
                        Math.round((clampStoryTextSize(textSize) - DEFAULT_STORY_TEXT_SIZE)
                                / STORY_TEXT_SIZE_OFFSET_STEP)));
    }

    public static float getStoryTextSizeForOffset(int offset) {
        int clampedOffset = Math.max(MIN_STORY_TEXT_SIZE_OFFSET, Math.min(MAX_STORY_TEXT_SIZE_OFFSET, offset));
        return clampStoryTextSize(DEFAULT_STORY_TEXT_SIZE + clampedOffset * STORY_TEXT_SIZE_OFFSET_STEP);
    }

    public static float getStoryMetaTextSize(float storyTextSize) {
        float scale = clampStoryTextSize(storyTextSize) / DEFAULT_STORY_TEXT_SIZE;
        return DEFAULT_STORY_META_TEXT_SIZE * scale;
    }

    public static String getPreferredCommentDisplayStyle(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String style = prefs.getString(PREF_COMMENT_DISPLAY_STYLE, COMMENT_DISPLAY_STYLE_STANDARD);
        if (COMMENT_DISPLAY_STYLE_CARD.equals(style)) {
            return COMMENT_DISPLAY_STYLE_CARD;
        }
        return COMMENT_DISPLAY_STYLE_STANDARD;
    }

    public static boolean shouldUseCardCommentDisplayStyle(Context ctx) {
        return COMMENT_DISPLAY_STYLE_CARD.equals(getPreferredCommentDisplayStyle(ctx));
    }

    public static boolean shouldUseTransparentStatusBar(Context ctx) {
        return getBooleanPref("pref_transparent_status_bar", false, ctx);
    }

    public static boolean shouldUseSpecialNighttimeTheme(Context ctx) {
        return getBooleanPref("pref_special_nighttime", false, ctx);
    }

    public static boolean shouldUseCommentsAnimation(Context ctx) {
        return getBooleanPref("pref_comments_animation", true, ctx);
    }

    public static boolean shouldSmoothScrollComments(Context ctx) {
        return getBooleanPref("pref_comments_animation_navigation", true, ctx);
    }

    public static final String COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED = "disabled";
    public static final String COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL = "top_level";
    private static final String PREF_COMMENTS_VOLUME_NAVIGATION = "pref_comments_volume_navigation";

    public static String getCommentsVolumeNavigationMode(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString(PREF_COMMENTS_VOLUME_NAVIGATION, COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED);
    }

    public static boolean shouldUseCommentsScrollbar(Context ctx) {
        return getBooleanPref("pref_comments_scrollbar", false, ctx);
    }

    public static boolean shouldHideClicked(Context ctx) {
        return getBooleanPref(PREF_HIDE_CLICKED, false, ctx);
    }

    public static boolean shouldGrayOutClicked(Context ctx) {
        return getBooleanPref(PREF_GRAY_OUT_CLICKED, true, ctx);
    }

    public static boolean shouldUseLinkPreviewArxiv(Context ctx) {
        return getBooleanPref("pref_link_preview_arxiv", true, ctx);
    }

    public static boolean shouldUseLinkPreviewGithub(Context ctx) {
        return getBooleanPref("pref_link_preview_github", true, ctx);
    }

    public static boolean shouldUseLinkPreviewGitLab(Context ctx) {
        return getBooleanPref("pref_link_preview_gitlab", true, ctx);
    }

    public static boolean shouldUseLinkPreviewStackExchange(Context ctx) {
        return getBooleanPref("pref_link_preview_stack_exchange", true, ctx);
    }

    public static boolean shouldUseLinkPreviewWikipedia(Context ctx) {
        return getBooleanPref("pref_link_preview_wikipedia", true, ctx);
    }

    public static boolean shouldRedirectNitter(Context ctx) {
        return getBooleanPref("pref_redirect_nitter", false, ctx);
    }

    public static boolean shouldUseLinkPreviewX(Context ctx) {
        return getBooleanPref("pref_link_preview_x", false, ctx);
    }

    public static boolean shouldShowChangelog(Context ctx) {
        return getBooleanPref("pref_show_changelog", true, ctx);
    }

    public static boolean shouldUseBookmarks(Context ctx) {
        return getBooleanPref(PREF_BOOKMARKS_ENABLED, true, ctx);
    }

    public static boolean shouldSwapCommentLongPressTap(Context ctx) {
        return getBooleanPref("pref_comments_swap_long", false, ctx);
    }

    public static boolean shouldUsePaginationMode(Context ctx) {
        return getBooleanPref("pref_pagination_mode", false, ctx);
    }

    public static boolean shouldAlwaysShowTapToRefresh(Context ctx) {
        return getBooleanPref(PREF_ALWAYS_SHOW_TAP_TO_REFRESH, false, ctx);
    }

    public static boolean shouldUseAlgoliaAPI(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return "algolia".equals(prefs.getString("pref_comments_provider", "algolia"));
    }

    public static boolean getBooleanPref(String key, boolean backup, Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getBoolean(key, backup);
    }

    public static float getPreferredCommentTextSize(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        try {
            return clampCommentTextSize(Float.parseFloat(
                    prefs.getString(PREF_COMMENT_TEXT_SIZE, String.valueOf(DEFAULT_COMMENT_TEXT_SIZE))));
        } catch (ClassCastException e) {
            try {
                return clampCommentTextSize(prefs.getFloat(PREF_COMMENT_TEXT_SIZE, DEFAULT_COMMENT_TEXT_SIZE));
            } catch (ClassCastException ignored) {
                return clampCommentTextSize(prefs.getInt(PREF_COMMENT_TEXT_SIZE, Math.round(DEFAULT_COMMENT_TEXT_SIZE)));
            }
        } catch (NumberFormatException e) {
            return DEFAULT_COMMENT_TEXT_SIZE;
        }
    }

    public static float clampCommentTextSize(float textSize) {
        return Math.max(MIN_COMMENT_TEXT_SIZE, Math.min(MAX_COMMENT_TEXT_SIZE, textSize));
    }

    public static int getCommentTextSizeOffset(float textSize) {
        return Math.max(
                MIN_COMMENT_TEXT_SIZE_OFFSET,
                Math.min(
                        MAX_COMMENT_TEXT_SIZE_OFFSET,
                        Math.round((clampCommentTextSize(textSize) - DEFAULT_COMMENT_TEXT_SIZE)
                                / COMMENT_TEXT_SIZE_OFFSET_STEP)));
    }

    public static float getCommentTextSizeForOffset(int offset) {
        int clampedOffset = Math.max(MIN_COMMENT_TEXT_SIZE_OFFSET, Math.min(MAX_COMMENT_TEXT_SIZE_OFFSET, offset));
        return clampCommentTextSize(DEFAULT_COMMENT_TEXT_SIZE + clampedOffset * COMMENT_TEXT_SIZE_OFFSET_STEP);
    }

    public static String getPreferredStoryType(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String startingPage = prefs.getString("pref_default_story_type", "Top Stories");
        if ("Bookmarks".equals(startingPage) || "History".equals(startingPage)) {
            return "Top Stories";
        }
        return startingPage;
    }

    public static String getPreferredCommentSorting(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_comment_sorting", "Default");
    }

    public static String getPreferredFaviconProvider(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_favicon_provider", "Google");
    }

    public static int getBookmarksIndex(Resources res) {
        String[] sortingOptions = res.getStringArray(R.array.sorting_options);

        for (int i = sortingOptions.length - 1; i >= 0; i--) {
            if (sortingOptions[i].equals("Bookmarks")) {
                return i;
            }
        }
        // fallback
        return sortingOptions.length - 1;
    }

    public static int getHistoryIndex(Resources res) {
        String[] sortingOptions = res.getStringArray(R.array.sorting_options);

        for (int i = sortingOptions.length - 1; i >= 0; i--) {
            if (sortingOptions[i].equals("History")) {
                return i;
            }
        }
        // fallback
        return sortingOptions.length - 1;
    }

    public static int getFavoritesIndex(Resources res) {
        return getBookmarksIndex(res) + 1;
    }

    public static int getJobsIndex(Resources res) {
        String[] sortingOptions = res.getStringArray(R.array.sorting_options);

        for (int i = sortingOptions.length - 1; i >= 0; i--) {
            if (sortingOptions[i].equals("HN Jobs")) {
                return i;
            }
        }
        // fallback
        return sortingOptions.length - 2;
    }

}
