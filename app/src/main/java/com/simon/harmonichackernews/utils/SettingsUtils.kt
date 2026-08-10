package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.Uri
import android.os.BatteryManager
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.AdditionalFrontpagePreferences
import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryCachePreferences
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils.sanitizeMode
import com.simon.harmonichackernews.utils.PreviewImageTintUtils.clearTintColorCaches
import com.simon.harmonichackernews.utils.Utils.GLOBAL_SHARED_PREFERENCES_KEY
import java.util.ArrayList
import java.util.HashSet
import java.util.List
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SettingsUtils {
    const val PREF_THEME: String = "pref_theme"
    const val PREF_THEME_NIGHTTIME: String = "pref_theme_nighttime"
    const val DEFAULT_THEME: String = "material_daynight"
    const val DEFAULT_NIGHTTIME_THEME: String = "dark"
    const val PREF_COMMENT_DEPTH_INDICATORS: String = "pref_comment_depth_indicators"
    const val PREF_COMMENT_DIVIDERS: String = "pref_comment_dividers"
    const val PREF_MONOCHROME_COMMENT_DEPTH: String = "pref_monochrome_comment_depth"
    const val PREF_STORY_DISPLAY_STYLE: String = "pref_story_display_style"
    const val PREF_STORY_PREVIEW_IMAGE_MODE: String = "pref_story_preview_image_mode"
    const val PREF_STORY_PREVIEW_IMAGE_BORDERLESS: String = "pref_story_preview_image_borderless"
    const val PREF_SHOW_STORY_SUMMARY: String = "pref_show_story_summary"
    const val PREF_TINT_CARD_USING_PREVIEW: String = "pref_tint_card_using_preview"
    const val PREF_PALETTE_TINT_MODE: String = "pref_palette_tint_mode"
    const val PREF_PALETTE_TINT_STRENGTH: String = "pref_palette_tint_strength"
    const val PREF_PALETTE_TINT_COLORFULNESS: String = "pref_palette_tint_colorfulness"
    const val PREF_PALETTE_TINT_TONE: String = "pref_palette_tint_tone"
    const val PREF_STORY_TEXT_SIZE: String = "pref_story_text_size"
    const val PREF_COMPACT_POINTS: String = "pref_compact_points"
    const val PREF_INCLUDE_TOP_LEVEL_DOMAIN: String = "pref_include_top_level_domain"
    const val PREF_COMMENT_DISPLAY_STYLE: String = "pref_comment_display_style"
    const val PREF_COMMENT_CARD_BORDER: String = "pref_comment_card_border"
    const val PREF_HIGHLIGHT_COMMENT_META: String = "pref_highlight_comment_meta"
    const val PREF_COMMENT_TEXT_SIZE: String = "pref_comment_text_size"
    const val PREF_ENABLE_COMMENTS_HEADER_TINT: String = "pref_enable_comments_header_tint"
    const val PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE: String =
        "pref_enable_comments_header_preview_image"
    const val PREF_COLLECT_LINKS_IN_COMMENTS: String = "pref_collect_links_in_comments"
    const val PREF_FONT: String = "pref_font"
    const val PREF_BOOKMARKS_ENABLED: String = "pref_bookmarks_enabled"
    const val PREF_GRAY_OUT_CLICKED: String = "pref_gray_out_clicked"
    const val PREF_HIDE_CLICKED: String = "pref_hide_clicked"
    const val PREF_ALWAYS_SHOW_TAP_TO_REFRESH: String = "pref_always_show_tap_to_refresh"
    const val PREF_PRELOAD_WEBVIEW: String = "pref_preload_webview"
    const val PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY: String = "pref_preload_webview_minimum_battery"
    const val PREF_WEBVIEW_READER_MODE_ENABLED: String = "pref_webview_reader_mode_enabled"
    const val PREF_WEBVIEW_READER_MODE_DEFAULT: String = "pref_webview_reader_mode_default"
    const val PREF_WEBVIEW_READER_MODE_FONT: String = "pref_webview_reader_mode_font"
    const val PREF_WEBVIEW_READER_MODE_FONT_SIZE: String = "pref_webview_reader_mode_font_size"
    const val PREF_ARCHIVE_REDIRECT_DOMAINS: String = "pref_archive_redirect_domains"
    const val PREF_STORIES_TO_CACHE: String = "pref_stories_to_cache"
    const val PREF_FAVICON_PROVIDER: String = "pref_favicon_provider"
    const val PREF_ADDITIONAL_FRONTPAGES: String = "pref_additional_frontpages"
    const val FRONT_PAGE_CLASSIC: String = "Classic"
    const val FRONT_PAGE_BEST_COMMENTS: String = "Best Comments"
    const val FRONT_PAGE_HIGHLIGHTS: String = "Highlights"
    const val FRONT_PAGE_ACTIVE: String = "Active"
    const val FRONT_PAGE_FRONT: String = "Front"
    const val PRELOAD_WEBVIEW_ALWAYS: String = "always"
    const val PRELOAD_WEBVIEW_ONLY_WIFI: String = "onlywifi"
    const val PRELOAD_WEBVIEW_NEVER: String = "never"
    const val FAVICON_PROVIDER_GOOGLE: String = "Google"
    const val FAVICON_PROVIDER_DUCKDUCKGO: String = "DuckDuckGo"
    const val FAVICON_PROVIDER_TWENTY: String = "Twenty icons"
    const val DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY: Int = 0
    const val DEFAULT_STORIES_TO_CACHE: Int = 20
    const val MIN_STORIES_TO_CACHE: Int = 5
    const val MAX_STORIES_TO_CACHE: Int = 200
    const val STORIES_TO_CACHE_STEP: Int = 5
    const val STORY_DISPLAY_STYLE_STANDARD: String = "standard"
    const val STORY_DISPLAY_STYLE_CARD: String = "card"
    const val STORY_PREVIEW_IMAGE_OFF: String = "off"
    const val STORY_PREVIEW_IMAGE_SMALL: String = "small"
    const val STORY_PREVIEW_IMAGE_LARGE: String = "large"
    const val PALETTE_TINT_DEFAULT: String = "default"
    const val PALETTE_TINT_VIBRANT: String = "vibrant"
    const val PALETTE_TINT_DOMINANT: String = "dominant"
    const val DEFAULT_PALETTE_TINT_STRENGTH: Int = 100
    const val DEFAULT_PALETTE_TINT_COLORFULNESS: Int = 110
    const val DEFAULT_PALETTE_TINT_TONE: Int = 0
    const val MIN_PALETTE_TINT_STRENGTH: Int = 0
    const val MAX_PALETTE_TINT_STRENGTH: Int = 200
    const val MIN_PALETTE_TINT_COLORFULNESS: Int = 0
    const val MAX_PALETTE_TINT_COLORFULNESS: Int = 200
    val MIN_PALETTE_TINT_TONE: Int = -20
    const val MAX_PALETTE_TINT_TONE: Int = 20
    val COMMENT_DISPLAY_STYLE_STANDARD: String = STORY_DISPLAY_STYLE_STANDARD
    val COMMENT_DISPLAY_STYLE_CARD: String = STORY_DISPLAY_STYLE_CARD
    const val DEFAULT_STORY_TEXT_SIZE: Float = TextPreferences.DEFAULT_STORY_TEXT_SIZE
    const val DEFAULT_STORY_META_TEXT_SIZE: Float = 13f
    val MIN_STORY_TEXT_SIZE_OFFSET: Int = -6
    const val MAX_STORY_TEXT_SIZE_OFFSET: Int = 6
    const val STORY_TEXT_SIZE_OFFSET_STEP: Float = 0.5f
    val MIN_STORY_TEXT_SIZE: Float = (DEFAULT_STORY_TEXT_SIZE
            + MIN_STORY_TEXT_SIZE_OFFSET * STORY_TEXT_SIZE_OFFSET_STEP)
    val MAX_STORY_TEXT_SIZE: Float = (DEFAULT_STORY_TEXT_SIZE
            + MAX_STORY_TEXT_SIZE_OFFSET * STORY_TEXT_SIZE_OFFSET_STEP)
    const val DEFAULT_COMMENT_TEXT_SIZE: Float = TextPreferences.DEFAULT_COMMENT_TEXT_SIZE
    val MIN_COMMENT_TEXT_SIZE_OFFSET: Int = -6
    const val MAX_COMMENT_TEXT_SIZE_OFFSET: Int = 6
    const val COMMENT_TEXT_SIZE_OFFSET_STEP: Float = 0.5f
    const val DEFAULT_READER_MODE_FONT_SIZE: Int = 18
    const val MIN_READER_MODE_FONT_SIZE: Int = 14
    const val MAX_READER_MODE_FONT_SIZE: Int = 24
    val MIN_COMMENT_TEXT_SIZE: Float = (DEFAULT_COMMENT_TEXT_SIZE
            + MIN_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP)
    val MAX_COMMENT_TEXT_SIZE: Float = (DEFAULT_COMMENT_TEXT_SIZE
            + MAX_COMMENT_TEXT_SIZE_OFFSET * COMMENT_TEXT_SIZE_OFFSET_STEP)
    const val FAVORITES_LABEL: String = "Favorites"
    const val UPVOTED_LABEL: String = "Upvoted"

    fun isAutoTheme(theme: String?): Boolean {
        return DEFAULT_THEME == theme
                || "darklight_daynight" == theme
                || "amoledwhite_daynight" == theme
    }

    fun isDarkTheme(theme: String?): Boolean {
        return "material_dark" == theme
                || "dark" == theme
                || "hacker" == theme
                || "amoled" == theme
                || "gray" == theme
    }

    fun getSelectableNighttimeTheme(theme: String): String {
        if (TextUtils.isEmpty(theme) || !isDarkTheme(theme)) {
            return DEFAULT_NIGHTTIME_THEME
        }
        return theme
    }

    fun readIntSetFromSharedPreferences(ctx: Context, key: String?): MutableSet<Int> {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val emptyBackup: MutableSet<String> = HashSet<String>()
        var stringSet = sharedPref.getStringSet(key, emptyBackup)
        if (stringSet == null) {
            stringSet = emptyBackup
        } else {
            stringSet = HashSet<String>(stringSet)
        }

        val intSet: MutableSet<Int> = HashSet(stringSet.size)
        for (string in stringSet) {
            intSet.add(string.toInt())
        }
        return intSet
    }

    fun saveIntSetToSharedPreferences(ctx: Context, key: String?, set: Set<Int>) {
        val stringSet: MutableSet<String> = HashSet<String>(set.size)

        for (integer in set) {
            stringSet.add(integer.toString())
        }

        saveStringSetToSharedPreferences(ctx, key, stringSet)
    }

    fun readStringSetFromSharedPreferences(ctx: Context, key: String?): MutableSet<String> {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val emptyBackup: MutableSet<String> = HashSet<String>()
        val stringSet = sharedPref.getStringSet(key, emptyBackup) ?: emptyBackup
        return HashSet(stringSet)
    }

    fun saveStringSetToSharedPreferences(ctx: Context, key: String?, set: MutableSet<String>?) {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        editor.putStringSet(key, if (set == null) null else HashSet<String?>(set)).apply()
    }

    fun saveStringToSharedPreferences(ctx: Context, key: String?, text: String?) {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()

        editor.putString(key, text).apply()
    }

    fun readStringFromSharedPreferences(ctx: Context, key: String?): String? {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        return sharedPref.getString(key, null)
    }

    fun readStringFromSharedPreferences(ctx: Context, key: String?, fallback: String?): String? {
        val sharedPref =
            ctx.getSharedPreferences(Utils.GLOBAL_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        return sharedPref.getString(key, fallback)
    }

    fun shouldShowPoints(ctx: Context): Boolean {
        return getBooleanPref("pref_show_points", true, ctx)
    }

    fun shouldUseCompactPoints(ctx: Context): Boolean {
        return getBooleanPref(PREF_COMPACT_POINTS, false, ctx)
    }

    fun shouldIncludeTopLevelDomain(ctx: Context): Boolean {
        return getBooleanPref(PREF_INCLUDE_TOP_LEVEL_DOMAIN, true, ctx)
    }

    fun shouldShowCommentsCount(ctx: Context): Boolean {
        return getBooleanPref("pref_show_comments_count", true, ctx)
    }

    fun shouldUseCompactView(ctx: Context): Boolean {
        return getBooleanPref("pref_compact_view", false, ctx)
    }

    fun shouldShowThumbnails(ctx: Context): Boolean {
        return getBooleanPref("pref_thumbnails", true, ctx)
    }

    fun getPreferredStoryPreviewImageMode(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val mode = prefs.getString(PREF_STORY_PREVIEW_IMAGE_MODE, null)
        if (STORY_PREVIEW_IMAGE_OFF == mode
            || STORY_PREVIEW_IMAGE_SMALL == mode
            || STORY_PREVIEW_IMAGE_LARGE == mode
        ) {
            return mode
        }
        return STORY_PREVIEW_IMAGE_SMALL
    }

    fun shouldUseBorderlessLargeStoryPreviewImage(ctx: Context): Boolean {
        return getBooleanPref(PREF_STORY_PREVIEW_IMAGE_BORDERLESS, false, ctx)
    }

    fun shouldShowStorySummary(ctx: Context): Boolean {
        return getBooleanPref(PREF_SHOW_STORY_SUMMARY, false, ctx)
    }

    fun shouldCollapseParent(ctx: Context): Boolean {
        return getBooleanPref("pref_collapse_parent", false, ctx)
    }

    fun shouldShowIndex(ctx: Context): Boolean {
        return getBooleanPref("pref_show_index", true, ctx)
    }

    fun shouldTintCardUsingPreview(ctx: Context): Boolean {
        return getBooleanPref(PREF_TINT_CARD_USING_PREVIEW, true, ctx)
    }

    fun shouldShowCommentsHeaderPreviewImage(ctx: Context): Boolean {
        return STORY_PREVIEW_IMAGE_OFF != getPreferredStoryPreviewImageMode(ctx) && getBooleanPref(
            PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE, true, ctx
        )
    }

    fun shouldTintCommentsHeader(ctx: Context): Boolean {
        return shouldTintCardUsingPreview(ctx)
                && getBooleanPref(PREF_ENABLE_COMMENTS_HEADER_TINT, true, ctx)
    }

    fun getPreferredPaletteTintMode(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return PaletteTintPreferences.sanitizeMode(
            prefs.getString(
                PREF_PALETTE_TINT_MODE,
                PALETTE_TINT_DEFAULT
            )
        )
    }

    fun getPreferredPaletteTintConfigKey(ctx: Context): String {
        return PaletteTintPreferences.configKey(
            getPreferredPaletteTintMode(ctx),
            getPreferredPaletteTintStrength(ctx),
            getPreferredPaletteTintColorfulness(ctx),
            getPreferredPaletteTintTone(ctx)
        )
    }

    fun setPreferredPaletteTintMode(ctx: Context, mode: String?) {
        val previousConfig = getPreferredPaletteTintConfigKey(ctx)
        val sanitizedMode = PaletteTintPreferences.sanitizeMode(mode)
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(PREF_PALETTE_TINT_MODE, sanitizedMode)
            .apply()
        val updatedConfig = PaletteTintPreferences.configKey(
            sanitizedMode,
            getPreferredPaletteTintStrength(ctx),
            getPreferredPaletteTintColorfulness(ctx),
            getPreferredPaletteTintTone(ctx)
        )
        if (!TextUtils.equals(previousConfig, updatedConfig)) {
            clearTintColorCaches(ctx)
        }
    }

    fun setPreferredPaletteTintSettings(
        ctx: Context,
        mode: String?,
        strength: Int,
        colorfulness: Int,
        tone: Int
    ) {
        val previousConfig = getPreferredPaletteTintConfigKey(ctx)
        val updatedConfig = PaletteTintPreferences.configKey(mode, strength, colorfulness, tone)
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(PREF_PALETTE_TINT_MODE, PaletteTintPreferences.sanitizeMode(mode))
            .putInt(PREF_PALETTE_TINT_STRENGTH, PaletteTintPreferences.clampStrength(strength))
            .putInt(
                PREF_PALETTE_TINT_COLORFULNESS,
                PaletteTintPreferences.clampColorfulness(colorfulness)
            )
            .putInt(PREF_PALETTE_TINT_TONE, PaletteTintPreferences.clampTone(tone))
            .apply()
        if (!TextUtils.equals(previousConfig, updatedConfig)) {
            clearTintColorCaches(ctx)
        }
    }

    fun clearPreferredPaletteTintMode(ctx: Context) {
        val previousConfig = getPreferredPaletteTintConfigKey(ctx)
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .remove(PREF_PALETTE_TINT_MODE)
            .remove(PREF_PALETTE_TINT_STRENGTH)
            .remove(PREF_PALETTE_TINT_COLORFULNESS)
            .remove(PREF_PALETTE_TINT_TONE)
            .apply()
        val defaultConfig = PaletteTintPreferences.configKey(
            PALETTE_TINT_DEFAULT,
            DEFAULT_PALETTE_TINT_STRENGTH,
            DEFAULT_PALETTE_TINT_COLORFULNESS,
            DEFAULT_PALETTE_TINT_TONE
        )
        if (!TextUtils.equals(previousConfig, defaultConfig)) {
            clearTintColorCaches(ctx)
        }
    }

    fun getPreferredPaletteTintStrength(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return PaletteTintPreferences.clampStrength(
            prefs.getInt(
                PREF_PALETTE_TINT_STRENGTH,
                DEFAULT_PALETTE_TINT_STRENGTH
            )
        )
    }

    fun getPreferredPaletteTintColorfulness(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return PaletteTintPreferences.clampColorfulness(
            prefs.getInt(
                PREF_PALETTE_TINT_COLORFULNESS,
                DEFAULT_PALETTE_TINT_COLORFULNESS
            )
        )
    }

    fun getPreferredPaletteTintTone(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return PaletteTintPreferences.clampTone(
            prefs.getInt(
                PREF_PALETTE_TINT_TONE,
                DEFAULT_PALETTE_TINT_TONE
            )
        )
    }

    fun isDefaultPaletteTintTuning(ctx: Context): Boolean {
        return getPreferredPaletteTintStrength(ctx) == DEFAULT_PALETTE_TINT_STRENGTH && getPreferredPaletteTintColorfulness(
            ctx
        ) == DEFAULT_PALETTE_TINT_COLORFULNESS && getPreferredPaletteTintTone(ctx) == DEFAULT_PALETTE_TINT_TONE
    }

    fun getPreferredPaletteTintSummary(ctx: Context): String {
        val label = PaletteTintPreferences.modeLabel(getPreferredPaletteTintMode(ctx))
        if (isDefaultPaletteTintTuning(ctx)) {
            return label
        }
        return label + ", adjusted"
    }

    fun shouldShowNavigationButtons(ctx: Context): Boolean {
        return getBooleanPref("pref_scroll_navigation", false, ctx)
    }

    fun shouldHideJobs(ctx: Context): Boolean {
        return getBooleanPref("pref_hide_jobs", false, ctx)
    }

    fun shouldCollapseTopLevel(ctx: Context): Boolean {
        return getBooleanPref("pref_collapse_top_level", false, ctx)
    }

    fun getPreferredHotness(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getString("pref_hotness", "-1")!!.toInt()
    }

    fun getPreferredFont(ctx: Context): String {
        if ("hacker" == ThemeUtils.getPreferredTheme(ctx)) {
            return "jetbrainsmono"
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return TextPreferences.sanitizeFont(prefs.getString(PREF_FONT, "googlesansflexrounded"))
    }

    fun setPreferredFont(ctx: Context, font: String) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(PREF_FONT, TextPreferences.sanitizeFont(font))
            .apply()
    }

    fun getPreferredFontLabel(ctx: Context): String? {
        return SettingsUtils.getFontLabel(ctx, getPreferredFont(ctx)!!)
    }

    fun getFontLabel(ctx: Context, font: String): String? {
        val sanitizedFont = TextPreferences.sanitizeFont(font)
        val entries = ctx.getResources().getStringArray(R.array.font_entries)
        val values = ctx.getResources().getStringArray(R.array.font_values)
        for (i in 0..<min(entries.size, values.size)) {
            if (sanitizedFont == values[i]) {
                return entries[i]
            }
        }
        return if (entries.size > 0) entries[0] else sanitizedFont
    }

    fun getPreferredReaderModeFont(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return TextPreferences.sanitizeFont(
            prefs.getString(
                PREF_WEBVIEW_READER_MODE_FONT,
                "googlesansflexrounded"
            )!!
        )
    }

    fun setPreferredReaderModeFont(ctx: Context, font: String) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(PREF_WEBVIEW_READER_MODE_FONT, TextPreferences.sanitizeFont(font))
            .apply()
    }

    fun getPreferredReaderModeFontLabel(ctx: Context): String? {
        return getReaderModeFontLabel(ctx, getPreferredReaderModeFont(ctx))
    }

    fun getReaderModeFontLabel(ctx: Context, font: String): String? {
        return getFontLabel(ctx, TextPreferences.sanitizeFont(font))
    }

    fun shouldUseExternalBrowser(ctx: Context): Boolean {
        return getBooleanPref("pref_external_browser", false, ctx)
    }

    fun getPreferredCommentDepthIndicatorMode(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (prefs.contains(PREF_COMMENT_DEPTH_INDICATORS)) {
            return CommentDepthIndicatorUtils.sanitizeMode(
                prefs.getString(
                    SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS,
                    CommentDepthIndicatorUtils.MODE_THEME_DEFAULT
                )!!
            )
        }
        if (getBooleanPref(PREF_MONOCHROME_COMMENT_DEPTH, false, ctx)) {
            return CommentDepthIndicatorUtils.MODE_MONOCHROME
        }
        return CommentDepthIndicatorUtils.MODE_THEME_DEFAULT
    }

    fun setPreferredCommentDepthIndicatorMode(ctx: Context, mode: String) {
        val sanitizedMode = sanitizeMode(mode)
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putString(PREF_COMMENT_DEPTH_INDICATORS, sanitizedMode)
            .putBoolean(
                PREF_MONOCHROME_COMMENT_DEPTH,
                CommentDepthIndicatorUtils.MODE_MONOCHROME == sanitizedMode
            )
            .apply()
    }

    fun shouldUseMonochromeCommentDepthIndicators(ctx: Context): Boolean {
        return CommentDepthIndicatorUtils.MODE_MONOCHROME == getPreferredCommentDepthIndicatorMode(
            ctx
        )
    }

    fun shouldUseIntegratedWebView(ctx: Context): Boolean {
        return getBooleanPref("pref_webview", true, ctx)
    }

    fun shouldPreloadWebView(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return WebViewPreferences.sanitizePreloadMode(
            prefs.getString(
                PREF_PRELOAD_WEBVIEW,
                PRELOAD_WEBVIEW_NEVER
            )!!
        )
    }

    fun getPreloadWebViewMinimumBattery(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return WebViewPreferences.clampBatteryPercent(
            prefs.getInt(
                PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY,
                DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY
            )
        )
    }

    fun getStoriesToCache(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return StoryCachePreferences.sanitizeCount(
            prefs.getInt(PREF_STORIES_TO_CACHE, DEFAULT_STORIES_TO_CACHE)
        )
    }

    fun setStoriesToCache(ctx: Context, value: Int) {
        PreferenceManager.getDefaultSharedPreferences(ctx)
            .edit()
            .putInt(PREF_STORIES_TO_CACHE, StoryCachePreferences.sanitizeCount(value))
            .apply()
    }

    fun hasEnoughBatteryForWebViewPreload(ctx: Context, minimumBattery: Int): Boolean {
        val clampedMinimumBattery = WebViewPreferences.clampBatteryPercent(minimumBattery)
        if (clampedMinimumBattery <= DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY) {
            return true
        }

        val batteryStatus = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (batteryStatus == null) {
            return true
        }

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return true
        }

        val batteryPercent = Math.round(level * 100f / scale)
        return batteryPercent >= clampedMinimumBattery
    }

    fun shouldMatchWebViewTheme(ctx: Context): Boolean {
        return getBooleanPref("pref_webview_match_theme", false, ctx)
    }

    fun shouldUseReaderMode(ctx: Context): Boolean {
        return getBooleanPref(PREF_WEBVIEW_READER_MODE_ENABLED, true, ctx)
    }

    fun shouldUseReaderModeByDefault(ctx: Context): Boolean {
        return shouldUseReaderMode(ctx) && getBooleanPref(
            PREF_WEBVIEW_READER_MODE_DEFAULT,
            false,
            ctx
        )
    }

    fun getReaderModeFontSize(ctx: Context): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return TextPreferences.clampReaderModeFontSize(
            prefs.getInt(
                PREF_WEBVIEW_READER_MODE_FONT_SIZE,
                DEFAULT_READER_MODE_FONT_SIZE
            )
        )
    }

    fun shouldCloseWebViewOnBack(ctx: Context): Boolean {
        return getBooleanPref("pref_close_webview_on_back", false, ctx)
    }

    fun shouldBlockAds(ctx: Context): Boolean {
        return getBooleanPref("pref_webview_adblock", false, ctx)
    }

    fun shouldShowTopLevelDepthIndicator(ctx: Context): Boolean {
        return getBooleanPref("pref_top_level_thread_indicators", false, ctx)
    }

    fun shouldAlwaysOpenComments(ctx: Context): Boolean {
        return getBooleanPref("pref_always_open_comments", false, ctx)
    }

    fun shouldUseCompactHeader(ctx: Context): Boolean {
        return getBooleanPref("pref_compact_header", false, ctx)
    }

    fun shouldUseLeftAlign(ctx: Context): Boolean {
        return getBooleanPref("pref_left_align", false, ctx)
    }

    fun getPreferredStoryDisplayStyle(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val style: String = prefs.getString(
            SettingsUtils.PREF_STORY_DISPLAY_STYLE,
            SettingsUtils.STORY_DISPLAY_STYLE_STANDARD
        )!!
        if (STORY_DISPLAY_STYLE_CARD == style) {
            return STORY_DISPLAY_STYLE_CARD
        }
        return STORY_DISPLAY_STYLE_STANDARD
    }

    fun shouldUseCardStoryDisplayStyle(ctx: Context): Boolean {
        return STORY_DISPLAY_STYLE_CARD == getPreferredStoryDisplayStyle(ctx)
    }

    fun getPreferredStoryTextSize(ctx: Context): Float {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        try {
            return TextPreferences.clampStoryTextSize(
                prefs.getString(
                    SettingsUtils.PREF_STORY_TEXT_SIZE,
                    SettingsUtils.DEFAULT_STORY_TEXT_SIZE.toString()
                )!!.toFloat()
            )
        } catch (e: ClassCastException) {
            return TextPreferences.clampStoryTextSize(
                prefs.getFloat(PREF_STORY_TEXT_SIZE, DEFAULT_STORY_TEXT_SIZE)
            )
        } catch (e: NumberFormatException) {
            return DEFAULT_STORY_TEXT_SIZE
        }
    }

    fun getStoryMetaTextSize(storyTextSize: Float): Float {
        val scale = TextPreferences.clampStoryTextSize(storyTextSize) / DEFAULT_STORY_TEXT_SIZE
        return DEFAULT_STORY_META_TEXT_SIZE * scale
    }

    fun getPreferredCommentDisplayStyle(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val style: String = prefs.getString(
            SettingsUtils.PREF_COMMENT_DISPLAY_STYLE,
            SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD
        )!!
        if (COMMENT_DISPLAY_STYLE_CARD == style) {
            return COMMENT_DISPLAY_STYLE_CARD
        }
        return COMMENT_DISPLAY_STYLE_STANDARD
    }

    fun shouldUseCardCommentDisplayStyle(ctx: Context): Boolean {
        return COMMENT_DISPLAY_STYLE_CARD == getPreferredCommentDisplayStyle(ctx)
    }

    fun shouldShowCommentCardBorder(ctx: Context): Boolean {
        return getBooleanPref(PREF_COMMENT_CARD_BORDER, true, ctx)
    }

    fun shouldShowCommentDividers(ctx: Context): Boolean {
        return getBooleanPref(PREF_COMMENT_DIVIDERS, false, ctx)
    }

    fun shouldHighlightCommentMeta(ctx: Context): Boolean {
        return getBooleanPref(PREF_HIGHLIGHT_COMMENT_META, false, ctx)
    }

    fun shouldUseTransparentStatusBar(ctx: Context): Boolean {
        return getBooleanPref("pref_transparent_status_bar", false, ctx)
    }

    fun shouldUseSpecialNighttimeTheme(ctx: Context): Boolean {
        return getBooleanPref("pref_special_nighttime", false, ctx)
    }

    fun shouldUseCommentsAnimation(ctx: Context): Boolean {
        return getBooleanPref("pref_comments_animation", true, ctx)
    }

    fun shouldSmoothScrollComments(ctx: Context): Boolean {
        return getBooleanPref("pref_comments_animation_navigation", true, ctx)
    }

    const val COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED: String = "disabled"
    const val COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL: String = "top_level"
    private const val PREF_COMMENTS_VOLUME_NAVIGATION = "pref_comments_volume_navigation"

    fun getCommentsVolumeNavigationMode(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getString(
            SettingsUtils.PREF_COMMENTS_VOLUME_NAVIGATION,
            SettingsUtils.COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED
        )!!
    }

    fun shouldUseCommentsScrollbar(ctx: Context): Boolean {
        return getBooleanPref("pref_comments_scrollbar", false, ctx)
    }

    fun shouldCollectLinksInComments(ctx: Context): Boolean {
        return getBooleanPref(PREF_COLLECT_LINKS_IN_COMMENTS, true, ctx)
    }

    fun shouldHideClicked(ctx: Context): Boolean {
        return getBooleanPref(PREF_HIDE_CLICKED, false, ctx)
    }

    fun shouldGrayOutClicked(ctx: Context): Boolean {
        return getBooleanPref(PREF_GRAY_OUT_CLICKED, true, ctx)
    }

    fun shouldUseLinkPreviewArxiv(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_arxiv", true, ctx)
    }

    fun shouldUseLinkPreviewGithub(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_github", true, ctx)
    }

    fun shouldUseLinkPreviewGitLab(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_gitlab", true, ctx)
    }

    fun shouldUseLinkPreviewStackExchange(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_stack_exchange", true, ctx)
    }

    fun shouldUseLinkPreviewWikipedia(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_wikipedia", true, ctx)
    }

    fun shouldRedirectNitter(ctx: Context): Boolean {
        return getBooleanPref("pref_redirect_nitter", false, ctx)
    }

    fun getArchiveRedirectDomains(ctx: Context): ArrayList<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return ArrayList(
            ArchiveRedirectPolicy.parseDomains(
                prefs.getString(PREF_ARCHIVE_REDIRECT_DOMAINS, "")
            )
        )
    }

    fun getArchiveRedirectUrl(ctx: Context, url: String?): String? {
        return ArchiveRedirectPolicy.redirectUrl(url, getArchiveRedirectDomains(ctx))
    }

    fun shouldUseLinkPreviewX(ctx: Context): Boolean {
        return getBooleanPref("pref_link_preview_x", false, ctx)
    }

    fun shouldShowChangelog(ctx: Context): Boolean {
        return getBooleanPref("pref_show_changelog", true, ctx)
    }

    fun shouldUseBookmarks(ctx: Context): Boolean {
        return getBooleanPref(PREF_BOOKMARKS_ENABLED, true, ctx)
    }

    fun shouldSwapCommentLongPressTap(ctx: Context): Boolean {
        return getBooleanPref("pref_comments_swap_long", false, ctx)
    }

    fun shouldUsePaginationMode(ctx: Context): Boolean {
        return getBooleanPref("pref_pagination_mode", false, ctx)
    }

    fun shouldAlwaysShowTapToRefresh(ctx: Context): Boolean {
        return getBooleanPref(PREF_ALWAYS_SHOW_TAP_TO_REFRESH, false, ctx)
    }

    fun shouldUseAlgoliaAPI(ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return "algolia" == prefs.getString("pref_comments_provider", "algolia")
    }

    fun getBooleanPref(key: String?, backup: Boolean, ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getBoolean(key, backup)
    }

    fun getPreferredCommentTextSize(ctx: Context): Float {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        try {
            return TextPreferences.clampCommentTextSize(
                prefs.getString(
                    SettingsUtils.PREF_COMMENT_TEXT_SIZE,
                    SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE.toString()
                )!!.toFloat()
            )
        } catch (e: ClassCastException) {
            try {
                return TextPreferences.clampCommentTextSize(
                    prefs.getFloat(
                        PREF_COMMENT_TEXT_SIZE,
                        DEFAULT_COMMENT_TEXT_SIZE
                    )
                )
            } catch (ignored: ClassCastException) {
                return TextPreferences.clampCommentTextSize(
                    prefs.getInt(
                        PREF_COMMENT_TEXT_SIZE, Math.round(
                            DEFAULT_COMMENT_TEXT_SIZE
                        )
                    ).toFloat()
                )
            }
        } catch (e: NumberFormatException) {
            return DEFAULT_COMMENT_TEXT_SIZE
        }
    }

    fun getPreferredStoryType(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val startingPage: String = prefs.getString("pref_default_story_type", "Top Stories")!!
        if ("Bookmarks" == startingPage
            || "History" == startingPage
            || (AdditionalFrontpagePreferences.isLabel(startingPage) && !isAdditionalFrontpageEnabled(
                ctx,
                startingPage
            ))
        ) {
            return "Top Stories"
        }
        return startingPage
    }

    fun getEnabledAdditionalFrontpages(ctx: Context): MutableSet<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val enabled: MutableSet<String> =
            prefs.getStringSet(PREF_ADDITIONAL_FRONTPAGES, HashSet<String>())!!
        return HashSet(AdditionalFrontpagePreferences.sanitize(enabled))
    }

    fun isAdditionalFrontpageEnabled(ctx: Context, label: String?): Boolean {
        return getEnabledAdditionalFrontpages(ctx).contains(label)
    }

    fun getPreferredCommentSorting(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getString("pref_comment_sorting", "Default")!!
    }

    fun getPreferredFaviconProvider(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return FaviconPreferences.sanitizeProvider(
            prefs.getString(
                PREF_FAVICON_PROVIDER,
                FAVICON_PROVIDER_GOOGLE
            )!!
        )
    }

    fun getFaviconProviderIconResource(provider: String): Int {
        when (FaviconPreferences.sanitizeProvider(provider)) {
            FAVICON_PROVIDER_DUCKDUCKGO -> return R.drawable.ic_favicon_provider_duckduckgo
            FAVICON_PROVIDER_TWENTY -> return R.drawable.ic_favicon_provider_twenty
            FAVICON_PROVIDER_GOOGLE -> return R.drawable.ic_favicon_provider_google
            else -> return R.drawable.ic_favicon_provider_google
        }
    }

    fun getBookmarksIndex(res: Resources): Int {
        val sortingOptions = res.getStringArray(R.array.sorting_options)

        for (i in sortingOptions.indices.reversed()) {
            if (sortingOptions[i] == "Bookmarks") {
                return i
            }
        }
        // fallback
        return sortingOptions.size - 1
    }

    fun getHistoryIndex(res: Resources): Int {
        val sortingOptions = res.getStringArray(R.array.sorting_options)

        for (i in sortingOptions.indices.reversed()) {
            if (sortingOptions[i] == "History") {
                return i
            }
        }
        // fallback
        return sortingOptions.size - 1
    }

    fun getFavoritesIndex(res: Resources): Int {
        return getBookmarksIndex(res) + 1
    }

    fun getJobsIndex(res: Resources): Int {
        val sortingOptions = res.getStringArray(R.array.sorting_options)

        for (i in sortingOptions.indices.reversed()) {
            if (sortingOptions[i] == "HN Jobs") {
                return i
            }
        }
        // fallback
        return sortingOptions.size - 2
    }
}
