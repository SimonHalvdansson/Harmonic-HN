package com.simon.harmonichackernews.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Canonical cross-platform theme preview palettes; native theme adapters may still resolve attrs. */
object ThemePreviewCatalog {
    private val pink = Color(0xFFFF959E)
    private val green = Color(0xFF4C9B7B)
    private val darkText = Color(0xFFDFDFDF)
    private val darkSecondary = Color(0xFFBBBBCC)
    private val lightText = Color(0xFF2F2F2F)
    private val lightSecondary = Color(0xFF4A4A4A)

    private val dark = ThemePreviewPalette(
        background = Color(0xFF222431),
        surface = Color(0xFF14191E),
        accent = pink,
        text = darkText,
        secondaryText = darkSecondary,
        dark = true,
    )
    private val materialDark = ThemePreviewPalette(
        background = Color(0xFF191C1E),
        surface = Color(0xFF2A3136),
        accent = Color(0xFF8094A0),
        text = darkText,
        secondaryText = Color(0xFF9AAEBB),
        dark = true,
    )
    private val gray = ThemePreviewPalette(
        background = Color(0xFF292A2E),
        surface = lerp(Color(0xFF292A2E), darkText, 0.07f),
        accent = pink,
        text = darkText,
        secondaryText = darkSecondary,
        dark = true,
    )
    private val black = ThemePreviewPalette(
        Color.Black, Color.Black, pink, darkText, darkSecondary, true,
    )
    private val hacker = ThemePreviewPalette(
        Color.Black,
        Color.Black,
        Color(0xFF00FF00),
        Color(0xFF00FF00),
        Color(0x9900FF00),
        true,
    )
    private val light = ThemePreviewPalette(
        background = Color(0xFFF6F6EF),
        surface = lerp(Color(0xFFF6F6EF), Color.White, 0.56f),
        accent = green,
        text = lightText,
        secondaryText = lightSecondary,
        dark = false,
    )
    private val hackerNews = ThemePreviewPalette(
        Color(0xFFF6F6EF),
        Color(0xFFEEEBD9),
        Color(0xFFFF6600),
        Color(0xFF222222),
        Color(0xFF828282),
        false,
    )
    private val materialLight = ThemePreviewPalette(
        Color(0xFFF0F0F3),
        Color(0xFFEBF1F8),
        Color(0xFF8094A0),
        lightText,
        Color(0xFF4E616C),
        false,
    )
    private val white = ThemePreviewPalette(
        Color.White,
        lerp(Color.White, lightText, 0.035f),
        green,
        lightText,
        lightSecondary,
        false,
    )

    fun palettes(theme: String): Pair<ThemePreviewPalette, ThemePreviewPalette?> = when (theme) {
        "material_daynight" -> materialLight to materialDark
        "darklight_daynight" -> light to dark
        "amoledwhite_daynight" -> white to black
        "material_light" -> materialLight to null
        "material_dark" -> materialDark to null
        "light" -> light to null
        "hacker" -> hacker to null
        "hacker_news" -> hackerNews to null
        "amoled" -> black to null
        "white" -> white to null
        "gray" -> gray to null
        else -> dark to null
    }
}
