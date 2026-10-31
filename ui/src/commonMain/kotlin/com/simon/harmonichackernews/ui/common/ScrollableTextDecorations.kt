package com.simon.harmonichackernews.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.HarmonicDimens

/** Apply before verticalScroll, to the content viewport only, never its translucent background. */
internal fun Modifier.fadingScrollEdges(state: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadeLength = HarmonicDimens.compose_comment_action_text_fade_length.toPx()
                .coerceAtMost(size.height / 2f)
            val topBrush = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = fadeLength.coerceAtLeast(1f))
            val bottomBrush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black),
                startY = size.height - fadeLength, endY = size.height)
            onDrawWithContent {
                drawContent()
                if (fadeLength > 0f && state.viewportSize > 0 && state.maxValue > 0) {
                    val topStrength = (state.value / fadeLength).coerceIn(0f, 1f)
                    val bottomStrength = ((state.maxValue - state.value) / fadeLength).coerceIn(0f, 1f)
                    if (topStrength > 0f) drawRect(topBrush, size = Size(size.width, fadeLength),
                        alpha = topStrength, blendMode = BlendMode.DstOut)
                    if (bottomStrength > 0f) drawRect(bottomBrush,
                        topLeft = Offset(0f, size.height - fadeLength), size = Size(size.width, fadeLength),
                        alpha = bottomStrength, blendMode = BlendMode.DstOut)
                }
            }
        }

@Composable
internal fun ScrollableTextScrollbar(state: ScrollState, modifier: Modifier = Modifier) {
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Canvas(modifier) { drawScrollThumb(state, thumbColor) }
}

@Composable
internal fun ScrollableTextDecorations(
    state: ScrollState,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Box(modifier.drawWithCache {
        val fadeLength = HarmonicDimens.compose_comment_action_text_fade_length.toPx()
            .coerceAtMost(size.height / 2f)
        val opaque = containerColor.copy(alpha = 1f)
        val transparent = containerColor.copy(alpha = 0f)
        val falling = Brush.verticalGradient(listOf(opaque, transparent), endY = 1f)
        val rising = Brush.verticalGradient(listOf(transparent, opaque), endY = 1f)
        val strip = Size(size.width, 1f)
        onDrawBehind {
            val maxValue = state.maxValue
            if (maxValue > 0 && state.viewportSize > 0 && fadeLength > 0f) {
                val top = state.value.toFloat().coerceAtMost(fadeLength)
                val bottom = (maxValue - state.value).toFloat().coerceAtMost(fadeLength)
                // Retain the original opaque lip (at least one pixel or 12% of the fade).
                fun lip(length: Float) = length * (1f / length).coerceIn(0.12f, 1f)
                if (top > 0f) {
                    val solid = lip(top)
                    drawRect(opaque, size = Size(size.width, solid))
                    if (top > solid) withTransform({
                        translate(top = solid)
                        scale(1f, top - solid, Offset.Zero)
                    }) { drawRect(falling, size = strip) }
                }
                if (bottom > 0f) {
                    val solid = lip(bottom)
                    if (bottom > solid) withTransform({
                        translate(top = size.height - bottom)
                        scale(1f, bottom - solid, Offset.Zero)
                    }) { drawRect(rising, size = strip) }
                    drawRect(opaque, topLeft = Offset(0f, size.height - solid), size = Size(size.width, solid))
                }
                drawScrollThumb(state, thumbColor)
            }
        }
    })
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
