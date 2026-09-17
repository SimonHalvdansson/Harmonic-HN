package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun painterPaletteSampleDimensions(size: Size): IntSize {
    val width = if (size.isSpecified) size.width.takeIf { it.isFinite() && it > 0f } ?: 1f else 1f
    val height = if (size.isSpecified) size.height.takeIf { it.isFinite() && it > 0f } ?: 1f else 1f
    val scale = min(96f / width, 96f / height)
    return IntSize(max(1f, width * scale).roundToInt(), max(1f, height * scale).roundToInt())
}

/** UI-owned painters are drawn on Main at the same rounded size as the original loader. */
internal suspend fun Painter.toPainterPaletteSample(
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap = withContext(Dispatchers.Main) {
    val dimensions = painterPaletteSampleDimensions(intrinsicSize)
    val sample = ImageBitmap(dimensions.width, dimensions.height)
    val size = Size(dimensions.width.toFloat(), dimensions.height.toFloat())
    CanvasDrawScope().draw(density, layoutDirection, Canvas(sample), size) {
        with(this@toPainterPaletteSample) { draw(size) }
    }
    sample
}
