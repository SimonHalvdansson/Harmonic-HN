package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import kotlin.math.absoluteValue

/** Canonical thread-depth colors and selection policy for every Compose host. */
object CommentDepthPaletteCatalog {
    const val colorCount = 7
    private val dark = listOf(
        Color(0xFFFF959E), Color(0xFF3F51B5), Color(0xFFFF0266), Color(0xFF1EB980),
        Color(0xFF00ACC1), Color(0xFF5D4037), Color(0xFFD32F2F),
    )
    private val light = listOf(
        Color(0xFFA14010), Color(0xFF3F51B5), Color(0xFFFF0266), Color(0xFF1EB980),
        Color(0xFF00ACC1), Color(0xFF5D4037), Color(0xFFD32F2F),
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
        CommentDepthPreferences.COLORS -> if (darkTheme) dark else light
        CommentDepthPreferences.NONE -> emptyList()
        else -> if (theme?.startsWith("material") == true) material
        else if (darkTheme) dark else light
    }

    fun color(
        mode: String,
        theme: String?,
        darkTheme: Boolean,
        depth: Int,
    ): Color = colors(mode, theme, darkTheme).let { palette ->
        if (palette.isEmpty()) Color.Transparent
        else palette[(depth % palette.size).absoluteValue]
    }
}
