package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Window-space geometry and transparent element layers captured before a preview opens. */
data class StoryPreviewSourceGeometry(
    val container: Rect,
    val containerElevationDp: Float = 0f,
    val image: Rect? = null,
    val title: Rect? = null,
    val summary: Rect? = null,
    val meta: Rect? = null,
    val index: Rect? = null,
    val comments: Rect? = null,
    val imageCornerRadiusPx: Float = 0f,
    val imageLayer: GraphicsLayer? = null,
    val titleLayer: GraphicsLayer? = null,
    val summaryLayer: GraphicsLayer? = null,
    val metaLayer: GraphicsLayer? = null,
    val indexLayer: GraphicsLayer? = null,
    val commentsLayer: GraphicsLayer? = null,
)

internal enum class StoryPreviewSourceElement {
    Image,
    Title,
    Summary,
    Meta,
    Index,
    Comments,
}

internal enum class StoryPreviewSharedElement {
    Image,
    Title,
    Summary,
    Meta,
    Supplementary,
}

internal data class StoryPreviewSharedTransitionState(
    val progress: Float,
    val active: Boolean,
    val hideTargetContent: Boolean,
    val drawOverlayShadows: Boolean,
    val source: StoryPreviewSourceGeometry?,
    val sourceSnapshot: (StoryPreviewSourceElement) -> ImageBitmap?,
    val targetContainer: Rect?,
    val targetCommentsButton: Rect?,
    val rootOffset: Offset,
    val targetBounds: (StoryPreviewSharedElement) -> Rect?,
    val targetSnapshot: (StoryPreviewSharedElement) -> ImageBitmap?,
    val updateTargetBounds: (StoryPreviewSharedElement, Rect) -> Unit,
    val updateTargetLayer: (StoryPreviewSharedElement, GraphicsLayer) -> Unit,
    val updateCommentsButtonBounds: (Rect) -> Unit,
)

internal val LocalStoryPreviewSharedTransition =
    compositionLocalOf<StoryPreviewSharedTransitionState?> { null }

/** Tracks the elevated Comments surface separately because its shadow is not in the bitmap layer. */
@Composable
internal fun Modifier.trackStoryPreviewCommentsButton(): Modifier {
    val transition = LocalStoryPreviewSharedTransition.current ?: return this
    return onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        if (bounds.width > 0f && bounds.height > 0f) {
            transition.updateCommentsButtonBounds(bounds)
        }
    }
}

/** The final static background. The moving background is rendered at overlay scope. */
@Composable
internal fun StoryPreviewContainerBackground(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = LocalStoryPreviewSharedTransition.current
    val visibility = if (transition?.hideTargetContent == true) {
        Modifier.graphicsLayer(alpha = 0f)
    } else {
        Modifier
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .then(visibility)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(color),
    )
}

