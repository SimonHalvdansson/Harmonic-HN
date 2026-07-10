package com.simon.harmonichackernews.utils;

import static com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;
import android.os.BatteryManager;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsUtils {

    public static final String PREF_THEME = "pref_theme";
    public static final String PREF_THEME_NIGHTTIME = "pref_theme_nighttime";
    public static final String DEFAULT_THEME = "material_daynight";
    public static final String DEFAULT_NIGHTTIME_THEME = "dark";
    public static final String PREF_COMMENT_DEPTH_INDICATORS = "pref_comment_depth_indicators";
    public static final String PREF_MONOCHROME_COMMENT_DEPTH = "pref_monochrome_comment_depth";
    public static final String PREF_STORY_DISPLAY_STYLE = "pref_story_display_style";
    public static final String PREF_STORY_PREVIEW_IMAGE_MODE = "pref_story_preview_image_mode";
    public static final String PREF_TINT_CARD_USING_PREVIEW = "pref_tint_card_using_preview";
    public static final String PREF_PALETTE_TINT_MODE = "pref_palette_tint_mode";
    public static final String PREF_PALETTE_TINT_STRENGTH = "pref_palette_tint_strength";
    public static final String PREF_PALETTE_TINT_COLORFULNESS = "pref_palette_tint_colorfulness";
    public static final String PREF_PALETTE_TINT_TONE = "pref_palette_tint_tone";
    public static final String PREF_STORY_TEXT_SIZE = "pref_story_text_size";
    public static final String PREF_COMPACT_POINTS = "pref_compact_points";
    public static final String PREF_INCLUDE_TOP_LEVEL_DOMAIN = "pref_include_top_level_domain";
    public static final String PREF_COMMENT_DISPLAY_STYLE = "pref_comment_display_style";
    public static final String PREF_COMMENT_TEXT_SIZE = "pref_comment_text_size";
    public static final String PREF_ENABLE_COMMENTS_HEADER_TINT = "pref_enable_comments_header_tint";
    public static final String PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE = "pref_enable_comments_header_preview_image";
    public static final String PREF_COLLECT_LINKS_IN_COMMENTS = "pref_collect_links_in_comments";
    public static final String PREF_FONT = "pref_font";
    public static final String PREF_TRANSLUCENT_STATUS_BAR = "pref_translucent_status_bar";
    public static final String PREF_BOOKMARKS_ENABLED = "pref_bookmarks_enabled";
    public static final String PREF_GRAY_OUT_CLICKED = "pref_gray_out_clicked";
    public static final String PREF_HIDE_CLICKED = "pref_hide_clicked";
    public static final String PREF_ALWAYS_SHOW_TAP_TO_REFRESH = "pref_always_show_tap_to_refresh";
    public static final String PREF_PRELOAD_WEBVIEW = "pref_preload_webview";
    public static final String PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY = "pref_preload_webview_minimum_battery";
    public static final String PREF_WEBVIEW_READER_MODE_ENABLED = "pref_webview_reader_mode_enabled";
    public static final String PREF_WEBVIEW_READER_MODE_DEFAULT = "pref_webview_reader_mode_default";
    public static final String PREF_WEBVIEW_READER_MODE_FONT = "pref_webview_reader_mode_font";
    public static final String PREF_WEBVIEW_READER_MODE_FONT_SIZE = "pref_webview_reader_mode_font_size";
    public static final String PREF_ARCHIVE_REDIRECT_DOMAINS = "pref_archive_redirect_domains";
    public static final String PREF_STORIES_TO_CACHE = "pref_stories_to_cache";
    public static final String PREF_FAVICON_PROVIDER = "pref_favicon_provider";
    public static final String PREF_ADDITIONAL_FRONTPAGES = "pref_additional_frontpages";
    public static final String FRONT_PAGE_CLASSIC = "Classic";
    public static final String FRONT_PAGE_BEST_COMMENTS = "Best Comments";
    public static final String FRONT_PAGE_HIGHLIGHTS = "Highlights";
    public static final String FRONT_PAGE_ACTIVE = "Active";
    public static final String FRONT_PAGE_FRONT = "Front";
    public static final String PRELOAD_WEBVIEW_ALWAYS = "always";
    public static final String PRELOAD_WEBVIEW_ONLY_WIFI = "onlywifi";
    public static final String PRELOAD_WEBVIEW_NEVER = "never";
    public static final String FAVICON_PROVIDER_GOOGLE = "Google";
    public static final String FAVICON_PROVIDER_DUCKDUCKGO = "DuckDuckGo";
    public static final String FAVICON_PROVIDER_TWENTY = "Twenty icons";
    public static final int DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY = 0;
    public static final int DEFAULT_STORIES_TO_CACHE = 20;
    public static final int MIN_STORIES_TO_CACHE = 5;
    public static final int MAX_STORIES_TO_CACHE = 200;
    public static final int STORIES_TO_CACHE_STEP = 5;
    public static final String STORY_DISPLAY_STYLE_STANDARD = "standard";
    public static final String STORY_DISPLAY_STYLE_CARD = "card";
    public static final String STORY_PREVIEW_IMAGE_OFF = "off";
    public static final String STORY_PREVIEW_IMAGE_SMALL = "small";
    public static final String STORY_PREVIEW_IMAGE_LARGE = "large";
    public static final String PALETTE_TINT_DEFAULT = "default";
    public static final String PALETTE_TINT_VIBRANT = "vibrant";
    public static final String PALETTE_TINT_DOMINANT = "dominant";
    public static final int DEFAULT_PALETTE_TINT_STRENGTH = 100;
    public static final int DEFAULT_PALETTE_TINT_COLORFULNESS = 110;
    public static final int DEFAULT_PALETTE_TINT_TONE = 0;
    public static final int MIN_PALETTE_TINT_STRENGTH = 0;
    public static final int MAX_PALETTE_TINT_STRENGTH = 200;
    public static final int MIN_PALETTE_TINT_COLORFULNESS = 0;
    public static final int MAX_PALETTE_TINT_COLORFULNESS = 200;
    public static final int MIN_PALETTE_TINT_TONE = -20;
    public static final int MAX_PALETTE_TINT_TONE = 20;
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
    public static final int DEFAULT_READER_MODE_FONT_SIZE = 18;
    public static final int MIN_READER_MODE_FONT_SIZE = 14;
    public static final int MAX_READER_MODE_FONT_SIZE = 24;
    public static final float MIN_COMMENT_TEXT_SIZE = DEFAULT_COMMENT_TEXT_SIZE
            + MIN_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP;
    public static final float MAX_COMMENT_TEXT_SIZE = DEFAULT_COMMENT_TEXT_SIZE
            + MAX_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP;
    public static final String FAVORITES_LABEL = "Favorites";
    public static final String UPVOTED_LABEL = "Upvoted";

    public static boolean isAutoTheme(String theme) {
        return DEFAULT_THEME.equals(theme)
                || "darklight_daynight".equals(theme)
                || "amoledwhite_daynight".equals(theme);
    }

    public static boolean isDarkTheme(String theme) {
        return "material_dark".equals(theme)
                || "dark".equals(theme)
                || "hacker".equals(theme)
                || "amoled".equals(theme)
                || "gray".equals(theme);
    }

    public static String getSelectableNighttimeTheme(String theme) {
        if (TextUtils.isEmpty(theme) || !isDarkTheme(theme)) {
            return DEFAULT_NIGHTTIME_THEME;
        }
        return theme;
    }

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

    public static boolean shouldUseCompactPoints(Context ctx) {
        return getBooleanPref(PREF_COMPACT_POINTS, false, ctx);
    }

    public static boolean shouldIncludeTopLevelDomain(Context ctx) {
        return getBooleanPref(PREF_INCLUDE_TOP_LEVEL_DOMAIN, true, ctx);
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
        if (STORY_PREVIEW_IMAGE_OFF.equals(mode)
                || STORY_PREVIEW_IMAGE_SMALL.equals(mode)
                || STORY_PREVIEW_IMAGE_LARGE.equals(mode)) {
            return mode;
        }
        return STORY_PREVIEW_IMAGE_SMALL;
    }

    public static boolean shouldCollapseParent(Context ctx) {
        return getBooleanPref("pref_collapse_parent", false, ctx);
    }

    public static boolean shouldShowIndex(Context ctx) {
        return getBooleanPref("pref_show_index", true, ctx);
    }

    public static boolean shouldTintCardUsingPreview(Context ctx) {
        return getBooleanPref(PREF_TINT_CARD_USING_PREVIEW, true, ctx);
    }

    public static boolean shouldShowCommentsHeaderPreviewImage(Context ctx) {
        return !STORY_PREVIEW_IMAGE_OFF.equals(getPreferredStoryPreviewImageMode(ctx))
                && getBooleanPref(PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE, true, ctx);
    }

    public static boolean shouldTintCommentsHeader(Context ctx) {
        return shouldTintCardUsingPreview(ctx)
                && getBooleanPref(PREF_ENABLE_COMMENTS_HEADER_TINT, true, ctx);
    }

    public static String getPreferredPaletteTintMode(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizePaletteTintMode(prefs.getString(PREF_PALETTE_TINT_MODE, PALETTE_TINT_DEFAULT));
    }

    public static String getPreferredPaletteTintConfigKey(Context ctx) {
        return buildPaletteTintConfigKey(
                getPreferredPaletteTintMode(ctx),
                getPreferredPaletteTintStrength(ctx),
                getPreferredPaletteTintColorfulness(ctx),
                getPreferredPaletteTintTone(ctx));
    }

    public static void setPreferredPaletteTintMode(Context ctx, String mode) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREF_PALETTE_TINT_MODE, sanitizePaletteTintMode(mode))
                .apply();
    }

    public static void setPreferredPaletteTintSettings(
            Context ctx,
            String mode,
            int strength,
            int colorfulness,
            int tone) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREF_PALETTE_TINT_MODE, sanitizePaletteTintMode(mode))
                .putInt(PREF_PALETTE_TINT_STRENGTH, clampPaletteTintStrength(strength))
                .putInt(PREF_PALETTE_TINT_COLORFULNESS, clampPaletteTintColorfulness(colorfulness))
                .putInt(PREF_PALETTE_TINT_TONE, clampPaletteTintTone(tone))
                .apply();
    }

    public static void clearPreferredPaletteTintMode(Context ctx) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .remove(PREF_PALETTE_TINT_MODE)
                .remove(PREF_PALETTE_TINT_STRENGTH)
                .remove(PREF_PALETTE_TINT_COLORFULNESS)
                .remove(PREF_PALETTE_TINT_TONE)
                .apply();
    }

    public static int getPreferredPaletteTintStrength(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return clampPaletteTintStrength(prefs.getInt(
                PREF_PALETTE_TINT_STRENGTH,
                DEFAULT_PALETTE_TINT_STRENGTH));
    }

    public static int getPreferredPaletteTintColorfulness(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return clampPaletteTintColorfulness(prefs.getInt(
                PREF_PALETTE_TINT_COLORFULNESS,
                DEFAULT_PALETTE_TINT_COLORFULNESS));
    }

    public static int getPreferredPaletteTintTone(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return clampPaletteTintTone(prefs.getInt(
                PREF_PALETTE_TINT_TONE,
                DEFAULT_PALETTE_TINT_TONE));
    }

    public static String sanitizePaletteTintMode(String mode) {
        String modePart = getPaletteTintModePart(mode);
        if (PALETTE_TINT_VIBRANT.equals(modePart)
                || PALETTE_TINT_DOMINANT.equals(modePart)) {
            return modePart;
        }
        return PALETTE_TINT_DEFAULT;
    }

    public static String buildPaletteTintConfigKey(
            String mode,
            int strength,
            int colorfulness,
            int tone) {
        return sanitizePaletteTintMode(mode)
                + "|"
                + clampPaletteTintStrength(strength)
                + "|"
                + clampPaletteTintColorfulness(colorfulness)
                + "|"
                + clampPaletteTintTone(tone);
    }

    public static String getPaletteTintConfigKey(String modeOrConfigKey) {
        return buildPaletteTintConfigKey(
                modeOrConfigKey,
                getPaletteTintStrength(modeOrConfigKey),
                getPaletteTintColorfulness(modeOrConfigKey),
                getPaletteTintTone(modeOrConfigKey));
    }

    public static int getPaletteTintStrength(String modeOrConfigKey) {
        return clampPaletteTintStrength(getPaletteTintConfigInt(
                modeOrConfigKey,
                1,
                DEFAULT_PALETTE_TINT_STRENGTH));
    }

    public static int getPaletteTintColorfulness(String modeOrConfigKey) {
        return clampPaletteTintColorfulness(getPaletteTintConfigInt(
                modeOrConfigKey,
                2,
                DEFAULT_PALETTE_TINT_COLORFULNESS));
    }

    public static int getPaletteTintTone(String modeOrConfigKey) {
        return clampPaletteTintTone(getPaletteTintConfigInt(
                modeOrConfigKey,
                3,
                DEFAULT_PALETTE_TINT_TONE));
    }

    public static float getPaletteTintStrengthMultiplier(String modeOrConfigKey) {
        return getPaletteTintStrength(modeOrConfigKey) / 100f;
    }

    public static float getPaletteTintColorfulnessMultiplier(String modeOrConfigKey) {
        return getPaletteTintColorfulness(modeOrConfigKey) / 100f;
    }

    public static float getPaletteTintToneOffset(String modeOrConfigKey) {
        return getPaletteTintTone(modeOrConfigKey) / 100f;
    }

    public static boolean isDefaultPaletteTintTuning(Context ctx) {
        return getPreferredPaletteTintStrength(ctx) == DEFAULT_PALETTE_TINT_STRENGTH
                && getPreferredPaletteTintColorfulness(ctx) == DEFAULT_PALETTE_TINT_COLORFULNESS
                && getPreferredPaletteTintTone(ctx) == DEFAULT_PALETTE_TINT_TONE;
    }

    public static String getPreferredPaletteTintSummary(Context ctx) {
        String label = getPaletteTintModeLabel(getPreferredPaletteTintMode(ctx));
        if (isDefaultPaletteTintTuning(ctx)) {
            return label;
        }
        return label + ", adjusted";
    }

    public static String getPaletteTintModeLabel(String mode) {
        switch (sanitizePaletteTintMode(mode)) {
            case PALETTE_TINT_VIBRANT:
                return "Vibrant";
            case PALETTE_TINT_DOMINANT:
                return "Dominant";
            case PALETTE_TINT_DEFAULT:
            default:
                return "Muted";
        }
    }

    public static int clampPaletteTintStrength(int strength) {
        return Math.max(MIN_PALETTE_TINT_STRENGTH, Math.min(MAX_PALETTE_TINT_STRENGTH, strength));
    }

    public static int clampPaletteTintColorfulness(int colorfulness) {
        return Math.max(MIN_PALETTE_TINT_COLORFULNESS, Math.min(MAX_PALETTE_TINT_COLORFULNESS, colorfulness));
    }

    public static int clampPaletteTintTone(int tone) {
        return Math.max(MIN_PALETTE_TINT_TONE, Math.min(MAX_PALETTE_TINT_TONE, tone));
    }

    private static String getPaletteTintModePart(String modeOrConfigKey) {
        if (modeOrConfigKey == null) {
            return PALETTE_TINT_DEFAULT;
        }

        int separatorIndex = modeOrConfigKey.indexOf('|');
        if (separatorIndex < 0) {
            return modeOrConfigKey;
        }
        return modeOrConfigKey.substring(0, separatorIndex);
    }

    private static int getPaletteTintConfigInt(
            String modeOrConfigKey,
            int partIndex,
            int defaultValue) {
        if (modeOrConfigKey == null) {
            return defaultValue;
        }

        String[] parts = modeOrConfigKey.split("\\|");
        if (parts.length <= partIndex) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(parts[partIndex]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
        if ("hacker".equals(ThemeUtils.getPreferredTheme(ctx))) {
            return "jetbrainsmono";
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizeFont(prefs.getString(PREF_FONT, "googlesansflexrounded"));
    }

    public static void setPreferredFont(Context ctx, String font) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREF_FONT, sanitizeFont(font))
                .apply();
    }

    public static String getPreferredFontLabel(Context ctx) {
        return getFontLabel(ctx, getPreferredFont(ctx));
    }

    public static String getFontLabel(Context ctx, String font) {
        String sanitizedFont = sanitizeFont(font);
        String[] entries = ctx.getResources().getStringArray(R.array.font_entries);
        String[] values = ctx.getResources().getStringArray(R.array.font_values);
        for (int i = 0; i < Math.min(entries.length, values.length); i++) {
            if (sanitizedFont.equals(values[i])) {
                return entries[i];
            }
        }
        return entries.length > 0 ? entries[0] : sanitizedFont;
    }

    public static String getPreferredReaderModeFont(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizeReaderModeFont(prefs.getString(PREF_WEBVIEW_READER_MODE_FONT, "googlesansflexrounded"));
    }

    public static void setPreferredReaderModeFont(Context ctx, String font) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putString(PREF_WEBVIEW_READER_MODE_FONT, sanitizeReaderModeFont(font))
                .apply();
    }

    public static String getPreferredReaderModeFontLabel(Context ctx) {
        return getReaderModeFontLabel(ctx, getPreferredReaderModeFont(ctx));
    }

    public static String getReaderModeFontLabel(Context ctx, String font) {
        return getFontLabel(ctx, sanitizeReaderModeFont(font));
    }

    public static String sanitizeFont(String font) {
        if ("productsans".equals(font)
                || "googlesansflexrounded".equals(font)
                || "devicedefault".equals(font)
                || "verdana".equals(font)
                || "jetbrainsmono".equals(font)
                || "googlesanscode".equals(font)
                || "georgia".equals(font)
                || "robotoslab".equals(font)) {
            return font;
        }
        return "googlesansflexrounded";
    }

    public static String sanitizeReaderModeFont(String font) {
        if ("georgia".equals(font)
                || "productsans".equals(font)
                || "googlesansflexrounded".equals(font)
                || "verdana".equals(font)
                || "robotoslab".equals(font)
                || "googlesanscode".equals(font)
                || "jetbrainsmono".equals(font)
                || "devicedefault".equals(font)) {
            return font;
        }
        return "googlesansflexrounded";
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

    public static int getStoriesToCache(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizeStoriesToCache(prefs.getInt(PREF_STORIES_TO_CACHE, DEFAULT_STORIES_TO_CACHE));
    }

    public static void setStoriesToCache(Context ctx, int value) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
                .edit()
                .putInt(PREF_STORIES_TO_CACHE, sanitizeStoriesToCache(value))
                .apply();
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

    public static int sanitizeStoriesToCache(int value) {
        int clampedValue = Math.max(MIN_STORIES_TO_CACHE, Math.min(MAX_STORIES_TO_CACHE, value));
        return Math.round(clampedValue / (float) STORIES_TO_CACHE_STEP) * STORIES_TO_CACHE_STEP;
    }

    public static boolean shouldMatchWebViewTheme(Context ctx) {
        return getBooleanPref("pref_webview_match_theme", false, ctx);
    }

    public static boolean shouldUseReaderMode(Context ctx) {
        return getBooleanPref(PREF_WEBVIEW_READER_MODE_ENABLED, true, ctx);
    }

    public static boolean shouldUseReaderModeByDefault(Context ctx) {
        return shouldUseReaderMode(ctx) && getBooleanPref(PREF_WEBVIEW_READER_MODE_DEFAULT, false, ctx);
    }

    public static int getReaderModeFontSize(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return clampReaderModeFontSize(prefs.getInt(PREF_WEBVIEW_READER_MODE_FONT_SIZE, DEFAULT_READER_MODE_FONT_SIZE));
    }

    public static int clampReaderModeFontSize(int textSize) {
        return Math.max(MIN_READER_MODE_FONT_SIZE, Math.min(MAX_READER_MODE_FONT_SIZE, textSize));
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

    public static boolean shouldUseTranslucentStatusBar(Context ctx) {
        return getBooleanPref(PREF_TRANSLUCENT_STATUS_BAR, true, ctx);
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

    public static boolean shouldCollectLinksInComments(Context ctx) {
        return getBooleanPref(PREF_COLLECT_LINKS_IN_COMMENTS, true, ctx);
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

    public static ArrayList<String> getArchiveRedirectDomains(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return parseArchiveRedirectDomains(prefs.getString(PREF_ARCHIVE_REDIRECT_DOMAINS, ""));
    }

    public static ArrayList<String> parseArchiveRedirectDomains(String value) {
        ArrayList<String> domains = new ArrayList<>();
        if (TextUtils.isEmpty(value)) {
            return domains;
        }

        String[] parts = value.split(",");
        for (String part : parts) {
            String domain = normalizeArchiveRedirectDomain(part);
            if (!TextUtils.isEmpty(domain) && !containsDomain(domains, domain)) {
                domains.add(domain);
            }
        }
        return domains;
    }

    public static String normalizeArchiveRedirectDomain(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.US);
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (!normalized.contains("://") && normalized.contains("/")) {
            normalized = "https://" + normalized;
        }

        Uri uri = Uri.parse(normalized);
        String host = uri.getHost();
        if (!TextUtils.isEmpty(host)) {
            normalized = host;
        } else {
            int pathStart = normalized.indexOf('/');
            if (pathStart >= 0) {
                normalized = normalized.substring(0, pathStart);
            }
        }

        int portStart = normalized.indexOf(':');
        if (portStart >= 0) {
            normalized = normalized.substring(0, portStart);
        }
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }

        if (!normalized.contains(".") || !normalized.matches("[a-z0-9.-]+")) {
            return "";
        }
        return normalized;
    }

    public static String getArchiveRedirectUrl(Context ctx, String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }

        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return null;
        }

        String host = uri.getHost();
        if (TextUtils.isEmpty(host) || isArchiveHost(host)) {
            return null;
        }

        String domain = normalizeArchiveRedirectDomain(host);
        if (TextUtils.isEmpty(domain)) {
            return null;
        }

        for (String archiveRedirectDomain : getArchiveRedirectDomains(ctx)) {
            if (domain.equals(archiveRedirectDomain) || domain.endsWith("." + archiveRedirectDomain)) {
                return "https://archive.is/newest/" + Uri.encode(url);
            }
        }
        return null;
    }

    private static boolean isArchiveHost(String host) {
        String domain = normalizeArchiveRedirectDomain(host);
        return "archive.is".equals(domain)
                || "archive.today".equals(domain)
                || "archive.ph".equals(domain)
                || "archive.vn".equals(domain)
                || "archive.md".equals(domain);
    }

    private static boolean containsDomain(List<String> domains, String domain) {
        for (String existing : domains) {
            if (existing.equalsIgnoreCase(domain)) {
                return true;
            }
        }
        return false;
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
        if ("Bookmarks".equals(startingPage)
                || "History".equals(startingPage)
                || (isAdditionalFrontpageLabel(startingPage) && !isAdditionalFrontpageEnabled(ctx, startingPage))) {
            return "Top Stories";
        }
        return startingPage;
    }

    public static String[] getAdditionalFrontpageLabels() {
        return new String[] {
                FRONT_PAGE_CLASSIC,
                FRONT_PAGE_BEST_COMMENTS,
                FRONT_PAGE_HIGHLIGHTS,
                FRONT_PAGE_ACTIVE,
                FRONT_PAGE_FRONT
        };
    }

    public static boolean isAdditionalFrontpageLabel(String label) {
        for (String frontpage : getAdditionalFrontpageLabels()) {
            if (frontpage.equals(label)) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> getEnabledAdditionalFrontpages(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        Set<String> enabled = prefs.getStringSet(PREF_ADDITIONAL_FRONTPAGES, new HashSet<>());
        return sanitizeAdditionalFrontpages(enabled);
    }

    public static boolean isAdditionalFrontpageEnabled(Context ctx, String label) {
        return getEnabledAdditionalFrontpages(ctx).contains(label);
    }

    public static Set<String> sanitizeAdditionalFrontpages(Set<String> enabled) {
        HashSet<String> sanitized = new HashSet<>();
        if (enabled == null) {
            return sanitized;
        }

        for (String frontpage : getAdditionalFrontpageLabels()) {
            if (enabled.contains(frontpage)) {
                sanitized.add(frontpage);
            }
        }
        return sanitized;
    }

    public static String summarizeAdditionalFrontpages(Context ctx) {
        return summarizeAdditionalFrontpages(getEnabledAdditionalFrontpages(ctx));
    }

    public static String summarizeAdditionalFrontpages(Set<String> enabled) {
        Set<String> sanitized = sanitizeAdditionalFrontpages(enabled);
        if (sanitized.isEmpty()) {
            return "Off";
        }

        StringBuilder summary = new StringBuilder();
        for (String frontpage : getAdditionalFrontpageLabels()) {
            if (!sanitized.contains(frontpage)) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(frontpage);
        }
        return summary.toString();
    }

    public static String getPreferredCommentSorting(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return prefs.getString("pref_comment_sorting", "Default");
    }

    public static String getPreferredFaviconProvider(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sanitizeFaviconProvider(prefs.getString(PREF_FAVICON_PROVIDER, FAVICON_PROVIDER_GOOGLE));
    }

    public static String sanitizeFaviconProvider(String provider) {
        if (FAVICON_PROVIDER_DUCKDUCKGO.equals(provider) || FAVICON_PROVIDER_TWENTY.equals(provider)) {
            return provider;
        }
        return FAVICON_PROVIDER_GOOGLE;
    }

    public static int getFaviconProviderIconResource(String provider) {
        switch (sanitizeFaviconProvider(provider)) {
            case FAVICON_PROVIDER_DUCKDUCKGO:
                return R.drawable.ic_favicon_provider_duckduckgo;
            case FAVICON_PROVIDER_TWENTY:
                return R.drawable.ic_favicon_provider_twenty;
            case FAVICON_PROVIDER_GOOGLE:
            default:
                return R.drawable.ic_favicon_provider_google;
        }
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
