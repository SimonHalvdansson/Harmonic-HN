package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.ui.theme.colorContrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryMapPaletteTest {
    @Test
    fun smallMapLabelsStayReadableOnLightDarkAndTintedSurfaces() {
        for (background in listOf(Color.White, Color(0xFFF3F3FB), Color(0xFF111218), Color(0xFF262C24))) {
            val palette = categoryMapPalette(background)
            assertEquals(ArticleCategories.defaults.size, palette.size)
            assertEquals(palette.size, palette.distinct().size)
            palette.forEach { assertTrue(colorContrast(it, background) >= 4.49f) }
        }
    }
}