/** Records one source element against transparency while continuing to draw it in the list. */
@Composable
internal fun Modifier.captureStoryPreviewSourceContent(
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

/** Tracks stable destination bounds; the actual transition drawing happens in the root overlay. */
@Composable
internal fun StoryPreviewSharedElement(
    element: StoryPreviewSharedElement,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val transition = LocalStoryPreviewSharedTransition.current
    val layer = rememberGraphicsLayer()
    SideEffect {
        transition?.updateTargetLayer(element, layer)
    }
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
    val finalClip = if (element == StoryPreviewSharedElement.Image) {
        Modifier.clip(
            RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .then(tracking)
            .then(finalClip)
            .drawWithContent snapshot@{
                layer.resetForLocalDraw()
                layer.record { this@snapshot.drawContent() }
                if (transition?.hideTargetContent != true) {
                    drawLayer(layer)
                }
            },
        content = content,
    )
}

/**
 * Draws above the pager and its scroll containers so moving content and shadows cannot be clipped
 * to the destination dialog's final bounds.
 */
@Composable
internal fun StoryPreviewTransitionOverlay(
    transition: StoryPreviewSharedTransitionState,
    color: Color,
) {
    if (!transition.active) return
    val source = transition.source ?: return
    val rootOffset = transition.rootOffset
    val localSource = source.localTo(rootOffset)
    val sourceContainer = localSource.container
    val targetContainer = transition.targetContainer?.localTo(rootOffset) ?: sourceContainer
    val progress = transition.progress.coerceIn(0f, 1f)
    val container = lerp(sourceContainer, targetContainer, progress)
    val density = LocalDensity.current
    val sourceRadiusPx = with(density) { 8.dp.toPx() }
    val targetRadiusPx = with(density) { 28.dp.toPx() }
    val containerRadiusPx = lerp(sourceRadiusPx, targetRadiusPx, progress)
    val containerRadius = with(density) { containerRadiusPx.toDp() }
    val elevation = if (transition.drawOverlayShadows) {
        lerp(source.containerElevationDp, 8f, progress).dp
    } else {
        0.dp
    }
    val width = with(density) { container.width.coerceAtLeast(1f).toDp() }
    val height = with(density) { container.height.coerceAtLeast(1f).toDp() }
    val shape = RoundedCornerShape(containerRadius)
    val targetCommentsButton = transition.targetCommentsButton?.localTo(rootOffset)
    val commentsButton = targetCommentsButton?.let { bounds ->
        moveBetweenContainers(bounds, targetContainer, container)
    }
    val commentsShadowProgress = supplementaryAlpha(progress)
    val commentsButtonShape = RoundedCornerShape(percent = 50)
    val commentsButtonColor = MaterialTheme.colorScheme.surfaceContainerLow

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .absoluteOffset {
                    IntOffset(container.left.roundToInt(), container.top.roundToInt())
                }
                .requiredSize(width, height)
                .shadow(elevation, shape, clip = false)
                .clip(shape)
                .background(color),
        )
        if (
            transition.drawOverlayShadows &&
            commentsButton != null
        ) {
            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            commentsButton.left.roundToInt(),
                            commentsButton.top.roundToInt(),
                        )
                    }
                    .requiredSize(
                        with(density) { commentsButton.width.coerceAtLeast(1f).toDp() },
                        with(density) { commentsButton.height.coerceAtLeast(1f).toDp() },
                    )
                    // Fade a fixed resting shadow instead of animating sub-dp elevation. An empty
                    // shadow caster shows its dark interior through the partially faded bitmap;
                    // covering it with the real button color keeps the pill chroma stable.
                    .graphicsLayer(alpha = commentsShadowProgress)
                    .shadow(
                        elevation = 1.dp,
                        shape = commentsButtonShape,
                        clip = false,
                    )
                    .background(commentsButtonColor, commentsButtonShape),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawTransitionContent(
                transition = transition,
                source = localSource,
                sourceContainer = sourceContainer,
                targetContainer = targetContainer,
                container = container,
                containerRadiusPx = containerRadiusPx,
                targetImageRadiusPx = targetRadiusPx,
                progress = progress,
            )
        }
    }
}

