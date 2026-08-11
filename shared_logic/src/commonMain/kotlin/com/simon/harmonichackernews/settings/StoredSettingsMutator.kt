package com.simon.harmonichackernews.settings

/** Writes typed settings without exposing a platform preference API to UI code. */
class StoredSettingsMutator(
    private val store: KeyValueStore,
) {
    fun setFont(font: String) {
        store.putString(UserPreferenceKeys.FONT, TextPreferences.sanitizeFont(font))
    }

    fun setReaderModeFont(font: String) {
        store.putString(UserPreferenceKeys.READER_MODE_FONT, TextPreferences.sanitizeFont(font))
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
}
