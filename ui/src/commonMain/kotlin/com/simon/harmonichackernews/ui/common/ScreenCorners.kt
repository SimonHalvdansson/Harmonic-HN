package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

/** Physical corners in density-independent units, in screen order (never RTL-relative). */
internal data class ScreenCorners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
)

/** Zero for unsupported platforms, older OS versions and non-full-screen windows. */
@Composable
internal expect fun rememberScreenCorners(): ScreenCorners

internal fun ScreenCorners.forViewport(bounds: Rect?, windowSize: IntSize): ScreenCorners {
    val fullWindow = bounds != null && windowSize.width > 0 && windowSize.height > 0 &&
        abs(bounds.left) <= 1f && abs(bounds.top) <= 1f &&
        abs(bounds.right - windowSize.width) <= 1f && abs(bounds.bottom - windowSize.height) <= 1f
    return if (fullWindow) this else ScreenCorners()
}

internal fun ScreenCorners.interpolateFrom(radius: Float, progress: Float): ScreenCorners {
    val p = progress.coerceIn(0f, 1f)
    fun corner(target: Float) = radius + (target - radius) * p
    return ScreenCorners(corner(topLeft), corner(topRight), corner(bottomRight), corner(bottomLeft))
}