private fun DrawScope.drawTransitionContent(
    transition: StoryPreviewSharedTransitionState,
    source: StoryPreviewSourceGeometry,
    sourceContainer: Rect,
    targetContainer: Rect,
    container: Rect,
    containerRadiusPx: Float,
    targetImageRadiusPx: Float,
    progress: Float,
) {
    val containerClip = roundedPath(container, containerRadiusPx, containerRadiusPx)

    fun drawElement(
        element: StoryPreviewSharedElement,
        sourceBounds: Rect?,
        sourceSnapshot: ImageBitmap?,
        image: Boolean = false,
    ) {
        val targetBounds = transition.targetBounds(element)?.localTo(transition.rootOffset)
        val targetSnapshot = transition.targetSnapshot(element)
        val destination = when {
            sourceBounds != null && targetBounds != null -> lerp(sourceBounds, targetBounds, progress)
            targetBounds != null -> mapBetweenContainers(targetBounds, targetContainer, container)
            sourceBounds != null -> mapBetweenContainers(sourceBounds, sourceContainer, container)
            else -> return
        }
        val imageClip = if (image) {
            val sourceImageRadius = source.imageCornerRadiusPx
            roundedPath(
                destination,
                lerp(sourceImageRadius, targetImageRadiusPx, progress),
                lerp(sourceImageRadius, 0f, progress),
            )
        } else {
            null
        }
        val targetAlpha = if (sourceBounds == null) {
            when (element) {
                StoryPreviewSharedElement.Summary -> supplementaryAlpha(progress)
                else -> progress
            }
        } else {
            progress
        }
        val drawing: DrawScope.() -> Unit = {
            if (sourceBounds != null && sourceSnapshot != null) {
                drawSnapshot(
                    snapshot = sourceSnapshot,
                    destination = destination,
                    // Keep a shared image opaque beneath its destination. A conventional
                    // source/destination alpha crossfade briefly lowers the combined opacity, and
                    // is especially visible while Coil is warming its cache on the first open.
                    alpha = if (image && targetBounds != null) 1f else 1f - progress,
                )
            }
            if (targetBounds != null && targetSnapshot != null) {
                drawSnapshot(
                    snapshot = targetSnapshot,
                    destination = destination,
                    alpha = targetAlpha,
                )
            }
        }
        if (imageClip != null) clipPath(imageClip, block = drawing) else drawing()
    }

    fun drawSourceAccessory(bounds: Rect?, snapshot: ImageBitmap?) {
        val alpha = sourceAccessoryAlpha(progress)
        if (bounds == null || snapshot == null || alpha <= 0.001f) return
        val destination = moveBetweenContainers(bounds, sourceContainer, container)
        clipPath(containerClip) {
            drawSnapshot(
                snapshot = snapshot,
                destination = destination,
                alpha = alpha,
            )
        }
    }

    drawSourceAccessory(
        source.index,
        transition.sourceSnapshot(StoryPreviewSourceElement.Index),
    )
    drawSourceAccessory(
        source.comments,
        transition.sourceSnapshot(StoryPreviewSourceElement.Comments),
    )
    drawElement(
        StoryPreviewSharedElement.Image,
        source.image,
        transition.sourceSnapshot(StoryPreviewSourceElement.Image),
        image = true,
    )
    drawElement(
        StoryPreviewSharedElement.Title,
        source.title,
        transition.sourceSnapshot(StoryPreviewSourceElement.Title),
    )
    drawElement(
        StoryPreviewSharedElement.Summary,
        source.summary,
        transition.sourceSnapshot(StoryPreviewSourceElement.Summary),
    )
    drawElement(
        StoryPreviewSharedElement.Meta,
        source.meta,
        transition.sourceSnapshot(StoryPreviewSourceElement.Meta),
    )

    val supplementary = transition
        .targetBounds(StoryPreviewSharedElement.Supplementary)
        ?.localTo(transition.rootOffset)
    val supplementarySnapshot =
        transition.targetSnapshot(StoryPreviewSharedElement.Supplementary)
    if (supplementary != null && supplementarySnapshot != null) {
        // Bottom controls follow the container's motion, but retain their measured size. Scaling
        // this bitmap with the container visibly squashes the icons and Comments pill early in
        // opening and late in closing.
        val destination = moveBetweenContainers(supplementary, targetContainer, container)
        clipPath(containerClip) {
            drawSnapshot(
                snapshot = supplementarySnapshot,
                destination = destination,
                alpha = supplementaryAlpha(progress),
            )
        }
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
    ) {
        return
    }
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

private fun mapBetweenContainers(rect: Rect, from: Rect, to: Rect): Rect {
    if (from.width <= 0f || from.height <= 0f) return rect
    val scaleX = to.width / from.width
    val scaleY = to.height / from.height
    return Rect(
        left = to.left + (rect.left - from.left) * scaleX,
        top = to.top + (rect.top - from.top) * scaleY,
        right = to.left + (rect.right - from.left) * scaleX,
        bottom = to.top + (rect.bottom - from.top) * scaleY,
    )
}

/** Maps an element's center with its container while preserving the element's intrinsic size. */
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

private fun roundedPath(rect: Rect, topRadius: Float, bottomRadius: Float): Path =
    Path().apply {
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

private fun StoryPreviewSourceGeometry.localTo(offset: Offset): StoryPreviewSourceGeometry =
    copy(
        container = container.localTo(offset),
        image = image?.localTo(offset),
        title = title?.localTo(offset),
        summary = summary?.localTo(offset),
        meta = meta?.localTo(offset),
        index = index?.localTo(offset),
        comments = comments?.localTo(offset),
    )

private fun Rect.localTo(offset: Offset): Rect = translate(-offset.x, -offset.y)

private fun supplementaryAlpha(progress: Float): Float =
    ((progress - 0.42f) / 0.43f).coerceIn(0f, 1f)

/** Fades out in the first 60% when opening and fades in in the final 60% when closing. */
private fun sourceAccessoryAlpha(progress: Float): Float =
    (1f - progress / 0.6f).coerceIn(0f, 1f)

private fun lerp(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = lerp(start.left, end.left, progress),
    top = lerp(start.top, end.top, progress),
    right = lerp(start.right, end.right, progress),
    bottom = lerp(start.bottom, end.bottom, progress),
)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
