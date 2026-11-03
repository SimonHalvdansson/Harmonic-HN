package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.graphics.Color

internal fun askUserBubbleColors(scheme: androidx.compose.material3.ColorScheme): Pair<Color, Color> =
    scheme.primary to com.simon.harmonichackernews.ui.theme.contrastingColor(scheme.onPrimary, listOf(scheme.primary), 4.5f)

/** A light backdrop during the container transform, clear at both resting endpoints. */
internal fun askBackgroundDimAlpha(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return (if (p <= .65f) p / .65f else (1f - p) / .35f).coerceIn(0f, 1f) * .12f
}
