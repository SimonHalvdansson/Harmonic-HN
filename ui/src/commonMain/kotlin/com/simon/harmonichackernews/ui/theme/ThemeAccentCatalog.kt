package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.settings.ThemePreferences

data class ThemeAccentOption(
    val value: String,
    val label: String,
    val lightColor: Color?,
    val darkColor: Color?,
)

/** Accent overrides intentionally leave neutral surfaces alone so every base theme keeps its identity. */
object ThemeAccentCatalog {
    val options = listOf(
        ThemeAccentOption(ThemePreferences.ACCENT_DEFAULT, "Theme default", null, null),
        ThemeAccentOption(
            ThemePreferences.ACCENT_ORANGE,
            "Orange",
            Color(0xFFA74413),
            Color(0xFFFFB68E),
        ),
        ThemeAccentOption(
            ThemePreferences.ACCENT_BLUE,
            "Blue",
            Color(0xFF365FB5),
            Color(0xFFAFC6FF),
        ),
        ThemeAccentOption(
            ThemePreferences.ACCENT_VIOLET,
            "Violet",
            Color(0xFF7048A6),
            Color(0xFFD5BAFF),
        ),
        ThemeAccentOption(
            ThemePreferences.ACCENT_TEAL,
            "Teal",
            Color(0xFF00796B),
            Color(0xFF73DBC8),
        ),
        ThemeAccentOption(
            ThemePreferences.ACCENT_ROSE,
            "Rose",
            Color(0xFFA83E60),
            Color(0xFFFFB1C0),
        ),
    )

    fun label(value: String): String = options.firstOrNull {
        it.value == ThemePreferences.sanitizeAccent(value)
    }?.label ?: options.first().label

    fun color(value: String, dark: Boolean): Color? = options.firstOrNull {
        it.value == ThemePreferences.sanitizeAccent(value)
    }?.let { if (dark) it.darkColor else it.lightColor }

    fun apply(palette: HarmonicThemePalette, value: String): HarmonicThemePalette {
        val accent = color(value, palette.dark) ?: return palette
        val colors = palette.colors
        val scheme = palette.colorScheme
        val onAccent = if (accent.luminance() > 0.42f) Color(0xFF171717) else Color.White
        val accentContainer = lerp(
            colors.background,
            accent,
            if (palette.dark) 0.34f else 0.20f,
        )
        return palette.copy(
            colors = colors.copy(
                accent = accent,
                link = accent,
                commentCountIndicator = accent,
                settingsMainToggle = accentContainer,
                settingsMainToggleText = colors.textPrimary,
                overlayButton = accentContainer,
                overlayButtonContent = colors.textPrimary,
            ),
            colorScheme = scheme.copy(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = accentContainer,
                onPrimaryContainer = colors.textPrimary,
                secondary = accent,
                secondaryContainer = accentContainer,
                onSecondaryContainer = colors.textPrimary,
            ),
        )
    }
}
