package com.simon.harmonichackernews.ui.common

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
