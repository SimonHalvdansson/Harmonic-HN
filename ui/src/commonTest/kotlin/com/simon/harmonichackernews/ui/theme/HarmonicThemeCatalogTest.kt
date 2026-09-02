package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.ThemePreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class HarmonicThemeCatalogTest {
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
