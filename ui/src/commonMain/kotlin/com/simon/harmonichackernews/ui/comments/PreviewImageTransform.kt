package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal const val PreviewImageMaxZoom = 5f

/** Keep the fitted image fixed while zoom reveals more of it vertically, up to the safe area. */
internal data class PreviewImageGeometry(val available: Size, val aspectRatio: Float) {
    val baseViewport = Size(available.width, min(available.width / aspectRatio, available.height))
    val image = fittedPreviewImageSize(baseViewport, aspectRatio)

    fun viewport(scale: Float): Size = Size(
        baseViewport.width,
        (image.height * scale.coerceIn(1f, PreviewImageMaxZoom))
            .coerceIn(baseViewport.height, available.height),
    )

    fun zoomBy(transform: PreviewImageTransform, zoom: Float, centroid: Offset, pan: Offset) =
        transform.zoomBy(zoom, centroid, pan, viewport(transform.scale), image,
            targetViewport = viewport(transform.scale * zoom))
}

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

    fun zoomBy(
        zoom: Float,
        centroid: Offset,
        pan: Offset,
        viewport: Size,
        image: Size,
        targetViewport: Size = viewport,
    ): PreviewImageTransform {
        val nextScale = (scale * zoom).coerceIn(1f, PreviewImageMaxZoom)
        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        return PreviewImageTransform(
            nextScale,
            focus - (focus - offset) * (nextScale / scale) + pan,
        ).bounded(targetViewport, image)
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
