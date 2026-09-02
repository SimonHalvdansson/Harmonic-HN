package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy

/** Writes typed settings without exposing a platform preference API to UI code. */
class StoredSettingsMutator(
    private val store: KeyValueStore,
) {
    fun setStoryBoolean(preference: StoryBooleanPreference, value: Boolean) {
        store.putBoolean(preference.storageKey, value)
    }

    fun setStoryString(preference: StoryStringPreference, value: String) {
        when (preference) {
            StoryStringPreference.DISPLAY_STYLE -> store.putString(
                UserPreferenceKeys.STORY_DISPLAY_STYLE,
                sanitizeDisplayStyle(value),
            )
        }
    }

    fun setStoryPreviewMode(value: StoryPreviewMode) {
        store.putString(UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE, value.storedValue)
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
        store.putBoolean(preference.storageKey, value)
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
        store.putBoolean(preference.storageKey, value)
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
        store.putBoolean(preference.storageKey, value)
    }

    fun setTheme(value: String) {
        store.update {
            putString(ThemePreferences.KEY, value)
            putBoolean(ThemePreferences.FOLLOW_SYSTEM_KEY, ThemePreferences.isAutomatic(value))
            if (ThemePreferences.isAutomatic(value)) {
                putString(ThemePreferences.LIGHT_KEY, ThemePreferences.pairedLightTheme(value))
                putString(ThemePreferences.DARK_KEY, ThemePreferences.pairedDarkTheme(value))
            } else if (ThemePreferences.isDark(value)) {
                putBoolean(ThemePreferences.MANUAL_DARK_KEY, true)
                putString(ThemePreferences.DARK_KEY, ThemePreferences.selectableDarkTheme(value))
            } else {
                putBoolean(ThemePreferences.MANUAL_DARK_KEY, false)
                putString(ThemePreferences.LIGHT_KEY, ThemePreferences.selectableLightTheme(value))
            }
        }
    }

    fun setFollowSystem(value: Boolean) =
        store.putBoolean(ThemePreferences.FOLLOW_SYSTEM_KEY, value)

    fun setManualDark(value: Boolean) = store.putBoolean(ThemePreferences.MANUAL_DARK_KEY, value)

    fun setLightTheme(value: String) =
        store.putString(ThemePreferences.LIGHT_KEY, ThemePreferences.selectableLightTheme(value))

    fun setDarkTheme(value: String) =
        store.putString(ThemePreferences.DARK_KEY, ThemePreferences.selectableDarkTheme(value))

    fun setAccentPreset(value: String) =
        store.putString(ThemePreferences.ACCENT_KEY, ThemePreferences.sanitizeAccent(value))

    fun setThemePair(lightTheme: String, darkTheme: String) {
        store.update {
            putString(
                ThemePreferences.LIGHT_KEY,
                ThemePreferences.selectableLightTheme(lightTheme),
            )
            putString(
                ThemePreferences.DARK_KEY,
                ThemePreferences.selectableDarkTheme(darkTheme),
            )
            putBoolean(ThemePreferences.FOLLOW_SYSTEM_KEY, true)
        }
    }

    fun setNighttimeTheme(value: String) {
        store.putString(
            ThemePreferences.NIGHTTIME_KEY,
            ThemePreferences.selectableNighttimeTheme(value),
        )
    }

    fun setGeneralBoolean(preference: GeneralBooleanPreference, value: Boolean) {
        store.putBoolean(preference.storageKey, value)
    }

    fun setDebugBoolean(preference: DebugBooleanPreference, value: Boolean) {
        store.putBoolean(preference.storageKey, value)
    }

    fun applyWelcomePreset(expressive: Boolean) {
        store.putBoolean(UserPreferenceKeys.TINT_CARD_USING_PREVIEW, expressive)
        store.putString(
            UserPreferenceKeys.FONT,
            if (expressive) "googlesansflexrounded" else "productsans",
        )
        store.putString(
            UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE,
            if (expressive) {
                StoryPreviewMode.SMALL.storedValue
            } else {
                StoryPreviewMode.OFF.storedValue
            },
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
