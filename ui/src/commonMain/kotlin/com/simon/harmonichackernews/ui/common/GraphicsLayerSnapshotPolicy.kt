package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.sqrt

internal const val MaxGraphicsLayerSnapshotBytes = 8L * 1024L * 1024L
internal const val MaxGraphicsLayerSnapshotDimension = 4096
private const val SnapshotBytesPerPixel = 4L

internal fun graphicsLayerSnapshotByteCount(size: IntSize): Long? {
    if (size.width <= 0 || size.height <= 0) return null
    val pixels = size.width.toLong() * size.height.toLong()
    return if (pixels > Long.MAX_VALUE / SnapshotBytesPerPixel) {
        Long.MAX_VALUE
    } else {
        pixels * SnapshotBytesPerPixel
    }
}

internal fun isGraphicsLayerSnapshotSizeSafe(size: IntSize): Boolean {
    if (
        size.width > MaxGraphicsLayerSnapshotDimension ||
        size.height > MaxGraphicsLayerSnapshotDimension
    ) {
        return false
    }
    return graphicsLayerSnapshotByteCount(size)?.let { it <= MaxGraphicsLayerSnapshotBytes } == true
}

internal fun GraphicsLayer.isSnapshotCaptureSafe(): Boolean =
    !isReleased && isGraphicsLayerSnapshotSizeSafe(size)

/** Preserve aspect ratio while bounding both bitmap memory and texture dimensions. */
internal fun boundedGraphicsLayerSnapshotSize(size: IntSize): IntSize? {
    if (size.width <= 0 || size.height <= 0) return null
    val bytes = size.width.toDouble() * size.height * SnapshotBytesPerPixel
    val scale = min(
        1.0,
        min(
            sqrt(MaxGraphicsLayerSnapshotBytes.toDouble() / bytes),
            MaxGraphicsLayerSnapshotDimension.toDouble() / maxOf(size.width, size.height),
        ),
    )
    return IntSize(
        (size.width * scale).toInt().coerceAtLeast(1),
        (size.height * scale).toInt().coerceAtLeast(1),
    )
}

internal data class GraphicsLayerSnapshot(
    val image: ImageBitmap? = null,
    val refreshKey: Int? = null,
    val resolvedKey: Int? = null,
) {
    fun isCurrent(key: Int): Boolean = image != null && refreshKey == key
    fun isUnavailable(key: Int): Boolean = resolvedKey == key && !isCurrent(key)
}

@Composable
internal fun rememberGraphicsLayerSnapshot(
    layer: GraphicsLayer?,
    refreshKey: Int,
    downsampleOversizedLayer: Boolean = false,
): GraphicsLayerSnapshot {
    val boundedLayer = if (downsampleOversizedLayer) rememberGraphicsLayer() else null
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // Keep the last bitmap visible while a dismiss-triggered refresh is recorded. Replacing it
    // with null here produces a single empty overlay frame when an opening animation is reversed.
    var snapshot by remember(layer) { mutableStateOf(GraphicsLayerSnapshot()) }
    LaunchedEffect(layer, refreshKey, downsampleOversizedLayer) {
        val currentLayer = layer ?: return@LaunchedEffect
        // The layer is published at composition time and recorded during draw.
        withFrameNanos { }
        val captureSize = if (downsampleOversizedLayer) {
            boundedGraphicsLayerSnapshotSize(currentLayer.size)
        } else {
            currentLayer.size.takeIf(::isGraphicsLayerSnapshotSizeSafe)
        }
        if (currentLayer.isReleased || captureSize == null) {
            snapshot = snapshot.copy(resolvedKey = refreshKey)
            return@LaunchedEffect
        }
        val image = try {
            if (boundedLayer != null && captureSize != currentLayer.size) {
                // Rasterize at the bounded size; never allocate the oversized bitmap first.
                boundedLayer.record(density, layoutDirection, captureSize) {
                    scale(
                        scaleX = captureSize.width.toFloat() / currentLayer.size.width,
                        scaleY = captureSize.height.toFloat() / currentLayer.size.height,
                        pivot = Offset.Zero,
                    ) {
                        drawLayer(currentLayer)
                    }
                }
                boundedLayer.toImageBitmap()
            } else {
                currentLayer.toImageBitmap()
            }
        } catch (_: Exception) {
            snapshot = snapshot.copy(resolvedKey = refreshKey)
            return@LaunchedEffect
        }
        snapshot = GraphicsLayerSnapshot(
            image = image,
            refreshKey = refreshKey,
            resolvedKey = refreshKey,
        )
    }
    return snapshot
}
