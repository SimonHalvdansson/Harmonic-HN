package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize

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
): GraphicsLayerSnapshot {
    // Keep the last bitmap visible while a dismiss-triggered refresh is recorded. Replacing it
    // with null here produces a single empty overlay frame when an opening animation is reversed.
    var snapshot by remember(layer) { mutableStateOf(GraphicsLayerSnapshot()) }
    LaunchedEffect(layer, refreshKey) {
        val currentLayer = layer ?: return@LaunchedEffect
        // The layer is published at composition time and recorded during draw.
        withFrameNanos { }
        if (!currentLayer.isSnapshotCaptureSafe()) {
            snapshot = snapshot.copy(resolvedKey = refreshKey)
            return@LaunchedEffect
        }
        val image = try {
            currentLayer.toImageBitmap()
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
