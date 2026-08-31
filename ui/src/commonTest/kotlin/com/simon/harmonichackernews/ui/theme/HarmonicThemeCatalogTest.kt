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
}
