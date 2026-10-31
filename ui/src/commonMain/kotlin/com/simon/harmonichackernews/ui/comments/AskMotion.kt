package com.simon.harmonichackernews.ui.comments

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.simon.harmonichackernews.ui.common.rememberScreenCorners
import com.simon.harmonichackernews.ui.common.forViewport
import com.simon.harmonichackernews.ui.common.interpolateFrom
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.navigation.activityNavigationEasing

// Material emphasized easing. One timeline drives bounds, uniform content scale and fade-through.
internal val AskEasing = activityNavigationEasing()

@Composable
internal fun rememberAskProgress(open: Boolean, backProgress: Float): State<Float> = animateFloatAsState(
    targetValue = if (open) 1f - backProgress.coerceIn(0f, 1f) * 0.35f else 0f,
    animationSpec = tween(
        durationMillis = if (backProgress > 0f) 0 else if (open) 500 else 350,
        easing = AskEasing,
    ),
    label = "Ask container",
)

/** Material container transform; the conversation never observes individual animation frames. */
@Composable
internal fun AskContainer(
    origin: Rect,
    progress: () -> Float,
    color: Color,
    source: GraphicsLayer,
    sourceCornerRadius: Dp = 28.dp,
    summarySource: Boolean = false,
    content: @Composable () -> Unit,
) {
    val visible by remember(progress, summarySource) {
        computedStateOf { summarySource || progress() > 0f }
    }
    if (!visible) return
    val screenCorners = rememberScreenCorners()
    val windowSize = LocalWindowInfo.current.containerSize
    var viewport by remember { mutableStateOf<Rect?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewport = it.boundsInWindow() }) {
        Box(Modifier.fillMaxSize().consumeAllPointerGestures())
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val restingCorners = screenCorners.forViewport(viewport, windowSize)
        val bounds = { askContainerBounds(origin, width, height, progress()) }
        AskMaterialSurface(origin, width, height, restingCorners, sourceCornerRadius,
            progress, color, summarySource)
        Box(Modifier.fillMaxSize().graphicsLayer {
            val rect = bounds()
            val corners = restingCorners.interpolateFrom(sourceCornerRadius.value, progress())
            fun px(radius: Float) = with(density) { radius.dp.toPx() }
            shape = object : Shape {
                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                    Outline.Generic(Path().apply { addRoundRect(RoundRect(
                        rect = rect,
                        topLeft = CornerRadius(px(corners.topLeft)), topRight = CornerRadius(px(corners.topRight)),
                        bottomRight = CornerRadius(px(corners.bottomRight)), bottomLeft = CornerRadius(px(corners.bottomLeft)),
                    )) })
            }
            clip = true
        }) {
            Canvas(Modifier.fillMaxSize().graphicsLayer {
                alpha = (1f - progress() / 0.15f).coerceIn(0f, 1f)
            }) {
                if (!source.isReleased && source.size.width > 0 && progress() < 0.15f) {
                    val rect = bounds()
                    withTransform({
                        translate(rect.left, rect.top)
                        val contentScale = rect.width / source.size.width
                        scale(contentScale, contentScale, Offset.Zero)
                    }) { drawLayer(source) }
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                val rect = bounds()
                val scale = rect.width / width.coerceAtLeast(1f)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = rect.left
                translationY = rect.top
                alpha = ((progress() - 0.15f) / 0.85f).coerceIn(0f, 1f)
            }) { content() }
        }
    }
}

internal fun askContainerBounds(origin: Rect, width: Float, height: Float, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(origin.left * (1f - p), origin.top * (1f - p),
        origin.right + (width - origin.right) * p, origin.bottom + (height - origin.bottom) * p)
}

/** Haze records immutable styles. Isolate their frame updates from the retained conversation. */
@Composable
private fun AskMaterialSurface(
    origin: Rect,
    width: Float,
    height: Float,
    restingCorners: com.simon.harmonichackernews.ui.common.ScreenCorners,
    sourceCornerRadius: Dp,
    progress: () -> Float,
    color: Color,
    summarySource: Boolean,
) {
    val p = progress().coerceIn(0f, 1f)
    val corners = restingCorners.interpolateFrom(sourceCornerRadius.value, p)
    val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val shape = RoundedCornerShape(
        topStart = (if (ltr) corners.topLeft else corners.topRight).dp,
        topEnd = (if (ltr) corners.topRight else corners.topLeft).dp,
        bottomEnd = (if (ltr) corners.bottomRight else corners.bottomLeft).dp,
        bottomStart = (if (ltr) corners.bottomLeft else corners.bottomRight).dp,
    )
    val surface = if (summarySource) {
        Modifier.background(color, shape).border(1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = MaterialTheme.colorScheme.outlineVariant.alpha * (1f - p)), shape)
    } else {
        Modifier.shadow((8f * (1f - p)).dp, shape, clip = false)
            .sharedHazeDialogBackground(color, shape, revealProgress = 1f - p)
    }
    Box(Modifier.absoluteOffset {
        val bounds = askContainerBounds(origin, width, height, progress())
        IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
    }.layout { measurable, _ ->
        val bounds = askContainerBounds(origin, width, height, progress())
        val child = measurable.measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(0),
            bounds.height.roundToInt().coerceAtLeast(0)))
        layout(child.width, child.height) { child.place(0, 0) }
    }.then(surface))
}
