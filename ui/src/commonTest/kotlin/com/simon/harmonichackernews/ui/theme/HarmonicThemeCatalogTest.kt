package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.settings.ThemePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarmonicThemeCatalogTest {
    @Test
    fun classicDarkUpdateButtonHasReadableTextContrast() {
        val colors = HarmonicThemeCatalog.resolve("dark", systemDark = true).colors
        val foreground = colors.overlayButtonContent.luminance()
        val background = colors.overlayButton.luminance()
        val contrast = (maxOf(foreground, background) + 0.05f) /
            (minOf(foreground, background) + 0.05f)

        assertTrue(contrast >= 4.5f, "Update button text contrast was $contrast:1")
    }

    @Test
    fun materialDarkUsesLegacyCommentCountIndicatorColor() {
        val palette = HarmonicThemeCatalog.resolve(
            theme = "material_dark",
            systemDark = true,
        )

        assertEquals(Color(0xFF00668B), palette.colors.commentCountIndicator)
    }

    @Test
    fun defaultDarkUsesMaterialDarkCommentCountIndicatorColor() {
        val palette = HarmonicThemeCatalog.resolve(
            theme = ThemePreferences.DEFAULT,
            systemDark = true,
        )

        assertEquals(Color(0xFF00668B), palette.colors.commentCountIndicator)
    }

    @Test
    fun fixedMaterialLightUsesFixedPurplePalette() {
        val palette = HarmonicThemeCatalog.resolve(
            theme = ThemePreferences.MATERIAL_FIXED_LIGHT,
            systemDark = false,
        )

        assertEquals(Color(0xFF6750A4), palette.colorScheme.primary)
        assertEquals(Color(0xFFF3EDF7), palette.colors.settingsPageBackground)
        assertEquals(Color(0xFFEADDFF), palette.colors.overlayButton)
        assertEquals(Color(0xFF21005D), palette.colors.overlayButtonContent)
    }

    @Test
    fun fixedMaterialAutoFollowsSystemDarkMode() {
        val palette = HarmonicThemeCatalog.resolve(
            theme = ThemePreferences.MATERIAL_FIXED_AUTO,
            systemDark = true,
        )

        assertEquals(true, palette.dark)
        assertEquals(Color(0xFF4F378B), palette.colors.overlayButton)
        assertEquals(Color(0xFFEADDFF), palette.colors.overlayButtonContent)
    }

    @Test
    fun accentPresetOverridesInteractiveColorsWithoutReplacingSurfaces() {
        val base = HarmonicThemeCatalog.resolve("light", systemDark = false)
        val accented = HarmonicThemeCatalog.resolve(
            "light",
            systemDark = false,
            accentPreset = ThemePreferences.ACCENT_ORANGE,
        )

        assertEquals(base.colors.background, accented.colors.background)
        assertEquals(Color(0xFFA74413), accented.colors.accent)
        assertEquals(Color(0xFFA74413), accented.colorScheme.primary)
    }
}
