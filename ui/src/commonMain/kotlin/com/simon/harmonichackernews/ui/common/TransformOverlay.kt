package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared container-transform shell for modal previews and action cards.
 *
 * The container and its destination content deliberately use separate transforms. A tiny inline
 * link can therefore expand into a card without squeezing the completed card's image, text, and
 * buttons into the source bounds. Destination content keeps its measured size, travels with the
 * container, and is progressively revealed by the moving shape. Image-only previews can opt back
 * into content scaling because the image itself is the shared element.
 */
@Composable
fun TransformOverlay(
    contentKey: Any,
    sourceBounds: Rect?,
    dismissRequestVersion: Int,
    predictiveBackProgress: Float,
    predictiveBackEdge: Int,
    maxWidth: Dp,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    targetCornerRadius: Dp,
    sourceCornerRadius: Dp = 0.dp,
    containerColor: Color,
    sourceContainerColor: Color = Color.Transparent,
    sourceBorderColor: Color = Color.Transparent,
    sourceBorderWidth: Dp = 0.dp,
    sourceAnchorSize: Dp? = null,
    shadowElevation: Dp = 8.dp,
    scaleContentWithContainer: Boolean = false,
    preserveContentAspectRatio: Boolean = false,
    keepContentOpaqueWithSource: Boolean = false,
    consumeAllGestures: Boolean = true,
    sourceContentLayer: GraphicsLayer? = null,
    onSourceReadyToCover: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onDismissAnimationFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val transformProgress = remember(contentKey) { Animatable(0f) }
    var rootBounds by remember(contentKey) { mutableStateOf(Rect.Zero) }
    var targetBounds by remember(contentKey) { mutableStateOf<Rect?>(null) }
    var dismissalFinished by remember(contentKey) { mutableStateOf(false) }
    var sourceHandoffComplete by remember(contentKey) { mutableStateOf(false) }
    var sourceSnapshot by remember(contentKey, sourceContentLayer) {
        mutableStateOf<ImageBitmap?>(null)
    }
    val sourceSnapshotRequired = sourceContentLayer != null
    val sourceSnapshotReady = !sourceSnapshotRequired || sourceSnapshot != null
    val targetReady = targetBounds != null && rootBounds.width > 0f && rootBounds.height > 0f &&
        sourceSnapshotReady

    LaunchedEffect(contentKey, sourceContentLayer) {
        val layer = sourceContentLayer ?: return@LaunchedEffect
        withFrameNanos { }
        if (!layer.isReleased && layer.size.width > 0 && layer.size.height > 0) {
            sourceSnapshot = layer.toImageBitmap()
        }
    }

    // A single effect owns both directions. Changing the dismiss request cancels an in-flight
    // opening animation and reverses from its current value without a blank handoff frame.
    LaunchedEffect(contentKey, targetReady, dismissRequestVersion) {
        if (!targetReady) return@LaunchedEffect
        if (!sourceHandoffComplete && onSourceReadyToCover != null) {
            sourceHandoffComplete = true
            // Give the captured source a complete draw frame before suppressing the live row.
            // Some asynchronously painted children (notably favicons) otherwise leave a single
            // empty frame between the live source and its moving snapshot.
            withFrameNanos { }
            withFrameNanos { }
            onSourceReadyToCover()
            withFrameNanos { }
        }
        if (dismissRequestVersion > 0) {
            transformProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            )
            if (!dismissalFinished) {
                dismissalFinished = true
                onDismissAnimationFinished()
            }
        } else {
            transformProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(280, easing = FastOutSlowInEasing),
            )
        }
    }

    val progress = transformProgress.value.coerceIn(0f, 1f)
    // A committed predictive gesture is still at full strength when the close morph starts, then
    // must unwind to identity with that morph so its source bounds are restored exactly.
    val predictiveVisualProgress = predictiveBackVisualProgress(
        predictiveBackProgress = predictiveBackProgress,
        transformProgress = progress,
    )
    val backDirection = if (predictiveBackEdge == 1) -1f else 1f
    val backTranslationX =
        with(density) { 56.dp.toPx() } * predictiveVisualProgress * backDirection
    val backTranslationY = with(density) { 18.dp.toPx() } * predictiveVisualProgress
    val backScale = 1f - 0.1f * predictiveVisualProgress
    val rootOffset = rootBounds.topLeft
    val localTarget = targetBounds?.translate(-rootOffset.x, -rootOffset.y)
    val localViewport = Rect(0f, 0f, rootBounds.width, rootBounds.height)
    val localSource = sourceBounds
        ?.translate(-rootOffset.x, -rootOffset.y)
        ?.intersectionOrNull(localViewport)
    val anchorSizePx = sourceAnchorSize?.let { with(density) { it.toPx() } }
    val transitionSource = when {
        localSource == null -> localTarget?.scaledAboutCenter(0.96f)
        anchorSizePx != null -> Rect(
            left = localSource.center.x - anchorSizePx / 2f,
            top = localSource.center.y - anchorSizePx / 2f,
            right = localSource.center.x + anchorSizePx / 2f,
            bottom = localSource.center.y + anchorSizePx / 2f,
        )
        else -> localSource
    }
    val container = if (transitionSource != null && localTarget != null) {
        lerp(transitionSource, localTarget, progress)
    } else {
        localTarget
    }
    val visualContainer = container?.transformedForPredictiveBack(
        scale = backScale,
        pivotFractionX = if (backDirection > 0f) 0f else 1f,
        translation = Offset(backTranslationX, backTranslationY),
    )
    val sourceRadiusPx = with(density) { sourceCornerRadius.toPx() }
    val targetRadiusPx = with(density) { targetCornerRadius.toPx() }
    val containerRadiusPx = sourceRadiusPx + (targetRadiusPx - sourceRadiusPx) * progress
    val visualRadiusPx = containerRadiusPx * backScale
    val movingShape = RoundedCornerShape(with(density) { visualRadiusPx.toDp() })
    val inlineSource = sourceAnchorSize != null
    val containerRevealProgress = if (inlineSource) {
        ((progress - 0.16f) / 0.84f).coerceIn(0f, 1f)
    } else {
        progress
    }
    val movingColor = lerp(sourceContainerColor, containerColor, containerRevealProgress)
    val movingBorderColor = sourceBorderColor.copy(
        alpha = sourceBorderColor.alpha * (1f - progress),
    )
    val movingBorderWidth = sourceBorderWidth * (1f - progress)
    val inlineContainerAlpha = if (inlineSource) {
        ((progress - 0.18f) / 0.42f).coerceIn(0f, 1f)
    } else {
        1f
    }
    val movingAlpha = when {
        sourceBounds == null -> progress
        inlineSource -> inlineContainerAlpha
        else -> 1f
    }
    val shadowProgress = if (inlineSource) {
        ((progress - 0.32f) / 0.68f).coerceIn(0f, 1f)
    } else {
        progress
    }
    val movingElevation = (shadowElevation.value * shadowProgress * backScale).dp
    val gestureBlocker = if (consumeAllGestures) Modifier.consumeModalGestures() else Modifier
    val contentMorphScaleX = if (
        scaleContentWithContainer && localTarget != null && localTarget.width > 0f &&
        container != null
    ) {
        container.width / localTarget.width
    } else {
        1f
    }
    val contentMorphScaleY = if (
        scaleContentWithContainer && localTarget != null && localTarget.height > 0f &&
        container != null
    ) {
        container.height / localTarget.height
    } else {
        1f
    }
    val aspectRatioCorrection = aspectPreservingCropCorrection(
        scaleX = contentMorphScaleX,
        scaleY = contentMorphScaleY,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootBounds = it.boundsInWindow() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.32f * progress * (1f - 0.55f * predictiveVisualProgress),
                    ),
                )
                .then(gestureBlocker)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )

        if (visualContainer != null && visualContainer.width > 0f && visualContainer.height > 0f) {
            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            visualContainer.left.roundToInt(),
                            visualContainer.top.roundToInt(),
                        )
                    }
                    .requiredSize(
                        with(density) { visualContainer.width.coerceAtLeast(1f).toDp() },
                        with(density) { visualContainer.height.coerceAtLeast(1f).toDp() },
                    )
                    .graphicsLayer(alpha = movingAlpha)
                    .shadow(movingElevation, movingShape, clip = false)
                    .clip(movingShape)
                    .background(movingColor)
                    .then(
                        if (movingBorderWidth > 0.dp) {
                            Modifier.border(movingBorderWidth, movingBorderColor, movingShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val bounds = visualContainer
                    if (bounds == null || bounds.width <= 0f || bounds.height <= 0f) {
                        return@drawWithContent
                    }
                    clipPath(roundedPath(bounds, visualRadiusPx)) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .fillMaxWidth()
                        .onGloballyPositioned { targetBounds = it.boundsInWindow() }
                        .graphicsLayer {
                            val target = localTarget
                            val current = container
                            scaleX = contentMorphScaleX * backScale
                            scaleY = contentMorphScaleY * backScale
                            translationX = if (target != null && current != null) {
                                val sharedTranslation = if (scaleContentWithContainer) {
                                    current.center.x - target.center.x
                                } else {
                                    current.left - target.left
                                }
                                sharedTranslation + backTranslationX
                            } else {
                                backTranslationX
                            }
                            translationY = if (target != null && current != null) {
                                val sharedTranslation = if (scaleContentWithContainer) {
                                    current.center.y - target.center.y
                                } else {
                                    current.top - target.top
                                }
                                sharedTranslation + backTranslationY
                            } else {
                                backTranslationY
                            }
                            alpha = when {
                                sourceBounds == null -> progress
                                preserveContentAspectRatio -> 1f
                                sourceSnapshotRequired ->
                                    ((progress - 0.12f) / 0.58f).coerceIn(0f, 1f)
                                inlineSource ->
                                    ((progress - 0.28f) / 0.54f).coerceIn(0f, 1f)
                                keepContentOpaqueWithSource -> 1f
                                else -> ((progress - 0.06f) / 0.64f).coerceIn(0f, 1f)
                            }
                            transformOrigin = if (predictiveVisualProgress > 0f) {
                                TransformOrigin(
                                    pivotFractionX = if (backDirection > 0f) 0f else 1f,
                                    pivotFractionY = 0.5f,
                                )
                            } else {
                                TransformOrigin.Center
                            }
                        }
                        .then(gestureBlocker)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    if (preserveContentAspectRatio) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = aspectRatioCorrection.scaleX
                                scaleY = aspectRatioCorrection.scaleY
                            },
                        ) {
                            content()
                        }
                    } else {
                        content()
                    }
                }
            }
        }

        sourceSnapshot?.let { snapshot ->
            val snapshotAlpha = ((0.62f - progress) / 0.62f).coerceIn(0f, 1f)
            if (snapshotAlpha > 0f && visualContainer != null) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    clipPath(roundedPath(visualContainer, visualRadiusPx)) {
                        if (preserveContentAspectRatio) {
                            val snapshotScale = max(
                                visualContainer.width / snapshot.width,
                                visualContainer.height / snapshot.height,
                            )
                            val destinationWidth = snapshot.width * snapshotScale
                            val destinationHeight = snapshot.height * snapshotScale
                            drawImage(
                                image = snapshot,
                                dstOffset = IntOffset(
                                    x = (visualContainer.center.x - destinationWidth / 2f)
                                        .roundToInt(),
                                    y = (visualContainer.center.y - destinationHeight / 2f)
                                        .roundToInt(),
                                ),
                                dstSize = IntSize(
                                    width = destinationWidth.roundToInt().coerceAtLeast(1),
                                    height = destinationHeight.roundToInt().coerceAtLeast(1),
                                ),
                                alpha = snapshotAlpha,
                            )
                        } else {
                            drawImage(
                                image = snapshot,
                                topLeft = visualContainer.topLeft,
                                alpha = snapshotAlpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Records a source composable without changing its live rendering. */
@Composable
internal fun Modifier.captureSharedTransformSourceContent(
    onLayerChanged: (GraphicsLayer) -> Unit,
): Modifier {
    val layer = rememberGraphicsLayer()
    SideEffect { onLayerChanged(layer) }
    return drawWithContent snapshot@{
        layer.record { this@snapshot.drawContent() }
        drawLayer(layer)
    }
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

internal fun Rect.transformedForPredictiveBack(
    scale: Float,
    pivotFractionX: Float,
    translation: Offset,
    pivotBounds: Rect = this,
): Rect {
    val pivot = Offset(
        x = pivotBounds.left + pivotBounds.width * pivotFractionX,
        y = pivotBounds.center.y,
    )
    return Rect(
        left = pivot.x + (left - pivot.x) * scale + translation.x,
        top = pivot.y + (top - pivot.y) * scale + translation.y,
        right = pivot.x + (right - pivot.x) * scale + translation.x,
        bottom = pivot.y + (bottom - pivot.y) * scale + translation.y,
    )
}

internal fun predictiveBackVisualProgress(
    predictiveBackProgress: Float,
    transformProgress: Float = 1f,
): Float {
    val normalized = predictiveBackProgress.coerceIn(0f, 1f)
    val eased = 1f - (1f - normalized) * (1f - normalized)
    return eased * transformProgress.coerceIn(0f, 1f)
}

/**
 * Target bounds must describe the fully displayed dialog. Bounds read below a predictive-back
 * graphics layer already contain its scale and translation; accepting them during the gesture
 * makes a dismissal overlay apply that transform for a second time.
 */
internal fun shouldUpdateRestingTargetGeometry(
    predictiveBackProgress: Float,
    dismissRequestVersion: Int,
): Boolean = predictiveBackProgress <= 0f && dismissRequestVersion == 0

internal data class ContentAspectRatioCorrection(
    val scaleX: Float,
    val scaleY: Float,
)

/** Counteracts a non-uniform container transform so its content is center-cropped, not stretched. */
internal fun aspectPreservingCropCorrection(
    scaleX: Float,
    scaleY: Float,
): ContentAspectRatioCorrection {
    if (scaleX <= 0f || scaleY <= 0f) return ContentAspectRatioCorrection(1f, 1f)
    val uniformScale = max(scaleX, scaleY)
    return ContentAspectRatioCorrection(
        scaleX = uniformScale / scaleX,
        scaleY = uniformScale / scaleY,
    )
}

private fun Rect.scaledAboutCenter(scale: Float): Rect = Rect(
    left = center.x - width * scale / 2f,
    top = center.y - height * scale / 2f,
    right = center.x + width * scale / 2f,
    bottom = center.y + height * scale / 2f,
)

private fun Rect.intersectionOrNull(other: Rect): Rect? {
    val intersection = Rect(
        left = max(left, other.left),
        top = max(top, other.top),
        right = min(right, other.right),
        bottom = min(bottom, other.bottom),
    )
    return intersection.takeIf { it.width > 0f && it.height > 0f }
}

private fun lerp(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = start.left + (end.left - start.left) * progress,
    top = start.top + (end.top - start.top) * progress,
    right = start.right + (end.right - start.right) * progress,
    bottom = start.bottom + (end.bottom - start.bottom) * progress,
)

private fun Modifier.consumeModalGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
