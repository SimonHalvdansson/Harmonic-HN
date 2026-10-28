package com.simon.harmonichackernews.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/** Rectangular theme backgrounds only invalidate drawing as their color animates. */
@Composable
fun Modifier.themeBackground(color: HarmonicColors.() -> Color): Modifier {
    val colors = HarmonicTheme.colors
    return drawBehind { drawRect(colors.color()) }
}
