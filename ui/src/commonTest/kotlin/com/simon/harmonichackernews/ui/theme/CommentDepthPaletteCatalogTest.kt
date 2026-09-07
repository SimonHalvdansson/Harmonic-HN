package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentDepthPaletteCatalogTest {
    @Test
    fun authorColorsStayStableAcrossDepthAndTheme() {
        for (theme in listOf("light", "dark", "material_dark")) {
            for (depth in listOf(0, 1, 6, 7, 100)) {
                assertEquals(authorColor("pg"), authorColor("pg", depth, theme))
                assertEquals(authorColor("dang"), authorColor("dang", depth, theme))
            }
        }
    }

    @Test
    fun authorColorsHaveLargeOutputSpaceWithBoundedSaturationAndLightness() {
        val names = listOf("", "Åsa", "🦦", "x".repeat(1000)) + (0..9999).map { "user$it" }
        val assigned = names.map { authorColor(it) }
        assertTrue(assigned.toSet().size > 9000, "Author colors should span thousands of distinct values")
        for (color in assigned) {
            val max = maxOf(color.red, color.green, color.blue)
            val min = minOf(color.red, color.green, color.blue)
            val lightness = (max + min) / 2f
            val saturation = (max - min) / (1f - kotlin.math.abs(2f * lightness - 1f))
            assertTrue(lightness in 0.455f..0.625f)
            assertTrue(saturation in 0.49f..0.71f)
            assertEquals(1f, color.alpha)
        }
    }

    @Test
    fun dialogExamplesUseStableAuthorMapping() {
        val expected = listOf("willow", "compass", "otter", "lantern", "pebble", "meadow", "saffron")
            .map { authorColor(it) }
        assertEquals(expected, CommentDepthPaletteCatalog.previewColors(CommentDepthPreferences.AUTHOR, "light", false))
        assertEquals(List(7) { Color.Transparent }, CommentDepthPaletteCatalog.previewColors(CommentDepthPreferences.NONE, "light", false))
    }

    private fun authorColor(author: String, depth: Int = 0, theme: String = "light") =
        CommentDepthPaletteCatalog.color(CommentDepthPreferences.AUTHOR, theme, theme != "light", depth, author)
}
