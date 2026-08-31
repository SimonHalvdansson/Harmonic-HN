package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog

/** Canonical cross-platform theme preview palettes; native theme adapters may still resolve attrs. */
object ThemePreviewCatalog {
    fun palettes(theme: String): Pair<ThemePreviewPalette, ThemePreviewPalette?> = when (theme) {
        "material_daynight", "material_fixed_daynight", "darklight_daynight",
        "amoledwhite_daynight" ->
            preview(theme, false) to preview(theme, true)
        else -> preview(theme, false) to null
    }

    private fun preview(theme: String, systemDark: Boolean): ThemePreviewPalette =
        HarmonicThemeCatalog.resolve(theme, systemDark).let { palette ->
            ThemePreviewPalette(
                background = palette.colors.background,
                surface = palette.colors.surfaceContainerHigh,
                accent = palette.colorScheme.primary,
                text = palette.colors.textPrimary,
                secondaryText = palette.colors.textSecondary,
                dark = palette.dark,
            )
        }
}
