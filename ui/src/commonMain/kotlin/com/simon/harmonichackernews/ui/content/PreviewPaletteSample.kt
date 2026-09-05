package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import coil3.Image
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Matches the preview's 96 px PaletteSamplePainter, including its rounded dimensions. */
internal fun previewPaletteSampleDimensions(width: Int, height: Int): IntSize {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val scale = min(96f / safeWidth, 96f / safeHeight)
    return IntSize(
        max(1f, safeWidth * scale).roundToInt(),
        max(1f, safeHeight * scale).roundToInt(),
    )
}

/**
 * Samples the decoded bitmap using the same drawImage operation and filtering as BitmapPainter.
 * No UI-owned painter is accessed, so the complete operation can run on a background dispatcher.
 */
internal fun Image.toPreviewPaletteSampleBitmap(): ImageBitmap? {
    if (!supportsOffMainPaletteSampling()) return null
    // Request the original dimensions: Coil's toBitmap(width, height) does not scale on Android.
    val source = toPaletteImageBitmap(width, height) ?: return null
    val dimensions = previewPaletteSampleDimensions(source.width, source.height)
    val sample = ImageBitmap(dimensions.width, dimensions.height)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(sample),
        size = Size(dimensions.width.toFloat(), dimensions.height.toFloat()),
    ) {
        drawImage(source, dstSize = dimensions, filterQuality = FilterQuality.Low)
    }
    return sample
}
