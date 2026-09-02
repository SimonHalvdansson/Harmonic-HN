package com.simon.harmonichackernews.settings

object ThemePreferences {
    const val KEY = "pref_theme"
    const val NIGHTTIME_KEY = "pref_theme_nighttime"
    const val FOLLOW_SYSTEM_KEY = "pref_theme_follow_system"
    const val MANUAL_DARK_KEY = "pref_theme_manual_dark"
    const val LIGHT_KEY = "pref_theme_light"
    const val DARK_KEY = "pref_theme_dark"
    const val ACCENT_KEY = "pref_theme_accent"
    const val DEFAULT = "material_daynight"
    const val DEFAULT_NIGHTTIME = "dark"
    const val DEFAULT_LIGHT = "material_light"
    const val DEFAULT_DARK = "material_dark"
    const val MATERIAL_FIXED_AUTO = "material_fixed_daynight"
    const val MATERIAL_FIXED_LIGHT = "material_fixed_light"
    const val MATERIAL_FIXED_DARK = "material_fixed_dark"

    const val ACCENT_DEFAULT = "default"
    const val ACCENT_ORANGE = "orange"
    const val ACCENT_BLUE = "blue"
    const val ACCENT_VIOLET = "violet"
    const val ACCENT_TEAL = "teal"
    const val ACCENT_ROSE = "rose"

    fun isAutomatic(theme: String?): Boolean = theme in setOf(
        DEFAULT,
        MATERIAL_FIXED_AUTO,
        "darklight_daynight",
        "amoledwhite_daynight",
    )

    fun isDark(theme: String?): Boolean = theme in setOf(
        "material_dark",
        MATERIAL_FIXED_DARK,
        "dark",
        "hacker",
        "amoled",
        "gray",
    )

    fun selectableNighttimeTheme(theme: String?): String =
        theme?.takeIf(::isDark) ?: DEFAULT_NIGHTTIME

    fun selectableLightTheme(theme: String?): String = when (theme) {
        "material_light", MATERIAL_FIXED_LIGHT, "light", "hacker_news", "white" -> theme
        else -> pairedLightTheme(theme)
    }

    fun selectableDarkTheme(theme: String?): String = when (theme) {
        "material_dark", MATERIAL_FIXED_DARK, "dark", "hacker", "amoled", "gray" -> theme
        else -> pairedDarkTheme(theme)
    }

    fun pairedLightTheme(theme: String?): String = when (theme) {
        MATERIAL_FIXED_AUTO, MATERIAL_FIXED_LIGHT, MATERIAL_FIXED_DARK -> MATERIAL_FIXED_LIGHT
        "darklight_daynight", "light", "dark", "gray" -> "light"
        "amoledwhite_daynight", "amoled", "white" -> "white"
        "hacker", "hacker_news" -> "hacker_news"
        else -> DEFAULT_LIGHT
    }

    fun pairedDarkTheme(theme: String?): String = when (theme) {
        MATERIAL_FIXED_AUTO, MATERIAL_FIXED_LIGHT, MATERIAL_FIXED_DARK -> MATERIAL_FIXED_DARK
        "darklight_daynight", "light", "dark" -> "dark"
        "amoledwhite_daynight", "amoled", "white" -> "amoled"
        "hacker", "hacker_news" -> "hacker"
        "gray" -> "gray"
        else -> DEFAULT_DARK
    }

    fun sanitizeAccent(accent: String?): String = when (accent) {
        ACCENT_ORANGE, ACCENT_BLUE, ACCENT_VIOLET, ACCENT_TEAL, ACCENT_ROSE -> accent
        else -> ACCENT_DEFAULT
    }

    fun fixedMaterialEquivalent(theme: String?): String? = when (theme) {
        DEFAULT -> MATERIAL_FIXED_AUTO
        "material_light" -> MATERIAL_FIXED_LIGHT
        "material_dark" -> MATERIAL_FIXED_DARK
        else -> theme
    }
}

object PaletteTintPreferences {
    const val DEFAULT = "default"
    const val VIBRANT = "vibrant"
    const val DOMINANT = "dominant"
    const val MIN_STRENGTH = 0
    const val MAX_STRENGTH = 200
    const val MIN_COLORFULNESS = 0
    const val MAX_COLORFULNESS = 200
    const val MIN_TONE = -20
    const val MAX_TONE = 20
    const val DEFAULT_STRENGTH = 100
    const val DEFAULT_COLORFULNESS = 110
    const val DEFAULT_TONE = 0

