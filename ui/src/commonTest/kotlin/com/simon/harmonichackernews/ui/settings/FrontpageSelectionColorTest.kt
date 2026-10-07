package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ColorSchemeCatalog
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
    fun everySchemeKeepsSelectionDistinctAndItsNameReadable() {
        for (option in ColorSchemeCatalog.options) for (dark in listOf(false, true)) {
            val palette = HarmonicThemeCatalog.scheme(option.value, dark)
            val colors = palette.colorScheme
            val selected = frontpageSelectionColor(
                colors.pageBackground, palette.colorScheme.secondaryContainer,
            )
            assertTrue(contrast(colors.pageBackground, selected) >= 1.3f, "${option.value}/$dark")
            val content = frontpageSelectionContentColor(selected, palette.colorScheme.onSecondaryContainer)
            assertTrue(contrast(content, selected) >= 4.5f, "Name in ${option.value}/$dark")
        }
    }

    @Test
    fun migratedAndroidLabelUsesAReadableForegroundWithoutChangingThePalette() {
        val selected = Color(0xFF99595E)
        val preferred = Color(0xFFFFD9DC)
        assertTrue(contrast(preferred, selected) < 4.5f)
        assertEquals(Color.White, frontpageSelectionContentColor(selected, preferred))
        assertEquals(Color.White, frontpageSelectionContentColor(Color.Black, Color.White))
    }

    private fun contrast(first: Color, second: Color): Float =
        (maxOf(first.luminance(), second.luminance()) + 0.05f) /
            (minOf(first.luminance(), second.luminance()) + 0.05f)
}
