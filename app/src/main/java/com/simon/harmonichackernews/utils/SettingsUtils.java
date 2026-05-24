package com.simon.harmonichackernews.utils;

import static com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.util.HashSet;
import java.util.Set;

public class SettingsUtils {

    public static final String PREF_COMMENT_DEPTH_INDICATORS = "pref_comment_depth_indicators";
    public static final String PREF_MONOCHROME_COMMENT_DEPTH = "pref_monochrome_comment_depth";
    public static final String PREF_STORY_DISPLAY_STYLE = "pref_story_display_style";
    public static final String PREF_STORY_PREVIEW_IMAGE_MODE = "pref_story_preview_image_mode";
    public static final String PREF_COMMENT_DISPLAY_STYLE = "pref_comment_display_style";
    public static final String PREF_BOOKMARKS_ENABLED = "pref_bookmarks_enabled";
    public static final String PREF_GRAY_OUT_CLICKED = "pref_gray_out_clicked";
    public static final String PREF_HIDE_CLICKED = "pref_hide_clicked";
    public static final String PREF_ALWAYS_SHOW_TAP_TO_REFRESH = "pref_always_show_tap_to_refresh";
    public static final String STORY_DISPLAY_STYLE_STANDARD = "standard";
    public static final String STORY_DISPLAY_STYLE_CARD = "card";
    public static final String STORY_PREVIEW_IMAGE_OFF = "off";
    public static final String STORY_PREVIEW_IMAGE_SMALL = "small";
    public static final String STORY_PREVIEW_IMAGE_LARGE = "large";
    public static final String COMMENT_DISPLAY_STYLE_STANDARD = STORY_DISPLAY_STYLE_STANDARD;
    public static final String COMMENT_DISPLAY_STYLE_CARD = STORY_DISPLAY_STYLE_CARD;
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
        return prefs.getString("pref_preload_webview", "never");
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

    public static boolean shouldUseCommentsAnimationNavigation(Context ctx) {
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

    public static int getPreferredCommentTextSize(Context ctx) {
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_comment_text_size", "15"));
    }

    public static String getPreferredStoryType(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String preferredStoryType = prefs.getString("pref_default_story_type", "Top Stories");
        if ("Bookmarks".equals(preferredStoryType) || "History".equals(preferredStoryType)) {
            return "Top Stories";
        }
        return preferredStoryType;
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
