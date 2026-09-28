package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue

class FrontpageSelectionColorTest {
    @Test
    fun selectedContainerKeepsItsColorWhenAlreadyDistinct() {
        val container = Color(0xffd0bcff)
        assertEquals(container, frontpageSelectionColor(Color.White, container))
    }

    @Test
    fun selectedContainerRemainsDistinctEvenWhenThemeUsesItForThePage() {
        for (page in listOf(Color(0xffe8def8), Color(0xff292a2e))) {
            val selected = frontpageSelectionColor(page, page)
            assertTrue(contrast(page, selected) >= 1.3f)
        }
    }

    @Test
    fun themeAndAccentCombinationsKeepSelectionDistinctAndItsNameReadable() {
        val themes = listOf(
            "light", "white", "dark", "gray", "amoled", "hacker", "hacker_news",
            "material_light", "material_dark", "material_fixed_light", "material_fixed_dark",
        )
        for (theme in themes) for (accent in ThemeAccentCatalog.options) {
            val palette = HarmonicThemeCatalog.resolve(theme, false, accent.value)
            val colors = palette.colors
            val selected = frontpageSelectionColor(
                colors.background, palette.colorScheme.secondaryContainer,
            )
            assertTrue(contrast(colors.background, selected) >= 1.3f, "$theme/${accent.value}")
            assertTrue(contrast(palette.colorScheme.onSecondaryContainer, selected) >= 4.5f, "Name in $theme/${accent.value}")
        }
    }

    private fun contrast(first: Color, second: Color): Float =
        (maxOf(first.luminance(), second.luminance()) + 0.05f) /
            (minOf(first.luminance(), second.luminance()) + 0.05f)
}
