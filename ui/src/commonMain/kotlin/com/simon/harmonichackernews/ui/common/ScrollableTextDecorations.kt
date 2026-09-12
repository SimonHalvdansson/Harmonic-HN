package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

@Composable
internal fun ScrollableTextDecorations(
    state: ScrollState,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = state.maxValue
    val viewportSize = state.viewportSize
    if (maxValue <= 0 || viewportSize <= 0) return

    val thumbColor = HarmonicTheme.colors.storyDisabled.copy(alpha = 0.55f)
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
        val widthPx = with(density) { 3.dp.toPx() }
        val endPaddingPx = with(density) { 1.dp.toPx() }
        val verticalPaddingPx = with(density) { 8.dp.toPx() }
        val minimumHeightPx = with(density) { 24.dp.toPx() }
        val fadeLengthPx = with(density) {
            HarmonicDimens.compose_comment_action_text_fade_length.toPx()
        }.coerceAtMost(size.height / 2f)
        val topFadeStrength = (state.value / fadeLengthPx).coerceIn(0f, 1f)
        val bottomFadeStrength = ((maxValue - state.value) / fadeLengthPx).coerceIn(0f, 1f)

        if (topFadeStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        containerColor.copy(alpha = containerColor.alpha * topFadeStrength),
                        containerColor.copy(alpha = 0f),
                    ),
                    startY = 0f,
                    endY = fadeLengthPx,
                ),
                size = androidx.compose.ui.geometry.Size(size.width, fadeLengthPx),
            )
        }
        if (bottomFadeStrength > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        containerColor.copy(alpha = 0f),
                        containerColor.copy(alpha = containerColor.alpha * bottomFadeStrength),
                    ),
                    startY = size.height - fadeLengthPx,
                    endY = size.height,
                ),
                topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fadeLengthPx),
                size = androidx.compose.ui.geometry.Size(size.width, fadeLengthPx),
            )
        }

        val trackHeight = (size.height - verticalPaddingPx * 2f).coerceAtLeast(0f)
        val contentHeight = viewportSize + maxValue
        val visibleFraction = viewportSize.toFloat() / contentHeight
        val thumbHeight = (trackHeight * visibleFraction)
            .coerceIn(minimumHeightPx.coerceAtMost(trackHeight), trackHeight)
        val scrollFraction = state.value.toFloat() / maxValue
        val top = verticalPaddingPx + (trackHeight - thumbHeight) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = androidx.compose.ui.geometry.Offset(
                size.width - widthPx - endPaddingPx,
                top,
            ),
            size = androidx.compose.ui.geometry.Size(widthPx, thumbHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(widthPx / 2f),
        )
    }
}

