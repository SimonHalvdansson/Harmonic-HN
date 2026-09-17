package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import kotlin.math.absoluteValue

/** Canonical thread-depth colors and selection policy for every Compose host. */
object CommentDepthPaletteCatalog {
    const val colorCount = 7
    private val colors = listOf(
        Color(0xFF5E97F6), Color(0xFF9CCC65), Color(0xFFFFB74D), Color(0xFFBA68C8),
        Color(0xFF4DD0E1), Color(0xFFEF5350), Color(0xFFFFD54F),
    )
    private val material = listOf(
        Color(0xFF526A78), Color(0xFF7B94A2), Color(0xFF6D7F89), Color(0xFF8DA5B2),
        Color(0xFF9AAEBB), Color(0xFF41545F), Color(0xFF72828B),
    )
    private val monochrome = List(colorCount) { Color(0xFF808080) }

    fun colors(mode: String, theme: String?, darkTheme: Boolean): List<Color> = when (
        CommentDepthPreferences.sanitizeMode(mode)
    ) {
        CommentDepthPreferences.MONOCHROME -> monochrome
        CommentDepthPreferences.MATERIAL_YOU -> material
        CommentDepthPreferences.COLORS -> colors
        CommentDepthPreferences.AUTHOR -> previewColors(mode, theme, darkTheme)
        CommentDepthPreferences.NONE -> emptyList()
        else -> if (theme?.startsWith("material") == true) material
        else colors
    }

    fun color(
        mode: String,
        theme: String?,
        darkTheme: Boolean,
        depth: Int,
        author: String = "",
    ): Color {
        if (mode == CommentDepthPreferences.AUTHOR) return authorColor(author)
        val palette = colors(mode, theme, darkTheme)
        return if (palette.isEmpty()) Color.Transparent
        else palette[(depth % palette.size).absoluteValue]
    }

    private fun authorColor(author: String): Color {
        val hash = authorHash(author)
        // 360 hues × 21 saturation levels × 17 lightness levels = 128,520 choices.
        // Bound saturation to 50–70% and lightness to 46–62% to avoid muddy,
        // neon, near-white, and near-black colors while keeping the mapping theme-independent.
        return Color.hsl(
            hue = (hash % 360u).toFloat(),
            saturation = 0.50f + ((hash / 360u) % 21u).toFloat() / 100f,
            lightness = 0.46f + ((hash / (360u * 21u)) % 17u).toFloat() / 100f,
        )
    }

    // Fixed words keep the examples stable across recompositions and dialog openings.
    private val previewAuthors = listOf("willow", "compass", "otter", "lantern", "pebble", "meadow", "saffron")

    fun previewColors(mode: String, theme: String?, darkTheme: Boolean): List<Color> =
        previewAuthors.mapIndexed { depth, author -> color(mode, theme, darkTheme, depth, author) }

    // Explicit FNV-1a over UTF-16 code units keeps the mapping identical on every host.
    // Unsigned arithmetic also handles overflow without negative palette indices.
    private fun authorHash(author: String): UInt = author.fold(2166136261u) { hash, character ->
        (hash xor character.code.toUInt()) * 16777619u
    }

}
