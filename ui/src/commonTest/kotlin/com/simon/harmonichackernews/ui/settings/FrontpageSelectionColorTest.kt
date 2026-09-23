package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrontpageSelectionColorTest {
    @Test
    fun selectedCardUsesSubtleLightWashAndDistinctDarkTone() {
        for (card in listOf(Color.White, Color(0xff181818))) {
            val accent = Color(0xff6d638c)
            val page = accent.copy(alpha = 0.16f).compositeOver(card)
            val selected = frontpageSelectionColor(page, card, accent)
            if (page.luminance() > 0.5f) {
                assertEquals(accent.copy(alpha = 0.05f).compositeOver(card), selected)
            } else {
                assertTrue(contrast(page, selected) >= 1.3f)
            }
        }
    }

    @Test
    fun themeAndAccentCombinationsKeepSelectionGentleAndItsNameReadable() {
        val themes = listOf(
            "light", "white", "dark", "gray", "amoled", "hacker", "hacker_news",
            "material_light", "material_dark", "material_fixed_light", "material_fixed_dark",
        )
        for (theme in themes) for (accent in ThemeAccentCatalog.options) {
            val palette = HarmonicThemeCatalog.resolve(theme, false, accent.value)
            val colors = palette.colors
            val selected = frontpageSelectionColor(
                colors.background, colors.itemBackground, palette.colorScheme.primary,
            )
            if (colors.background.luminance() > 0.5f) {
                assertEquals(
                    palette.colorScheme.primary.copy(alpha = 0.05f).compositeOver(colors.itemBackground),
                    selected,
                    "$theme/${accent.value}",
                )
            } else {
                assertTrue(contrast(colors.background, selected) >= 1.3f, "$theme/${accent.value}")
            }
            assertTrue(contrast(colors.textPrimary, selected) >= 4.5f, "Name in $theme/${accent.value}")
        }
    }

    private fun contrast(first: Color, second: Color): Float =
        (maxOf(first.luminance(), second.luminance()) + 0.05f) /
            (minOf(first.luminance(), second.luminance()) + 0.05f)
}
