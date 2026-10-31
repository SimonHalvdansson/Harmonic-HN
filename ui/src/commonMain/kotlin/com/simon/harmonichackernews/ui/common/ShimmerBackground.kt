package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform

/** Two cached gradient segments preserve a moving color stop without rebuilding its shader. */
internal fun Modifier.shimmerBackground(
    base: Color,
    highlight: Color,
    progress: () -> Float,
): Modifier = drawWithCache {
    val rising = Brush.horizontalGradient(listOf(base, highlight), endX = 1f)
    val falling = Brush.horizontalGradient(listOf(highlight, base), endX = 1f)
    val strip = Size(1f, size.height)
    onDrawBehind {
        val peak = size.width * progress().coerceIn(0f, 1f)
        if (peak > 0f) withTransform({ scale(peak, 1f, Offset.Zero) }) {
            drawRect(rising, size = strip)
        }
        val remaining = size.width - peak
        if (remaining > 0f) withTransform({
            translate(left = peak)
            scale(remaining, 1f, Offset.Zero)
        }) {
            drawRect(falling, size = strip)
        }
    }
}
