package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.common.predictiveBackVisualProgress
import com.simon.harmonichackernews.ui.common.transformedForPredictiveBack
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Window-space source geometry and the transparent comment contents recorded before opening. */
data class CommentActionSourceGeometry(
    val container: Rect,
    val containerColor: Color,
    val containerCornerRadiusDp: Float,
    val containerElevationDp: Float,
    val containerBorderColor: Color = Color.Transparent,
    val containerBorderWidthDp: Float = 0f,
    val contentLayer: GraphicsLayer? = null,
)

internal enum class CommentActionTargetElement {
    User,
    Body,
    Supplementary,
}

internal data class CommentActionSharedTransitionState(
    val progress: Float,
    val active: Boolean,
    val hideTargetContent: Boolean,
    val drawOverlayShadows: Boolean,
    val source: CommentActionSourceGeometry?,
    val sourceSnapshot: ImageBitmap?,
    val targetContainer: Rect?,
    val predictiveBackProgress: Float,
    val predictiveBackEdge: Int,
    val rootBounds: Rect,
    val targetBounds: (CommentActionTargetElement) -> Rect?,
    val targetSnapshot: (CommentActionTargetElement) -> ImageBitmap?,
    val updateTargetBounds: (CommentActionTargetElement, Rect) -> Unit,
    val updateTargetLayer: (CommentActionTargetElement, GraphicsLayer) -> Unit,
)

internal val LocalCommentActionSharedTransition =
    compositionLocalOf<CommentActionSharedTransitionState?> { null }

/** Records the comment contents against transparency while leaving the live row unchanged. */
@Composable
internal fun Modifier.captureCommentActionSourceContent(
    onLayerChanged: (GraphicsLayer) -> Unit,
): Modifier {
    val layer = rememberGraphicsLayer()
    SideEffect { onLayerChanged(layer) }
    return drawWithContent snapshot@{
        layer.resetForLocalDraw()
        layer.record { this@snapshot.drawContent() }
        drawLayer(layer)
    }
}

/** Records a destination group and hides its live rendering while the root overlay owns it. */
@Composable
internal fun CommentActionTarget(
    element: CommentActionTargetElement,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = LocalCommentActionSharedTransition.current
    val layer = rememberGraphicsLayer()
    SideEffect { transition?.updateTargetLayer(element, layer) }
    val tracking = if (transition == null) {
        Modifier
    } else {
        Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            if (bounds.width > 0f && bounds.height > 0f) {
                transition.updateTargetBounds(element, bounds)
            }
        }
    }
    Box(
        modifier
            .then(tracking)
            .drawWithContent snapshot@{
                layer.resetForLocalDraw()
                layer.record { this@snapshot.drawContent() }
                if (transition?.hideTargetContent != true) drawLayer(layer)
            },
        content = content,
    )
}

/** Final static background. During a transform, the root draws the moving replacement. */
@Composable
internal fun BoxScope.CommentActionContainerBackground(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = LocalCommentActionSharedTransition.current
    val visibility = if (transition?.hideTargetContent == true) {
        Modifier.graphicsLayer(alpha = 0f)
    } else {
        Modifier
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .matchParentSize()
            .then(visibility)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(color),
    )
}