    fun sanitizeMode(modeOrConfigKey: String?): String = when (modePart(modeOrConfigKey)) {
        VIBRANT -> VIBRANT
        DOMINANT -> DOMINANT
        else -> DEFAULT
    }

    fun configKey(
        mode: String?,
        strength: Int,
        colorfulness: Int,
        tone: Int,
    ): String = listOf(
        sanitizeMode(mode),
        clampStrength(strength),
        clampColorfulness(colorfulness),
        clampTone(tone),
    ).joinToString("|")

    fun normalizeConfigKey(modeOrConfigKey: String?): String {
        // This is called repeatedly while story rows resolve their preview and favicon tint state.
        // Split once instead of independently tokenizing the same value for every field.
        val parts = modeOrConfigKey?.split('|')
        val mode = when (parts?.getOrNull(0)) {
            VIBRANT -> VIBRANT
            DOMINANT -> DOMINANT
            else -> DEFAULT
        }
        val strength = clampStrength(
            parts?.getOrNull(1)?.toIntOrNull() ?: DEFAULT_STRENGTH,
        )
        val colorfulness = clampColorfulness(
            parts?.getOrNull(2)?.toIntOrNull() ?: DEFAULT_COLORFULNESS,
        )
        val tone = clampTone(parts?.getOrNull(3)?.toIntOrNull() ?: DEFAULT_TONE)
        return "$mode|$strength|$colorfulness|$tone"
    }

    fun strength(value: String?): Int = clampStrength(configInt(value, 1, DEFAULT_STRENGTH))
    fun colorfulness(value: String?): Int =
        clampColorfulness(configInt(value, 2, DEFAULT_COLORFULNESS))

    fun tone(value: String?): Int = clampTone(configInt(value, 3, DEFAULT_TONE))
    fun strengthMultiplier(value: String?): Float = strength(value) / 100f
    fun colorfulnessMultiplier(value: String?): Float = colorfulness(value) / 100f
    fun toneOffset(value: String?): Float = tone(value) / 100f
    fun modeLabel(value: String?): String = when (sanitizeMode(value)) {
        VIBRANT -> "Vibrant"
        DOMINANT -> "Dominant"
        else -> "Muted"
    }

    fun summary(value: String?): String {
        val label = modeLabel(value)
        return if (
            strength(value) == DEFAULT_STRENGTH &&
            colorfulness(value) == DEFAULT_COLORFULNESS &&
            tone(value) == DEFAULT_TONE
        ) {
            label
        } else {
            "$label, adjusted"
        }
    }

    fun clampStrength(value: Int): Int = value.coerceIn(MIN_STRENGTH, MAX_STRENGTH)
    fun clampColorfulness(value: Int): Int =
        value.coerceIn(MIN_COLORFULNESS, MAX_COLORFULNESS)

    fun clampTone(value: Int): Int = value.coerceIn(MIN_TONE, MAX_TONE)

    private fun modePart(value: String?): String = value.orEmpty().substringBefore('|')
    private fun configInt(value: String?, index: Int, default: Int): Int =
        value?.split('|')?.getOrNull(index)?.toIntOrNull() ?: default
}

object DisplayStylePreferences {
    const val STANDARD = "standard"
    const val CARD = "card"
}

enum class DisplayStyle(val storedValue: String) {
    STANDARD(DisplayStylePreferences.STANDARD),
    CARD(DisplayStylePreferences.CARD);

    companion object {
        fun fromStored(value: String?): DisplayStyle =
            entries.firstOrNull { it.storedValue == value } ?: STANDARD
    }
}

enum class CommentSortingPreference(val storedValue: String, val label: String) {
    DEFAULT("Default", "Default"),
    NEWEST_FIRST("Newest first", "Newest first"),
    OLDEST_FIRST("Oldest first", "Oldest first"),
    REPLY_COUNT("Reply count", "Reply count");

    companion object {
        fun fromStored(value: String?): CommentSortingPreference =
            entries.firstOrNull { it.storedValue == value } ?: DEFAULT
    }
}

enum class CommentsProvider(val storedValue: String, val label: String) {
    ALGOLIA("algolia", "Algolia API"),
    OFFICIAL("official", "Official Hacker News API");

    companion object {
        fun fromStored(value: String?): CommentsProvider =
            entries.firstOrNull { it.storedValue == value } ?: ALGOLIA
    }
}

