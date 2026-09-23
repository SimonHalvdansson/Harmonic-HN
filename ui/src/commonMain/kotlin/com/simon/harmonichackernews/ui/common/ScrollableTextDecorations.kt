package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Apply before verticalScroll, to the content viewport only, never its translucent background. */
internal fun Modifier.fadingScrollEdges(state: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadeLength = HarmonicDimens.compose_comment_action_text_fade_length.toPx()
                .coerceAtMost(size.height / 2f)
            if (fadeLength <= 0f || state.viewportSize <= 0 || state.maxValue <= 0) {
                return@drawWithContent
            }
            val topStrength = (state.value / fadeLength).coerceIn(0f, 1f)
            val bottomStrength = ((state.maxValue - state.value) / fadeLength).coerceIn(0f, 1f)
            if (topStrength > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = topStrength), Color.Transparent),
                        startY = 0f,
                        endY = fadeLength,
                    ),
                    size = androidx.compose.ui.geometry.Size(size.width, fadeLength),
                    blendMode = BlendMode.DstOut,
                )
            }
            if (bottomStrength > 0f) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = bottomStrength)),
                        startY = size.height - fadeLength,
                        endY = size.height,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fadeLength),
                    size = androidx.compose.ui.geometry.Size(size.width, fadeLength),
                    blendMode = BlendMode.DstOut,
                )
            }
        }

@Composable
internal fun ScrollableTextScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val thumbColor = HarmonicTheme.colors.mutedText.copy(alpha = 0.55f)
    Canvas(modifier) { drawScrollThumb(state, thumbColor) }
}

@Composable
internal fun ScrollableTextDecorations(
    state: ScrollState,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = state.maxValue
    val viewportSize = state.viewportSize
    if (maxValue <= 0 || viewportSize <= 0) return

    val thumbColor = HarmonicTheme.colors.mutedText.copy(alpha = 0.55f)
    val density = LocalDensity.current
    Canvas(modifier = modifier) {
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

        drawScrollThumb(state, thumbColor)
    }
}

private fun DrawScope.drawScrollThumb(state: ScrollState, thumbColor: Color) {
    val maxValue = state.maxValue
    val viewportSize = state.viewportSize
    if (maxValue <= 0 || viewportSize <= 0) return
    val widthPx = 3.dp.toPx()
    val endPaddingPx = 1.dp.toPx()
    val verticalPaddingPx = 8.dp.toPx()
    val minimumHeightPx = 24.dp.toPx()
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
