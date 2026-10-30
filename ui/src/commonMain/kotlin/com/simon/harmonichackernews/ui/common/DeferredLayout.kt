package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.dp

/** Read changing dimensions in measurement, without invalidating content composition. */
internal fun Modifier.layoutHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val pixels = constraints.constrainHeight(height().roundToPx().coerceAtLeast(0))
    val child = measurable.measure(constraints.copy(minHeight = pixels, maxHeight = pixels))
    layout(child.width, pixels) { child.placeRelative(0, 0) }
}

internal fun Modifier.layoutVerticalPadding(
    top: () -> Dp = { 0.dp },
    bottom: () -> Dp = { 0.dp },
): Modifier = layout { measurable, constraints ->
    val topPx = top().roundToPx().coerceAtLeast(0)
    val bottomPx = bottom().roundToPx().coerceAtLeast(0)
    val child = measurable.measure(constraints.offset(vertical = -topPx - bottomPx))
    layout(child.width, constraints.constrainHeight(child.height + topPx + bottomPx)) {
        child.placeRelative(0, topPx)
    }
}
