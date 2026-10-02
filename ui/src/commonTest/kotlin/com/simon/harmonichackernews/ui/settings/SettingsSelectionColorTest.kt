package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

class SettingsSelectionColorTest {
    @Test
    fun activeCategoryIsDistinctFromPageAndRowsAcrossThemes() {
        for (theme in listOf("default", "material_light", "material_dark", "light", "dark",
            "white", "amoled", "gray", "hacker", "hacker_news")) {
            for (dark in listOf(false, true)) {
                val colors = HarmonicThemeCatalog.resolve(theme, dark).colors
                val selected = settingsSelectionColor(colors)
                assertTrue(contrast(selected, colors.background) >= 1.15f, "$theme page")
                assertTrue(contrast(selected, colors.itemBackground) >= 1.15f, "$theme rows")
                assertTrue(contrast(selected, colors.contentPrimary) >= 4.5f, "$theme text")
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) /
            (minOf(a.luminance(), b.luminance()) + 0.05f)
}
