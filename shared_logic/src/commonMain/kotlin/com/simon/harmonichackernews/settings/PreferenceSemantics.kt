package com.simon.harmonichackernews.settings

object ThemePreferences {
    const val DEFAULT = "material_daynight"
    const val DEFAULT_NIGHTTIME = "dark"

    fun isAutomatic(theme: String?): Boolean = theme in setOf(
        DEFAULT,
        "darklight_daynight",
        "amoledwhite_daynight",
    )

    fun isDark(theme: String?): Boolean = theme in setOf(
        "material_dark",
        "dark",
        "hacker",
        "amoled",
        "gray",
    )

    fun selectableNighttimeTheme(theme: String?): String =
        theme?.takeIf(::isDark) ?: DEFAULT_NIGHTTIME
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

    fun normalizeConfigKey(modeOrConfigKey: String?): String = configKey(
        modeOrConfigKey,
        strength(modeOrConfigKey),
        colorfulness(modeOrConfigKey),
        tone(modeOrConfigKey),
    )

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

    fun clampStrength(value: Int): Int = value.coerceIn(MIN_STRENGTH, MAX_STRENGTH)
    fun clampColorfulness(value: Int): Int =
        value.coerceIn(MIN_COLORFULNESS, MAX_COLORFULNESS)

    fun clampTone(value: Int): Int = value.coerceIn(MIN_TONE, MAX_TONE)

    private fun modePart(value: String?): String = value.orEmpty().substringBefore('|')
    private fun configInt(value: String?, index: Int, default: Int): Int =
        value?.split('|')?.getOrNull(index)?.toIntOrNull() ?: default
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
    const val PRELOAD_ALWAYS = "always"
    const val PRELOAD_WIFI_ONLY = "onlywifi"
    const val PRELOAD_NEVER = "never"

    fun sanitizePreloadMode(mode: String?): String = when (mode) {
        PRELOAD_ALWAYS, PRELOAD_WIFI_ONLY -> mode
        else -> PRELOAD_NEVER
    }

    fun clampBatteryPercent(value: Int): Int = value.coerceIn(0, 100)
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
