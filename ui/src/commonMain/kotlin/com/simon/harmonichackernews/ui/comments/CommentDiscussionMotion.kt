package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.navigation.activityNavigationEasing

// Material emphasized easing. One timeline drives bounds, uniform content scale and fade-through.
internal val CommentDiscussionEasing = activityNavigationEasing()

/** Material container transform with width-fit content, rather than a stationary reveal. */
@Composable
internal fun CommentDiscussionContainer(
    origin: Rect,
    progress: Float,
    color: Color,
    source: GraphicsLayer,
    content: @Composable () -> Unit,
) {
    if (progress <= 0f) return
    BoxWithConstraints(Modifier.fillMaxSize().consumeAllPointerGestures()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val p = progress.coerceIn(0f, 1f)
        val bounds = Rect(
            origin.left * (1f - p), origin.top * (1f - p),
            origin.right + (width - origin.right) * p,
            origin.bottom + (height - origin.bottom) * p,
        )
        val radius = with(density) { 28.dp.toPx() } * (1f - p)
        val mask = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Generic(Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(radius))) })
        }
        // One material surface owns the entire morph, including the final shadow and corners.
        // The recorded source contains only foreground content, never a second background.
        Box(Modifier.absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .requiredSize(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .shadow((8f * (1f - p)).dp, RoundedCornerShape(with(density) { radius.toDp() }), clip = false)
            .sharedHazeDialogBackground(color, RoundedCornerShape(with(density) { radius.toDp() }),
                revealProgress = 1f - p))
        Box(Modifier.fillMaxSize().clip(mask)) {
            val sourceAlpha = (1f - p / 0.15f).coerceIn(0f, 1f)
            if (sourceAlpha > 0f && !source.isReleased && source.size.width > 0) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = sourceAlpha }) {
                    withTransform({
                        translate(bounds.left, bounds.top)
                        val contentScale = bounds.width / source.size.width
                        scale(contentScale, contentScale, Offset.Zero)
                    }) { drawLayer(source) }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val scale = bounds.width / width.coerceAtLeast(1f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = bounds.left
                translationY = bounds.top
                alpha = ((p - 0.15f) / 0.30f).coerceIn(0f, 1f)
            }) { content() }
        }
    }
}
