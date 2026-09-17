package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path

/** Maps an element's center with its container while preserving the element's intrinsic size. */
internal fun moveRectBetweenContainers(rect: Rect, from: Rect, to: Rect): Rect {
    if (from.width <= 0f || from.height <= 0f) return rect
    val centerX = to.left + (rect.center.x - from.left) / from.width * to.width
    val centerY = to.top + (rect.center.y - from.top) / from.height * to.height
    return Rect(
        left = centerX - rect.width / 2f,
        top = centerY - rect.height / 2f,
        right = centerX + rect.width / 2f,
        bottom = centerY + rect.height / 2f,
    )
}

internal fun lerpRect(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * progress,
    top = start.top + (end.top - start.top) * progress,
    right = start.right + (end.right - start.right) * progress,
    bottom = start.bottom + (end.bottom - start.bottom) * progress,
)

internal fun roundedRectPath(
    rect: Rect,
    topRadius: Float,
    bottomRadius: Float = topRadius,
): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = CornerRadius(topRadius),
            topRight = CornerRadius(topRadius),
            bottomRight = CornerRadius(bottomRadius),
            bottomLeft = CornerRadius(bottomRadius),
        ),
    )
}
