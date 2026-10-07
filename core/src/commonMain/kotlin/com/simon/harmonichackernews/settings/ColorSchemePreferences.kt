package com.simon.harmonichackernews.settings

/** Material Color Utilities palette recipes, stored independently of the source color. */
enum class ColorSchemeStyle(val storedValue: String) {
    NeutralSurfaces("rainbow"), Balanced("tonal_spot"), Vibrant("vibrant");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.storedValue == value } ?: Balanced
    }
}

/**
 * Scheme identity is independent of appearance mode. Every identity has two Material palettes.
 * The paired fields retain backup compatibility; repository reads and writes always synchronize them.
 */
data class ColorSchemeSelection(
    val light: String = ColorSchemePreferences.DYNAMIC,
    val dark: String = light,
    val coupled: Boolean = light == dark,
    val nighttime: String = ColorSchemePreferences.CLASSIC,
    val lightStyle: ColorSchemeStyle = ColorSchemeStyle.Balanced,
    val darkStyle: ColorSchemeStyle = lightStyle,
    val nighttimeStyle: ColorSchemeStyle = ColorSchemeStyle.Balanced,
) {
    fun forMode(darkMode: Boolean): String = if (darkMode) dark else light
    fun styleForMode(darkMode: Boolean): ColorSchemeStyle = if (darkMode) darkStyle else lightStyle
}

object ColorSchemePreferences {
    const val LIGHT_STYLE_KEY = "pref_color_scheme_light_style"
    const val DARK_STYLE_KEY = "pref_color_scheme_dark_style"
    const val NIGHTTIME_STYLE_KEY = "pref_color_scheme_nighttime_style"
    const val LIGHT_KEY = "pref_color_scheme_light"
    const val DARK_KEY = "pref_color_scheme_dark"
    const val COUPLED_KEY = "pref_color_scheme_coupled"
    const val NIGHTTIME_KEY = "pref_color_scheme_nighttime"

    const val DYNAMIC = "dynamic"
    const val CLASSIC = "classic"
    const val PURE = "pure"
    const val HACKER_NEWS = "hacker_news"
    const val HACKER = "hacker"
    const val GRAY = "gray"
    val generatedValues = listOf("orange", "blue", "violet", "teal", "rose", "green", "amber", "slate")
    val values = listOf(DYNAMIC) + generatedValues + listOf(CLASSIC, PURE, HACKER_NEWS, HACKER, GRAY)
    fun supportsStyle(value: String): Boolean = value in values && value != DYNAMIC

    fun sanitize(value: String?): String = value?.takeIf { it in values } ?: DYNAMIC

    fun fromLegacyTheme(theme: String?, color: String? = null): String = when {
        ThemePreferences.isMaterial(theme) -> when (val preset = ThemePreferences.sanitizeMaterialColor(color)) {
            ThemePreferences.COLOR_SYSTEM -> DYNAMIC
            else -> preset
        }
        theme in listOf("light", "dark", "darklight_daynight") -> CLASSIC
        theme in listOf("white", "amoled", "amoledwhite_daynight") -> PURE
        else -> sanitize(theme)
    }

    /** The saved light selection is the shared scheme; old dark/coupling keys are compatibility data. */
    fun read(store: KeyValueStore): ColorSchemeSelection {
        val legacy = store.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT)
        val color = store.getString(ThemePreferences.COLOR_KEY)
        val darkOnly = !store.contains(LIGHT_KEY) && store.contains(DARK_KEY)
        val light = when {
            store.contains(LIGHT_KEY) -> sanitize(store.getString(LIGHT_KEY))
            darkOnly -> sanitize(store.getString(DARK_KEY))
            else -> fromLegacyTheme(
                store.getString(ThemePreferences.LIGHT_KEY)
                    ?: if (ThemePreferences.isAutomatic(legacy)) ThemePreferences.pairedLightTheme(legacy) else legacy,
                color,
            )
        }
        val nighttime = if (store.contains(NIGHTTIME_KEY)) sanitize(store.getString(NIGHTTIME_KEY)) else fromLegacyTheme(
            ThemePreferences.selectableNighttimeTheme(store.getString(ThemePreferences.NIGHTTIME_KEY)), color,
        )
        // Missing/unknown styles stay Balanced; retain the old stored name for Neutral.
        val lightStyle = ColorSchemeStyle.fromStored(store.getString(if (darkOnly) DARK_STYLE_KEY else LIGHT_STYLE_KEY))
        val nighttimeStyle = ColorSchemeStyle.fromStored(store.getString(NIGHTTIME_STYLE_KEY))
        return ColorSchemeSelection(
            light = light, dark = light, coupled = true, nighttime = nighttime,
            lightStyle = lightStyle, darkStyle = lightStyle, nighttimeStyle = nighttimeStyle,
        )
    }

    fun write(store: KeyValueStore, selection: ColorSchemeSelection) = store.update {
        putString(LIGHT_KEY, sanitize(selection.light))
        putString(DARK_KEY, sanitize(selection.light))
        putBoolean(COUPLED_KEY, true)
        putString(NIGHTTIME_KEY, sanitize(selection.nighttime))
        putString(LIGHT_STYLE_KEY, selection.lightStyle.storedValue)
        putString(DARK_STYLE_KEY, selection.lightStyle.storedValue)
        putString(NIGHTTIME_STYLE_KEY, selection.nighttimeStyle.storedValue)
    }
}
