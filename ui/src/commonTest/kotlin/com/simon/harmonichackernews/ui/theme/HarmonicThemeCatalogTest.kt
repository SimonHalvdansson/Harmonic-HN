package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.settings.ThemePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun changingPageBackgroundDoesNotChangeReaderBackground() {
        val light = HarmonicThemeCatalog.resolve("light", systemDark = false)
        val changedPage = light.colors.copy(background = Color.Magenta)
        assertEquals(
            ReaderModeThemeFactory.create(light.colors, light = true, font = null, fontSizePx = 16).backgroundColor,
            ReaderModeThemeFactory.create(changedPage, light = true, font = null, fontSizePx = 16).backgroundColor,
        )
    }

    @Test
    fun fixedMaterialAutoFollowsSystemDarkMode() {
        for (systemDark in listOf(false, true)) {
            val palette = HarmonicThemeCatalog.resolve(
                theme = ThemePreferences.MATERIAL_FIXED_AUTO,
                systemDark = systemDark,
            )
            assertEquals(systemDark, palette.dark)
        }
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
        assertNotEquals(base.colors.accent, accented.colors.accent)
        assertEquals(accented.colors.accent, accented.colorScheme.primary)
    }

    @Test
    fun accentPaletteDoesNotDependOnPageBackground() {
        val base = HarmonicThemeCatalog.resolve("light", systemDark = false)
        val changedPage = base.copy(colors = base.colors.copy(background = Color.Magenta))

        val originalAccent = ThemeAccentCatalog.apply(base, ThemePreferences.ACCENT_ORANGE)
        val changedPageAccent = ThemeAccentCatalog.apply(changedPage, ThemePreferences.ACCENT_ORANGE)

        assertEquals(originalAccent.colors.settingsMainToggle, changedPageAccent.colors.settingsMainToggle)
        assertEquals(originalAccent.colorScheme.primaryContainer, changedPageAccent.colorScheme.primaryContainer)
    }
}