enum class CommentVolumeNavigationMode(val storedValue: String, val label: String) {
    DISABLED("disabled", "Disabled"),
    TOP_LEVEL("top_level", "Top level comments"),
    ALL("all", "All comments");

    companion object {
        fun fromStored(value: String?): CommentVolumeNavigationMode =
            entries.firstOrNull { it.storedValue == value } ?: DISABLED
    }
}

object CommentNavigationPreferences {
    const val DISABLED = "disabled"
    const val TOP_LEVEL = "top_level"
}

object CommentDepthPreferences {
    const val THEME_DEFAULT = "theme_default"
    const val MATERIAL_YOU = "material_you"
    const val COLORS = "colors"
    const val MONOCHROME = "monochrome"
    const val NONE = "none"

    fun sanitizeMode(mode: String): String = when (mode) {
        MATERIAL_YOU, COLORS, MONOCHROME, NONE -> mode
        else -> THEME_DEFAULT
    }

    fun shouldShowIndicators(mode: String): Boolean = sanitizeMode(mode) != NONE

    fun modeLabel(mode: String): String = when (sanitizeMode(mode)) {
        MATERIAL_YOU -> "Material You"
        COLORS -> "Standard"
        MONOCHROME -> "Monochrome"
        NONE -> "None"
        else -> "Theme default"
    }
}

object FaviconPreferences {
    const val GOOGLE = "Google"
    const val DUCK_DUCK_GO = "DuckDuckGo"
    const val TWENTY = "Twenty icons"

    fun sanitizeProvider(provider: String?): String = when (provider) {
        DUCK_DUCK_GO, TWENTY -> provider
        else -> GOOGLE
    }
}

object WebViewPreferences {
    const val DEFAULT_MINIMUM_BATTERY = 0
    const val PRELOAD_ALWAYS = "always"
    const val PRELOAD_WIFI_ONLY = "onlywifi"
    const val PRELOAD_NEVER = "never"

    fun sanitizePreloadMode(mode: String?): String = when (mode) {
        PRELOAD_ALWAYS, PRELOAD_WIFI_ONLY -> mode
        else -> PRELOAD_NEVER
    }

    fun clampBatteryPercent(value: Int): Int = value.coerceIn(0, 100)
}

enum class WebViewPreloadMode(val storedValue: String, val label: String) {
    ALWAYS(WebViewPreferences.PRELOAD_ALWAYS, "Always"),
    WIFI_ONLY(WebViewPreferences.PRELOAD_WIFI_ONLY, "Only on WiFi"),
    NEVER(WebViewPreferences.PRELOAD_NEVER, "Never");

    fun summary(minimumBattery: Int): String {
        if (this == NEVER) return label
        val battery = WebViewPreferences.clampBatteryPercent(minimumBattery)
        return if (battery == 0) "$label, any battery level" else {
            "$label, battery at least $battery%"
        }
    }

    companion object {
        fun fromStored(value: String?): WebViewPreloadMode =
            entries.firstOrNull { it.storedValue == value } ?: NEVER
    }
}

enum class StoryPreviewMode(val storedValue: String) {
    OFF("off"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    companion object {
        fun fromStored(value: String?): StoryPreviewMode =
            entries.firstOrNull { it.storedValue == value } ?: SMALL
    }
}

object StoryCachePreferences {
    const val DEFAULT_COUNT = 20
    const val MIN_COUNT = 5
    const val MAX_COUNT = 200
    const val STEP = 5

    fun sanitizeCount(value: Int): Int {
        val clamped = value.coerceIn(MIN_COUNT, MAX_COUNT)
        return ((clamped + STEP / 2) / STEP) * STEP
    }
}

object AdditionalFrontpagePreferences {
    const val CLASSIC = "Classic"
    const val BEST_COMMENTS = "Best Comments"
    const val HIGHLIGHTS = "Highlights"
    const val ACTIVE = "Active"
    const val FRONT = "Front"
    val labels = listOf(CLASSIC, BEST_COMMENTS, HIGHLIGHTS, ACTIVE, FRONT)

    fun isLabel(value: String?): Boolean = value in labels

    fun sanitize(enabled: Set<String>?): Set<String> =
        labels.filterTo(linkedSetOf()) { it in enabled.orEmpty() }

    fun summary(enabled: Set<String>?): String =
        sanitize(enabled).takeIf(Set<String>::isNotEmpty)?.joinToString(", ") ?: "Off"
}
