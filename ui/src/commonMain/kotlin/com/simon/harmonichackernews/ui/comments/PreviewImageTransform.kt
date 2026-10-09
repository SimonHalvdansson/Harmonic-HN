package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal const val PreviewImageMaxZoom = 5f

internal data class PreviewImageTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
) {
    fun bounded(viewport: Size, image: Size): PreviewImageTransform {
        val zoom = scale.coerceIn(1f, PreviewImageMaxZoom)
        val maxX = ((image.width * zoom - viewport.width) / 2f).coerceAtLeast(0f)
        val maxY = ((image.height * zoom - viewport.height) / 2f).coerceAtLeast(0f)
        return PreviewImageTransform(
            zoom,
            Offset(
                if (maxX == 0f) 0f else offset.x.coerceIn(-maxX, maxX),
                if (maxY == 0f) 0f else offset.y.coerceIn(-maxY, maxY),
            ),
        )
    }

    fun zoomBy(zoom: Float, centroid: Offset, pan: Offset, viewport: Size, image: Size): PreviewImageTransform {
        val nextScale = (scale * zoom).coerceIn(1f, PreviewImageMaxZoom)
        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        return PreviewImageTransform(
            nextScale,
            focus - (focus - offset) * (nextScale / scale) + pan,
        ).bounded(viewport, image)
    }

    fun imageBounds(viewport: Size, image: Size): Rect {
        val center = Offset(viewport.width / 2f, viewport.height / 2f) + offset
        val half = Offset(image.width, image.height) * (scale / 2f)
        return Rect(center - half, center + half)
    }
}

internal fun fittedPreviewImageSize(viewport: Size, aspectRatio: Float): Size {
    val width = min(viewport.width, viewport.height * aspectRatio)
    return Size(width, width / aspectRatio)
}