/** Draws outside the final card subtree so neither the moving card nor its shadow is clipped. */
@Composable
internal fun CommentActionTransitionOverlay(
    transition: CommentActionSharedTransitionState,
    targetColor: Color,
) {
    if (!transition.active) return
    val source = transition.source ?: return
    val rootBounds = transition.rootBounds
    if (rootBounds.width <= 0f || rootBounds.height <= 0f) return
    val rootOffset = rootBounds.topLeft
    val localSource = source.container.translate(-rootOffset.x, -rootOffset.y)
    val viewport = Rect(0f, 0f, rootBounds.width, rootBounds.height)
    val visibleSource = localSource.intersectionOrNull(viewport) ?: return
    val targetContainer = transition.targetContainer
        ?.translate(-rootOffset.x, -rootOffset.y)
        ?: visibleSource
    val progress = transition.progress.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val predictiveVisualProgress = predictiveBackVisualProgress(
        predictiveBackProgress = transition.predictiveBackProgress,
        transformProgress = progress,
    )
    val backDirection = if (transition.predictiveBackEdge == 1) -1f else 1f
    val backScale = 1f - 0.1f * predictiveVisualProgress
    val backPivotFractionX = if (backDirection > 0f) 0f else 1f
    val backTranslation = Offset(
        x = with(density) { 56.dp.toPx() } * predictiveVisualProgress * backDirection,
        y = with(density) { 18.dp.toPx() } * predictiveVisualProgress,
    )
    val baseContainer = lerp(visibleSource, targetContainer, progress)
    val container = baseContainer.transformedForPredictiveBack(
        scale = backScale,
        pivotFractionX = backPivotFractionX,
        translation = backTranslation,
    )
    val sourceRadiusPx = with(density) { source.containerCornerRadiusDp.dp.toPx() }
    val targetRadiusPx = with(density) { 28.dp.toPx() }
    val radiusPx = lerp(sourceRadiusPx, targetRadiusPx, progress) * backScale
    val shape = RoundedCornerShape(with(density) { radiusPx.toDp() })
    val elevation = if (transition.drawOverlayShadows) {
        (lerp(source.containerElevationDp, 8f, progress) * backScale).dp
    } else {
        0.dp
    }
    val sourceBorderAlpha = sourceAlpha(progress)
    val sourceBorder = if (
        source.containerBorderWidthDp > 0f &&
        source.containerBorderColor.alpha > 0f
    ) {
        Modifier.border(
            width = source.containerBorderWidthDp.dp,
            color = source.containerBorderColor.copy(
                alpha = source.containerBorderColor.alpha * sourceBorderAlpha,
            ),
            shape = shape,
        )
    } else {
        Modifier
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .absoluteOffset {
                    IntOffset(container.left.roundToInt(), container.top.roundToInt())
                }
                .requiredSize(
                    with(density) { container.width.coerceAtLeast(1f).toDp() },
                    with(density) { container.height.coerceAtLeast(1f).toDp() },
                )
                .shadow(elevation, shape, clip = false)
                .clip(shape)
                .background(lerp(source.containerColor, targetColor, progress))
                .then(sourceBorder),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawCommentActionContent(
                transition = transition,
                sourceContainer = localSource,
                visibleSource = visibleSource,
                targetContainer = targetContainer,
                baseContainer = baseContainer,
                visualContainer = container,
                predictiveBackScale = backScale,
                predictiveBackPivotFractionX = backPivotFractionX,
                predictiveBackTranslation = backTranslation,
                radiusPx = radiusPx,
                progress = progress,
            )
        }
    }
}

private fun DrawScope.drawCommentActionContent(
    transition: CommentActionSharedTransitionState,
    sourceContainer: Rect,
    visibleSource: Rect,
    targetContainer: Rect,
    baseContainer: Rect,
    visualContainer: Rect,
    predictiveBackScale: Float,
    predictiveBackPivotFractionX: Float,
    predictiveBackTranslation: Offset,
    radiusPx: Float,
    progress: Float,
) {
    val clip = roundedPath(visualContainer, radiusPx)
    // The original comment should travel with its container, not inherit the dialog's changing
    // height. Scaling this bitmap made the source text progressively taller until it occupied the
    // whole dialog. Preserve its intrinsic size, like the fixed-size source accessories in the
    // story preview transition, and crossfade to the separately measured dialog groups instead.
    val sourceDestination = moveBetweenContainers(sourceContainer, visibleSource, baseContainer)
        .transformedForPredictiveBack(
            scale = predictiveBackScale,
            pivotFractionX = predictiveBackPivotFractionX,
            translation = predictiveBackTranslation,
            pivotBounds = baseContainer,
        )
    clipPath(clip) {
        transition.sourceSnapshot?.let { snapshot ->
            drawSnapshotRegion(
                snapshot = snapshot,
                sourceBounds = sourceContainer,
                visibleSourceBounds = visibleSource,
                destination = sourceDestination,
                alpha = sourceAlpha(progress),
            )
        }

        CommentActionTargetElement.entries.forEach { element ->
            val bounds = transition.targetBounds(element)
                ?.translate(-transition.rootBounds.left, -transition.rootBounds.top)
                ?: return@forEach
            val snapshot = transition.targetSnapshot(element) ?: return@forEach
            // Destination content follows its container but retains its measured size. Scaling
            // the complete final dialog is the artifact this transition is replacing.
            val destination = moveBetweenContainers(bounds, targetContainer, baseContainer)
                .transformedForPredictiveBack(
                    scale = predictiveBackScale,
                    pivotFractionX = predictiveBackPivotFractionX,
                    translation = predictiveBackTranslation,
                    pivotBounds = baseContainer,
                )
            drawSnapshot(
                snapshot = snapshot,
                destination = destination,
                alpha = targetAlpha(element, progress),
            )
        }
    }
}

