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
}
