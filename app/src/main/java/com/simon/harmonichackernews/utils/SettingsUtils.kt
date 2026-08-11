package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.net.Uri
import android.os.BatteryManager
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.AdditionalFrontpagePreferences
import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.StoryCachePreferences
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils.sanitizeMode
import com.simon.harmonichackernews.utils.PreviewImageTintUtils.clearTintColorCaches
import java.util.ArrayList
import java.util.HashSet
import java.util.List
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object SettingsUtils {
    private fun settings(ctx: Context) = AndroidUserSettings.get(ctx)

    const val PREF_THEME: String = "pref_theme"
    const val PREF_THEME_NIGHTTIME: String = "pref_theme_nighttime"
    const val DEFAULT_THEME: String = ThemePreferences.DEFAULT
    const val DEFAULT_NIGHTTIME_THEME: String = ThemePreferences.DEFAULT_NIGHTTIME
    const val PREF_COMMENT_DEPTH_INDICATORS: String = UserPreferenceKeys.COMMENT_DEPTH_INDICATORS
    const val PREF_COMMENT_DIVIDERS: String = UserPreferenceKeys.COMMENT_DIVIDERS
    const val PREF_MONOCHROME_COMMENT_DEPTH: String = UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH
    const val PREF_STORY_DISPLAY_STYLE: String = UserPreferenceKeys.STORY_DISPLAY_STYLE
    const val PREF_STORY_PREVIEW_IMAGE_MODE: String = UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE
    const val PREF_STORY_PREVIEW_IMAGE_BORDERLESS: String =
        UserPreferenceKeys.STORY_PREVIEW_IMAGE_BORDERLESS
    const val PREF_SHOW_STORY_SUMMARY: String = UserPreferenceKeys.SHOW_STORY_SUMMARY
    const val PREF_TINT_CARD_USING_PREVIEW: String = UserPreferenceKeys.TINT_CARD_USING_PREVIEW
    const val PREF_PALETTE_TINT_MODE: String = UserPreferenceKeys.PALETTE_TINT_MODE
    const val PREF_PALETTE_TINT_STRENGTH: String = UserPreferenceKeys.PALETTE_TINT_STRENGTH
    const val PREF_PALETTE_TINT_COLORFULNESS: String =
        UserPreferenceKeys.PALETTE_TINT_COLORFULNESS
    const val PREF_PALETTE_TINT_TONE: String = UserPreferenceKeys.PALETTE_TINT_TONE
    const val PREF_STORY_TEXT_SIZE: String = UserPreferenceKeys.STORY_TEXT_SIZE
    const val PREF_COMPACT_POINTS: String = UserPreferenceKeys.COMPACT_POINTS
    const val PREF_INCLUDE_TOP_LEVEL_DOMAIN: String = UserPreferenceKeys.INCLUDE_TOP_LEVEL_DOMAIN
    const val PREF_COMMENT_DISPLAY_STYLE: String = UserPreferenceKeys.COMMENT_DISPLAY_STYLE
    const val PREF_COMMENT_CARD_BORDER: String = UserPreferenceKeys.COMMENT_CARD_BORDER
    const val PREF_HIGHLIGHT_COMMENT_META: String = UserPreferenceKeys.HIGHLIGHT_COMMENT_META
    const val PREF_COMMENT_TEXT_SIZE: String = UserPreferenceKeys.COMMENT_TEXT_SIZE
    const val PREF_ENABLE_COMMENTS_HEADER_TINT: String = UserPreferenceKeys.COMMENTS_HEADER_TINT
    const val PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE: String =
        UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE
    const val PREF_COLLECT_LINKS_IN_COMMENTS: String = UserPreferenceKeys.COLLECT_LINKS_IN_COMMENTS
    const val PREF_FONT: String = UserPreferenceKeys.FONT
    const val PREF_BOOKMARKS_ENABLED: String = UserPreferenceKeys.BOOKMARKS_ENABLED
    const val PREF_GRAY_OUT_CLICKED: String = UserPreferenceKeys.GRAY_OUT_CLICKED
    const val PREF_HIDE_CLICKED: String = UserPreferenceKeys.HIDE_CLICKED
    const val PREF_ALWAYS_SHOW_TAP_TO_REFRESH: String = UserPreferenceKeys.ALWAYS_SHOW_TAP_TO_REFRESH
    const val PREF_PRELOAD_WEBVIEW: String = UserPreferenceKeys.PRELOAD_WEBVIEW
    const val PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY: String =
        UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY
    const val PREF_WEBVIEW_READER_MODE_ENABLED: String = UserPreferenceKeys.READER_MODE_ENABLED
    const val PREF_WEBVIEW_READER_MODE_DEFAULT: String = UserPreferenceKeys.READER_MODE_DEFAULT
    const val PREF_WEBVIEW_READER_MODE_FONT: String = UserPreferenceKeys.READER_MODE_FONT
    const val PREF_WEBVIEW_READER_MODE_FONT_SIZE: String = UserPreferenceKeys.READER_MODE_FONT_SIZE
    const val PREF_ARCHIVE_REDIRECT_DOMAINS: String = UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS
    const val PREF_STORIES_TO_CACHE: String = UserPreferenceKeys.STORIES_TO_CACHE
    const val PREF_FAVICON_PROVIDER: String = UserPreferenceKeys.FAVICON_PROVIDER
    const val PREF_ADDITIONAL_FRONTPAGES: String = UserPreferenceKeys.ADDITIONAL_FRONTPAGES
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
        return ThemePreferences.isAutomatic(theme)
    }

    fun isDarkTheme(theme: String?): Boolean {
        return ThemePreferences.isDark(theme)
    }

    fun getSelectableNighttimeTheme(theme: String): String {
        return ThemePreferences.selectableNighttimeTheme(theme)
    }

    fun readIntSetFromSharedPreferences(ctx: Context, key: String?): MutableSet<Int> {
        if (key == null) return mutableSetOf()
        return AndroidKeyValueStore.global(ctx).getStringSet(key)
            .mapNotNullTo(mutableSetOf(), String::toIntOrNull)
    }

    fun saveIntSetToSharedPreferences(ctx: Context, key: String?, set: Set<Int>) {
        if (key == null) return
        AndroidKeyValueStore.global(ctx).putStringSet(
            key,
            set.mapTo(mutableSetOf(), Int::toString),
        )
    }

    fun readStringSetFromSharedPreferences(ctx: Context, key: String?): MutableSet<String> {
        if (key == null) return mutableSetOf()
        return AndroidKeyValueStore.global(ctx).getStringSet(key).toMutableSet()
    }

    fun saveStringSetToSharedPreferences(ctx: Context, key: String?, set: MutableSet<String>?) {
        if (key == null) return
        AndroidKeyValueStore.global(ctx).putStringSet(key, set)
    }

    fun saveStringToSharedPreferences(ctx: Context, key: String?, text: String?) {
        if (key == null) return
        AndroidKeyValueStore.global(ctx).putString(key, text)
    }

    fun readStringFromSharedPreferences(ctx: Context, key: String?): String? {
        if (key == null) return null
        return AndroidKeyValueStore.global(ctx).getString(key)
    }

    fun readStringFromSharedPreferences(ctx: Context, key: String?, fallback: String?): String? {
        if (key == null) return fallback
        return AndroidKeyValueStore.global(ctx).getString(key, fallback)
    }

    fun shouldShowPoints(ctx: Context): Boolean {
        return settings(ctx).story.showPoints
    }

    fun shouldUseCompactPoints(ctx: Context): Boolean {
        return settings(ctx).story.compactPoints
    }

    fun shouldIncludeTopLevelDomain(ctx: Context): Boolean {
        return settings(ctx).story.includeTopLevelDomain
    }

    fun shouldShowCommentsCount(ctx: Context): Boolean {
        return settings(ctx).story.showCommentsCount
    }

    fun shouldUseCompactView(ctx: Context): Boolean {
        return settings(ctx).story.compactView
    }

    fun shouldShowThumbnails(ctx: Context): Boolean {
        return settings(ctx).story.thumbnails
    }

    fun getPreferredStoryPreviewImageMode(ctx: Context): String {
        return settings(ctx).story.previewImageMode
    }

    fun shouldUseBorderlessLargeStoryPreviewImage(ctx: Context): Boolean {
        return settings(ctx).story.borderlessLargePreviewImage
    }

    fun shouldShowStorySummary(ctx: Context): Boolean {
        return settings(ctx).story.showSummary
    }

    fun shouldCollapseParent(ctx: Context): Boolean {
        return settings(ctx).comments.collapseParent
    }

    fun shouldShowIndex(ctx: Context): Boolean {
        return settings(ctx).story.showIndex
    }

    fun shouldTintCardUsingPreview(ctx: Context): Boolean {
        return settings(ctx).story.tintCardUsingPreview
    }

    fun shouldShowCommentsHeaderPreviewImage(ctx: Context): Boolean {
        return settings(ctx).comments.showHeaderPreviewImage
    }

    fun shouldTintCommentsHeader(ctx: Context): Boolean {
        return settings(ctx).comments.tintHeader
    }

    fun getPreferredPaletteTintMode(ctx: Context): String {
        return PaletteTintPreferences.sanitizeMode(settings(ctx).story.paletteTintConfigKey)
    }

    fun getPreferredPaletteTintConfigKey(ctx: Context): String {
        return settings(ctx).story.paletteTintConfigKey
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
        return PaletteTintPreferences.strength(settings(ctx).story.paletteTintConfigKey)
    }

    fun getPreferredPaletteTintColorfulness(ctx: Context): Int {
        return PaletteTintPreferences.colorfulness(settings(ctx).story.paletteTintConfigKey)
    }

    fun getPreferredPaletteTintTone(ctx: Context): Int {
        return PaletteTintPreferences.tone(settings(ctx).story.paletteTintConfigKey)
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
        return settings(ctx).comments.showNavigationButtons
    }

    fun shouldHideJobs(ctx: Context): Boolean {
        return settings(ctx).story.hideJobs
    }

    fun shouldCollapseTopLevel(ctx: Context): Boolean {
        return settings(ctx).comments.collapseTopLevel
    }

    fun getPreferredHotness(ctx: Context): Int {
        return settings(ctx).story.hotness
    }

    fun getPreferredFont(ctx: Context): String {
        return settings(ctx).story.font
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
        return settings(ctx).reading.readerModeFont
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
        return settings(ctx).reading.externalBrowser
    }

    fun getPreferredCommentDepthIndicatorMode(ctx: Context): String {
        return settings(ctx).comments.depthIndicatorMode
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
        return settings(ctx).reading.integratedWebView
    }

    fun shouldPreloadWebView(ctx: Context): String {
        return settings(ctx).reading.preloadWebViewMode
    }

    fun getPreloadWebViewMinimumBattery(ctx: Context): Int {
        return settings(ctx).reading.preloadWebViewMinimumBattery
    }

    fun getStoriesToCache(ctx: Context): Int {
        return settings(ctx).cache.storiesToCache
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
        return settings(ctx).reading.matchWebViewTheme
    }

    fun shouldUseReaderMode(ctx: Context): Boolean {
        return settings(ctx).reading.readerModeEnabled
    }

    fun shouldUseReaderModeByDefault(ctx: Context): Boolean {
        return settings(ctx).reading.readerModeDefault
    }

    fun getReaderModeFontSize(ctx: Context): Int {
        return settings(ctx).reading.readerModeFontSize
    }

    fun shouldCloseWebViewOnBack(ctx: Context): Boolean {
        return settings(ctx).reading.closeWebViewOnBack
    }

    fun shouldBlockAds(ctx: Context): Boolean {
        return settings(ctx).reading.blockAds
    }

    fun shouldShowTopLevelDepthIndicator(ctx: Context): Boolean {
        return settings(ctx).comments.showTopLevelDepthIndicator
    }

    fun shouldAlwaysOpenComments(ctx: Context): Boolean {
        return settings(ctx).story.alwaysOpenComments
    }

    fun shouldUseCompactHeader(ctx: Context): Boolean {
        return settings(ctx).story.compactHeader
    }

    fun shouldUseLeftAlign(ctx: Context): Boolean {
        return settings(ctx).story.leftAlign
    }

    fun getPreferredStoryDisplayStyle(ctx: Context): String {
        return if (settings(ctx).story.cardStyle) {
            STORY_DISPLAY_STYLE_CARD
        } else {
            STORY_DISPLAY_STYLE_STANDARD
        }
    }

    fun shouldUseCardStoryDisplayStyle(ctx: Context): Boolean {
        return STORY_DISPLAY_STYLE_CARD == getPreferredStoryDisplayStyle(ctx)
    }

    fun getPreferredStoryTextSize(ctx: Context): Float {
        return settings(ctx).story.storyTextSize
    }

    fun getStoryMetaTextSize(storyTextSize: Float): Float {
        val scale = TextPreferences.clampStoryTextSize(storyTextSize) / DEFAULT_STORY_TEXT_SIZE
        return DEFAULT_STORY_META_TEXT_SIZE * scale
    }

    fun getPreferredCommentDisplayStyle(ctx: Context): String {
        return if (settings(ctx).comments.cardStyle) {
            COMMENT_DISPLAY_STYLE_CARD
        } else {
            COMMENT_DISPLAY_STYLE_STANDARD
        }
    }

    fun shouldUseCardCommentDisplayStyle(ctx: Context): Boolean {
        return COMMENT_DISPLAY_STYLE_CARD == getPreferredCommentDisplayStyle(ctx)
    }

    fun shouldShowCommentCardBorder(ctx: Context): Boolean {
        return settings(ctx).comments.cardBorder
    }

    fun shouldShowCommentDividers(ctx: Context): Boolean {
        return settings(ctx).comments.showDividers
    }

    fun shouldHighlightCommentMeta(ctx: Context): Boolean {
        return settings(ctx).comments.highlightMetadata
    }

    fun shouldUseTransparentStatusBar(ctx: Context): Boolean {
        return settings(ctx).general.transparentStatusBar
    }

    fun shouldUseSpecialNighttimeTheme(ctx: Context): Boolean {
        return settings(ctx).general.specialNighttimeTheme
    }

    fun shouldUseCommentsAnimation(ctx: Context): Boolean {
        return settings(ctx).comments.animateChanges
    }

    fun shouldSmoothScrollComments(ctx: Context): Boolean {
        return settings(ctx).comments.smoothScroll
    }

    const val COMMENTS_VOLUME_NAVIGATION_MODE_DISABLED: String = "disabled"
    const val COMMENTS_VOLUME_NAVIGATION_MODE_TOP_LEVEL: String = "top_level"
    private const val PREF_COMMENTS_VOLUME_NAVIGATION = "pref_comments_volume_navigation"

    fun getCommentsVolumeNavigationMode(ctx: Context): String {
        return settings(ctx).comments.volumeNavigationMode
    }

    fun shouldUseCommentsScrollbar(ctx: Context): Boolean {
        return settings(ctx).comments.showScrollbar
    }

    fun shouldCollectLinksInComments(ctx: Context): Boolean {
        return settings(ctx).comments.collectReferenceLinks
    }

    fun shouldHideClicked(ctx: Context): Boolean {
        return settings(ctx).story.hideClicked
    }

    fun shouldGrayOutClicked(ctx: Context): Boolean {
        return settings(ctx).story.grayOutClicked
    }

    fun shouldUseLinkPreviewArxiv(ctx: Context): Boolean {
        return settings(ctx).reading.previewArxiv
    }

    fun shouldUseLinkPreviewGithub(ctx: Context): Boolean {
        return settings(ctx).reading.previewGithub
    }

    fun shouldUseLinkPreviewGitLab(ctx: Context): Boolean {
        return settings(ctx).reading.previewGitlab
    }

    fun shouldUseLinkPreviewStackExchange(ctx: Context): Boolean {
        return settings(ctx).reading.previewStackExchange
    }

    fun shouldUseLinkPreviewWikipedia(ctx: Context): Boolean {
        return settings(ctx).reading.previewWikipedia
    }

    fun shouldRedirectNitter(ctx: Context): Boolean {
        return settings(ctx).reading.redirectNitter
    }

    fun getArchiveRedirectDomains(ctx: Context): ArrayList<String> {
        return ArrayList(settings(ctx).reading.archiveRedirectDomains)
    }

    fun getArchiveRedirectUrl(ctx: Context, url: String?): String? {
        return ArchiveRedirectPolicy.redirectUrl(url, getArchiveRedirectDomains(ctx))
    }

    fun shouldUseLinkPreviewX(ctx: Context): Boolean {
        return settings(ctx).reading.previewX
    }

    fun shouldShowChangelog(ctx: Context): Boolean {
        return settings(ctx).general.showChangelog
    }

    fun shouldUseBookmarks(ctx: Context): Boolean {
        return settings(ctx).general.bookmarksEnabled
    }

    fun shouldSwapCommentLongPressTap(ctx: Context): Boolean {
        return settings(ctx).comments.swapLongPressTap
    }

    fun shouldUsePaginationMode(ctx: Context): Boolean {
        return settings(ctx).story.pagination
    }

    fun shouldAlwaysShowTapToRefresh(ctx: Context): Boolean {
        return settings(ctx).story.alwaysShowTapToRefresh
    }

    fun shouldUseAlgoliaAPI(ctx: Context): Boolean {
        return settings(ctx).reading.useAlgoliaApi
    }

    fun getBooleanPref(key: String?, backup: Boolean, ctx: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return prefs.getBoolean(key, backup)
    }

    fun getPreferredCommentTextSize(ctx: Context): Float {
        return settings(ctx).comments.textSize
    }

    fun getPreferredStoryType(ctx: Context): String {
        return settings(ctx).story.preferredStoryType
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
        return settings(ctx).comments.sorting
    }

    fun getPreferredFaviconProvider(ctx: Context): String {
        return settings(ctx).story.faviconProvider
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