/** Draws only the pixels that were visible when the transition began. */
private fun DrawScope.drawSnapshotRegion(
    snapshot: ImageBitmap,
    sourceBounds: Rect,
    visibleSourceBounds: Rect,
    destination: Rect,
    alpha: Float,
) {
    if (
        alpha <= 0.001f || snapshot.width <= 0 || snapshot.height <= 0 ||
        sourceBounds.width <= 0f || sourceBounds.height <= 0f ||
        destination.width <= 0f || destination.height <= 0f
    ) return
    val leftFraction = ((visibleSourceBounds.left - sourceBounds.left) / sourceBounds.width)
        .coerceIn(0f, 1f)
    val topFraction = ((visibleSourceBounds.top - sourceBounds.top) / sourceBounds.height)
        .coerceIn(0f, 1f)
    val rightFraction = ((visibleSourceBounds.right - sourceBounds.left) / sourceBounds.width)
        .coerceIn(0f, 1f)
    val bottomFraction = ((visibleSourceBounds.bottom - sourceBounds.top) / sourceBounds.height)
        .coerceIn(0f, 1f)
    if (rightFraction <= leftFraction || bottomFraction <= topFraction) return

    val srcLeft = (snapshot.width * leftFraction).roundToInt().coerceIn(0, snapshot.width - 1)
    val srcTop = (snapshot.height * topFraction).roundToInt().coerceIn(0, snapshot.height - 1)
    val srcRight = (snapshot.width * rightFraction).roundToInt()
        .coerceIn(srcLeft + 1, snapshot.width)
    val srcBottom = (snapshot.height * bottomFraction).roundToInt()
        .coerceIn(srcTop + 1, snapshot.height)
    val destinationRegion = Rect(
        left = destination.left + destination.width * leftFraction,
        top = destination.top + destination.height * topFraction,
        right = destination.left + destination.width * rightFraction,
        bottom = destination.top + destination.height * bottomFraction,
    )
    clipRect(
        destinationRegion.left,
        destinationRegion.top,
        destinationRegion.right,
        destinationRegion.bottom,
    ) {
        drawImage(
            image = snapshot,
            srcOffset = IntOffset(srcLeft, srcTop),
            srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
            dstOffset = IntOffset(
                destinationRegion.left.roundToInt(),
                destinationRegion.top.roundToInt(),
            ),
            dstSize = IntSize(
                destinationRegion.width.roundToInt().coerceAtLeast(1),
                destinationRegion.height.roundToInt().coerceAtLeast(1),
            ),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

private fun DrawScope.drawSnapshot(
    snapshot: ImageBitmap,
    destination: Rect,
    alpha: Float,
) {
    if (
        alpha <= 0.001f || snapshot.width <= 0 || snapshot.height <= 0 ||
        destination.width <= 0f || destination.height <= 0f
    ) return
    clipRect(destination.left, destination.top, destination.right, destination.bottom) {
        drawImage(
            image = snapshot,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(snapshot.width, snapshot.height),
            dstOffset = IntOffset(destination.left.roundToInt(), destination.top.roundToInt()),
            dstSize = IntSize(
                destination.width.roundToInt().coerceAtLeast(1),
                destination.height.roundToInt().coerceAtLeast(1),
            ),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

private fun GraphicsLayer.resetForLocalDraw() {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    pivotOffset = Offset.Zero
}

private fun moveBetweenContainers(rect: Rect, from: Rect, to: Rect): Rect {
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

private fun roundedPath(rect: Rect, radius: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = rect,
            topLeft = CornerRadius(radius),
            topRight = CornerRadius(radius),
            bottomRight = CornerRadius(radius),
            bottomLeft = CornerRadius(radius),
        ),
    )
}

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val intersection = Rect(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )
    return intersection.takeIf { it.width > 0f && it.height > 0f }
}

private fun targetAlpha(element: CommentActionTargetElement, progress: Float): Float = when (element) {
    CommentActionTargetElement.User -> ((progress - 0.12f) / 0.58f).coerceIn(0f, 1f)
    CommentActionTargetElement.Body -> ((progress - 0.08f) / 0.62f).coerceIn(0f, 1f)
    CommentActionTargetElement.Supplementary -> ((progress - 0.42f) / 0.43f).coerceIn(0f, 1f)
}

private fun sourceAlpha(progress: Float): Float = (1f - progress / 0.68f).coerceIn(0f, 1f)

private fun lerp(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = lerp(start.left, end.left, progress),
    top = lerp(start.top, end.top, progress),
    right = lerp(start.right, end.right, progress),
    bottom = lerp(start.bottom, end.bottom, progress),
)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
