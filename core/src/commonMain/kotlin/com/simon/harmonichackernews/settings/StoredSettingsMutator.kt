package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy

/** Writes typed settings without exposing a platform preference API to UI code. */
class StoredSettingsMutator(
    private val store: KeyValueStore,
) {
    fun setStoryBoolean(preference: StoryBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                StoryBooleanPreference.BORDERLESS_LARGE_IMAGE ->
                    UserPreferenceKeys.STORY_PREVIEW_IMAGE_BORDERLESS
                StoryBooleanPreference.TINT_CARD_USING_PREVIEW ->
                    UserPreferenceKeys.TINT_CARD_USING_PREVIEW
                StoryBooleanPreference.COMPACT_VIEW -> UserPreferenceKeys.COMPACT_VIEW
                StoryBooleanPreference.SHOW_SUMMARY -> UserPreferenceKeys.SHOW_STORY_SUMMARY
                StoryBooleanPreference.SHOW_THUMBNAILS -> UserPreferenceKeys.THUMBNAILS
                StoryBooleanPreference.SHOW_POINTS -> UserPreferenceKeys.SHOW_POINTS
                StoryBooleanPreference.COMPACT_POINTS -> UserPreferenceKeys.COMPACT_POINTS
                StoryBooleanPreference.INCLUDE_TOP_LEVEL_DOMAIN ->
                    UserPreferenceKeys.INCLUDE_TOP_LEVEL_DOMAIN
                StoryBooleanPreference.SHOW_COMMENTS_COUNT -> UserPreferenceKeys.SHOW_COMMENTS_COUNT
                StoryBooleanPreference.SHOW_INDEX -> UserPreferenceKeys.SHOW_INDEX
                StoryBooleanPreference.LEFT_ALIGN -> UserPreferenceKeys.LEFT_ALIGN
                StoryBooleanPreference.ALWAYS_OPEN_COMMENTS -> UserPreferenceKeys.ALWAYS_OPEN_COMMENTS
                StoryBooleanPreference.PAGINATION -> UserPreferenceKeys.PAGINATION_MODE
                StoryBooleanPreference.HIDE_CLICKED -> UserPreferenceKeys.HIDE_CLICKED
                StoryBooleanPreference.GRAY_OUT_CLICKED -> UserPreferenceKeys.GRAY_OUT_CLICKED
                StoryBooleanPreference.HIDE_JOBS -> UserPreferenceKeys.HIDE_JOBS
            },
            value,
        )
    }

    fun setStoryString(preference: StoryStringPreference, value: String) {
        when (preference) {
            StoryStringPreference.PREVIEW_IMAGE_MODE -> store.putString(
                UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE,
                StoryPreviewPreferences.sanitize(value),
            )
            StoryStringPreference.DISPLAY_STYLE -> store.putString(
                UserPreferenceKeys.STORY_DISPLAY_STYLE,
                sanitizeDisplayStyle(value),
            )
        }
    }

    fun setStoryTextSize(value: Float) {
        store.putString(
            UserPreferenceKeys.STORY_TEXT_SIZE,
            serializeFloat(TextPreferences.clampStoryTextSize(value)),
        )
    }

    fun setHotness(value: Int) {
        store.putString(UserPreferenceKeys.HOTNESS, value.toString())
    }

    fun setPreferredStoryType(value: String) {
        store.putString(UserPreferenceKeys.DEFAULT_STORY_TYPE, value)
    }

    fun setAdditionalFrontpages(value: Set<String>) {
        store.putStringSet(
            UserPreferenceKeys.ADDITIONAL_FRONTPAGES,
            AdditionalFrontpagePreferences.sanitize(value),
        )
    }

    fun setFaviconProvider(value: String) {
        store.putString(
            UserPreferenceKeys.FAVICON_PROVIDER,
            FaviconPreferences.sanitizeProvider(value),
        )
    }

    fun setCommentBoolean(preference: CommentBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                CommentBooleanPreference.CARD_BORDER -> UserPreferenceKeys.COMMENT_CARD_BORDER
                CommentBooleanPreference.COLLECT_REFERENCE_LINKS ->
                    UserPreferenceKeys.COLLECT_LINKS_IN_COMMENTS
                CommentBooleanPreference.HIGHLIGHT_METADATA ->
                    UserPreferenceKeys.HIGHLIGHT_COMMENT_META
                CommentBooleanPreference.SHOW_DIVIDERS -> UserPreferenceKeys.COMMENT_DIVIDERS
                CommentBooleanPreference.TOP_LEVEL_DEPTH_INDICATOR ->
                    UserPreferenceKeys.TOP_LEVEL_THREAD_INDICATORS
                CommentBooleanPreference.SHOW_SCROLLBAR -> UserPreferenceKeys.COMMENTS_SCROLLBAR
                CommentBooleanPreference.ANIMATE_CHANGES -> UserPreferenceKeys.COMMENTS_ANIMATION
                CommentBooleanPreference.HEADER_TINT -> UserPreferenceKeys.COMMENTS_HEADER_TINT
                CommentBooleanPreference.HEADER_PREVIEW_IMAGE ->
                    UserPreferenceKeys.COMMENTS_HEADER_PREVIEW_IMAGE
                CommentBooleanPreference.SHOW_UP_BUTTON ->
                    UserPreferenceKeys.COMMENTS_SHOW_UP_BUTTON
                CommentBooleanPreference.COLLAPSE_PARENT -> UserPreferenceKeys.COLLAPSE_PARENT
                CommentBooleanPreference.COLLAPSE_TOP_LEVEL -> UserPreferenceKeys.COLLAPSE_TOP_LEVEL
                CommentBooleanPreference.HIDE_DELAYED_COMMENTS ->
                    UserPreferenceKeys.HIDE_DELAYED_COMMENTS
                CommentBooleanPreference.SWAP_LONG_PRESS_TAP -> UserPreferenceKeys.COMMENTS_SWAP_LONG
                CommentBooleanPreference.SHOW_NAVIGATION_BUTTONS ->
                    UserPreferenceKeys.SCROLL_NAVIGATION
                CommentBooleanPreference.SMOOTH_SCROLL -> UserPreferenceKeys.COMMENTS_SMOOTH_SCROLL
            },
            value,
        )
    }

    fun setCommentDisplayStyle(value: String) {
        setCommentDisplayStyle(DisplayStyle.fromStored(value))
    }

    fun setCommentDisplayStyle(value: DisplayStyle) {
        store.putString(UserPreferenceKeys.COMMENT_DISPLAY_STYLE, value.storedValue)
    }

    fun setCommentTextSize(value: Float) {
        store.putString(
            UserPreferenceKeys.COMMENT_TEXT_SIZE,
            serializeFloat(TextPreferences.clampCommentTextSize(value)),
        )
    }

    fun setCommentSorting(value: String) {
        setCommentSorting(CommentSortingPreference.fromStored(value))
    }

    fun setCommentSorting(value: CommentSortingPreference) {
        store.putString(UserPreferenceKeys.COMMENT_SORTING, value.storedValue)
    }

    fun setCommentsProvider(value: String) {
        setCommentsProvider(CommentsProvider.fromStored(value))
    }

    fun setCommentsProvider(value: CommentsProvider) {
        store.putString(UserPreferenceKeys.COMMENTS_PROVIDER, value.storedValue)
    }

    fun setCommentsVolumeNavigation(value: String) {
        setCommentsVolumeNavigation(CommentVolumeNavigationMode.fromStored(value))
    }

    fun setCommentsVolumeNavigation(value: CommentVolumeNavigationMode) {
        store.putString(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION, value.storedValue)
    }

    fun setReadingBoolean(preference: ReadingBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                ReadingBooleanPreference.INTEGRATED_WEB_VIEW -> UserPreferenceKeys.WEBVIEW
                ReadingBooleanPreference.CLOSE_WEB_VIEW_ON_BACK ->
                    UserPreferenceKeys.CLOSE_WEBVIEW_ON_BACK
                ReadingBooleanPreference.MATCH_WEB_VIEW_THEME -> UserPreferenceKeys.WEBVIEW_MATCH_THEME
                ReadingBooleanPreference.BLOCK_ADS -> UserPreferenceKeys.WEBVIEW_ADBLOCK
                ReadingBooleanPreference.READER_MODE_ENABLED -> UserPreferenceKeys.READER_MODE_ENABLED
                ReadingBooleanPreference.READER_MODE_DEFAULT -> UserPreferenceKeys.READER_MODE_DEFAULT
                ReadingBooleanPreference.EXTERNAL_BROWSER -> UserPreferenceKeys.EXTERNAL_BROWSER
                ReadingBooleanPreference.REDIRECT_NITTER -> UserPreferenceKeys.REDIRECT_NITTER
            },
            value,
        )
    }

    fun setLinkPreviewEnabled(type: LinkPreviewType, enabled: Boolean) {
        store.putBoolean(type.preferenceKey, enabled)
        if (type == LinkPreviewType.TWITTER_X && enabled) {
            store.putBoolean(UserPreferenceKeys.REDIRECT_NITTER, true)
        }
    }

    fun setReaderModeFontSize(value: Int) {
        store.putInt(
            UserPreferenceKeys.READER_MODE_FONT_SIZE,
            TextPreferences.clampReaderModeFontSize(value),
        )
    }

    fun setWebViewPreload(mode: String, minimumBattery: Int) {
        setWebViewPreload(WebViewPreloadMode.fromStored(mode), minimumBattery)
    }

    fun setWebViewPreload(mode: WebViewPreloadMode, minimumBattery: Int) {
        store.putString(UserPreferenceKeys.PRELOAD_WEBVIEW, mode.storedValue)
        store.putInt(
            UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY,
            WebViewPreferences.clampBatteryPercent(minimumBattery),
        )
    }

    fun setCommentsPreload(mode: WebViewPreloadMode, minimumBattery: Int) {
        store.update {
            putString(UserPreferenceKeys.PRELOAD_COMMENTS_MODE, mode.storedValue)
            putInt(
                UserPreferenceKeys.PRELOAD_COMMENTS_MINIMUM_BATTERY,
                WebViewPreferences.clampBatteryPercent(minimumBattery),
            )
        }
    }

    fun setArchiveRedirectDomains(domains: List<String>) {
        val normalized = ArchiveRedirectPolicy.parseDomains(domains.joinToString(","))
        store.putString(UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS, normalized.joinToString(","))
    }

    fun setAppearanceBoolean(preference: AppearanceBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                AppearanceBooleanPreference.SPECIAL_NIGHTTIME -> UserPreferenceKeys.SPECIAL_NIGHTTIME
                AppearanceBooleanPreference.TRANSPARENT_STATUS_BAR ->
                    UserPreferenceKeys.TRANSPARENT_STATUS_BAR
                AppearanceBooleanPreference.COMPACT_HEADER -> UserPreferenceKeys.COMPACT_HEADER
            },
            value,
        )
    }

    fun setTheme(value: String) {
        store.putString(ThemePreferences.KEY, value)
    }

    fun setNighttimeTheme(value: String) {
        store.putString(
            ThemePreferences.NIGHTTIME_KEY,
            ThemePreferences.selectableNighttimeTheme(value),
        )
    }

    fun setGeneralBoolean(preference: GeneralBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                GeneralBooleanPreference.BOOKMARKS_ENABLED -> UserPreferenceKeys.BOOKMARKS_ENABLED
                GeneralBooleanPreference.SHOW_CHANGELOG -> UserPreferenceKeys.SHOW_CHANGELOG
            },
            value,
        )
    }

    fun setDebugBoolean(preference: DebugBooleanPreference, value: Boolean) {
        store.putBoolean(
            when (preference) {
                DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH ->
                    UserPreferenceKeys.ALWAYS_SHOW_TAP_TO_REFRESH
            },
            value,
        )
    }

    fun applyWelcomePreset(expressive: Boolean) {
        store.putBoolean(UserPreferenceKeys.TINT_CARD_USING_PREVIEW, expressive)
        store.putString(
            UserPreferenceKeys.FONT,
            if (expressive) "googlesansflexrounded" else "productsans",
        )
        store.putString(
            UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE,
            if (expressive) StoryPreviewPreferences.SMALL else StoryPreviewPreferences.OFF,
        )
    }

    fun setFont(font: String) {
        setFont(AppFont.fromStored(font))
    }

    fun setFont(font: AppFont) {
        store.putString(UserPreferenceKeys.FONT, font.storedValue)
    }

    fun setReaderModeFont(font: String) {
        setReaderModeFont(AppFont.fromStored(font))
    }

    fun setReaderModeFont(font: AppFont) {
        store.putString(UserPreferenceKeys.READER_MODE_FONT, font.storedValue)
    }

    fun setCommentDepthIndicatorMode(mode: String) {
        val sanitized = CommentDepthPreferences.sanitizeMode(mode)
        store.putString(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS, sanitized)
        store.putBoolean(
            UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH,
            sanitized == CommentDepthPreferences.MONOCHROME,
        )
    }

    /** Returns true when palette-derived caches must be invalidated. */
    fun setPaletteTint(
        mode: String?,
        strength: Int,
        colorfulness: Int,
        tone: Int,
    ): Boolean {
        val previous = paletteTintConfigKey()
        val sanitizedMode = PaletteTintPreferences.sanitizeMode(mode)
        val sanitizedStrength = PaletteTintPreferences.clampStrength(strength)
        val sanitizedColorfulness = PaletteTintPreferences.clampColorfulness(colorfulness)
        val sanitizedTone = PaletteTintPreferences.clampTone(tone)
        store.putString(UserPreferenceKeys.PALETTE_TINT_MODE, sanitizedMode)
        store.putInt(UserPreferenceKeys.PALETTE_TINT_STRENGTH, sanitizedStrength)
        store.putInt(UserPreferenceKeys.PALETTE_TINT_COLORFULNESS, sanitizedColorfulness)
        store.putInt(UserPreferenceKeys.PALETTE_TINT_TONE, sanitizedTone)
        return previous != PaletteTintPreferences.configKey(
            sanitizedMode,
            sanitizedStrength,
            sanitizedColorfulness,
            sanitizedTone,
        )
    }

    /** Returns true when palette-derived caches must be invalidated. */
    fun clearPaletteTint(): Boolean {
        val previous = paletteTintConfigKey()
        store.remove(UserPreferenceKeys.PALETTE_TINT_MODE)
        store.remove(UserPreferenceKeys.PALETTE_TINT_STRENGTH)
        store.remove(UserPreferenceKeys.PALETTE_TINT_COLORFULNESS)
        store.remove(UserPreferenceKeys.PALETTE_TINT_TONE)
        return previous != defaultPaletteTintConfigKey()
    }

    private fun paletteTintConfigKey(): String = PaletteTintPreferences.configKey(
        store.getString(UserPreferenceKeys.PALETTE_TINT_MODE, PaletteTintPreferences.DEFAULT),
        store.getInt(
            UserPreferenceKeys.PALETTE_TINT_STRENGTH,
            PaletteTintPreferences.DEFAULT_STRENGTH,
        ),
        store.getInt(
            UserPreferenceKeys.PALETTE_TINT_COLORFULNESS,
            PaletteTintPreferences.DEFAULT_COLORFULNESS,
        ),
        store.getInt(
            UserPreferenceKeys.PALETTE_TINT_TONE,
            PaletteTintPreferences.DEFAULT_TONE,
        ),
    )

    private fun defaultPaletteTintConfigKey(): String = PaletteTintPreferences.configKey(
        PaletteTintPreferences.DEFAULT,
        PaletteTintPreferences.DEFAULT_STRENGTH,
        PaletteTintPreferences.DEFAULT_COLORFULNESS,
        PaletteTintPreferences.DEFAULT_TONE,
    )

    private fun sanitizeDisplayStyle(value: String): String = when (value) {
        DisplayStylePreferences.CARD -> DisplayStylePreferences.CARD
        else -> DisplayStylePreferences.STANDARD
    }

    private fun serializeFloat(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
