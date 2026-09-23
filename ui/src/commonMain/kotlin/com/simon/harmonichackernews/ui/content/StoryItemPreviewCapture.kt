package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.stories.StoryPreviewSourceGeometry
import com.simon.harmonichackernews.ui.stories.captureStoryPreviewSourceContent

internal class StoryItemGeometry {
    var coordinates: LayoutCoordinates? = null
    var itemHeightPx: Int = 0
    var largeImageCoordinates: LayoutCoordinates? = null
    var smallImageCoordinates: LayoutCoordinates? = null
    var titleCoordinates: LayoutCoordinates? = null
    var summaryCoordinates: LayoutCoordinates? = null
    var metaCoordinates: LayoutCoordinates? = null
    var indexCoordinates: LayoutCoordinates? = null
    var commentsCoordinates: LayoutCoordinates? = null
    var largeImageLayer: GraphicsLayer? = null
    var smallImageLayer: GraphicsLayer? = null
    var titleLayer: GraphicsLayer? = null
    var summaryLayer: GraphicsLayer? = null
    var metaLayer: GraphicsLayer? = null
    var indexLayer: GraphicsLayer? = null
    var commentsLayer: GraphicsLayer? = null

    fun snapshot(
        style: StoryItemStyle,
        hasPreview: Boolean,
        imageCornerRadiusPx: Float,
    ): StoryPreviewSourceGeometry? {
        val containerBounds = coordinates.windowBoundsOrNull() ?: return null
        val imageCoordinates = when {
            !hasPreview || style.previewImageMode == StoryPreviewMode.OFF -> null
            style.previewImageMode == StoryPreviewMode.LARGE -> largeImageCoordinates
            else -> smallImageCoordinates
        }
        val imageLayer = when {
            !hasPreview || style.previewImageMode == StoryPreviewMode.OFF -> null
            style.previewImageMode == StoryPreviewMode.LARGE -> largeImageLayer
            else -> smallImageLayer
        }
        return StoryPreviewSourceGeometry(
            container = containerBounds,
            containerElevationDp = if (style.cardStyle) 1f else 0f,
            image = imageCoordinates.windowBoundsOrNull(),
            title = titleCoordinates.windowBoundsOrNull(),
            summary = summaryCoordinates
                .takeIf { style.showPreviewText }
                .windowBoundsOrNull(),
            meta = metaCoordinates
                .takeIf { !style.compact }
                .windowBoundsOrNull(),
            index = indexCoordinates.windowBoundsOrNull(),
            comments = commentsCoordinates.windowBoundsOrNull(),
            imageCornerRadiusPx = imageCornerRadiusPx,
            imageLayer = imageLayer?.takeUnless(GraphicsLayer::isReleased),
            titleLayer = titleLayer?.takeUnless(GraphicsLayer::isReleased),
            summaryLayer = summaryLayer?.takeUnless(GraphicsLayer::isReleased),
            metaLayer = metaLayer?.takeUnless(GraphicsLayer::isReleased),
            indexLayer = indexLayer?.takeUnless(GraphicsLayer::isReleased),
            commentsLayer = commentsLayer?.takeUnless(GraphicsLayer::isReleased),
        )
    }
}

private fun LayoutCoordinates?.windowBoundsOrNull(): Rect? =
    this
        ?.takeIf(LayoutCoordinates::isAttached)
        ?.let { coordinates ->
            val topLeft = coordinates.positionInWindow()
            Rect(
                offset = topLeft,
                size = androidx.compose.ui.geometry.Size(
                    coordinates.size.width.toFloat(),
                    coordinates.size.height.toFloat(),
                ),
            )
        }
        ?.takeIf { it.width > 0f && it.height > 0f }

@Composable
internal fun Modifier.captureStoryPreviewElement(
    enabled: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onLayerChanged: (GraphicsLayer) -> Unit,
): Modifier = if (enabled) {
    onGloballyPositioned(onPositioned)
        .captureStoryPreviewSourceContent(onLayerChanged)
} else {
    this
}

/** Records only the active preview source; ordinary scrolling avoids window transforms. */
internal class StoryItemPreviewCapture(
    val geometry: StoryItemGeometry,
    val captureContent: Boolean,
    val modifier: Modifier,
    val onLongClick: (() -> Unit)?,
)

@Composable
internal fun rememberStoryItemPreviewCapture(
    style: StoryItemStyle,
    hasPreview: Boolean,
    listItem: Boolean,
    capturePreviewSourceGeometry: Boolean,
    onGeometryChanged: ((Rect, Int) -> Unit)?,
    onPreviewSourceGeometryChanged: ((StoryPreviewSourceGeometry) -> Unit)?,
    onLinkLongClick: (() -> Unit)?,
): StoryItemPreviewCapture {
    val itemGeometry = remember { StoryItemGeometry() }
    var sourceCaptureRequested by remember { mutableStateOf(false) }
    val captureSourceContent = capturePreviewSourceGeometry || sourceCaptureRequested
    val density = LocalDensity.current
    val itemVerticalPaddingPx = with(density) {
        (if (listItem) 8.dp else 28.dp).roundToPx()
    }
    val previewImageCornerRadiusPx = with(density) {
        when {
            style.previewImageMode == StoryPreviewMode.SMALL -> 6.dp.toPx()
            style.previewImageMode == StoryPreviewMode.MEDIUM && style.borderlessLargeImage ->
                0f
            style.previewImageMode == StoryPreviewMode.MEDIUM -> 10.dp.toPx()
            style.previewImageMode == StoryPreviewMode.LARGE && !style.borderlessLargeImage ->
                8.dp.toPx()
            else -> 0f
        }
    }
    val geometryModifier = if (
        onGeometryChanged == null && onPreviewSourceGeometryChanged == null
    ) {
        Modifier
    } else {
        Modifier
            .onSizeChanged { size ->
                val itemHeightPx = size.height + itemVerticalPaddingPx
                if (itemGeometry.itemHeightPx != itemHeightPx) {
                    itemGeometry.itemHeightPx = itemHeightPx
                    onGeometryChanged?.invoke(Rect.Zero, itemHeightPx)
                }
            }
            .onGloballyPositioned { coordinates ->
                itemGeometry.coordinates = coordinates
                // Normally defer the window transforms until a preview needs this row. Once it is
                // the pager's settled source, however, keep its published geometry aligned with
                // the list. The last pager-driven list delta can land after the page settles; a
                // one-shot snapshot would then make the dismiss transform end at the old position.
                if (capturePreviewSourceGeometry) {
                    itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                        ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
                }
            }
    }
    val trackedLinkLongClick = onLinkLongClick?.let {
        { sourceCaptureRequested = true }
    }
    LaunchedEffect(sourceCaptureRequested, onLinkLongClick) {
        if (sourceCaptureRequested) {
            // Let the newly attached recording modifiers draw once before publishing the source.
            withFrameNanos { }
            itemGeometry.coordinates
                ?.takeIf(LayoutCoordinates::isAttached)
                ?.boundsInWindow()
                ?.let { bounds ->
                    onGeometryChanged?.invoke(bounds, itemGeometry.itemHeightPx)
                }
            itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
            onLinkLongClick?.invoke()
            sourceCaptureRequested = false
        }
    }
    LaunchedEffect(
        capturePreviewSourceGeometry,
        style.previewImageMode,
        style.showPreviewText,
        style.compact,
        hasPreview,
    ) {
        if (capturePreviewSourceGeometry) {
            itemGeometry.snapshot(style, hasPreview, previewImageCornerRadiusPx)
                ?.let { onPreviewSourceGeometryChanged?.invoke(it) }
        }
    }
    return StoryItemPreviewCapture(
        geometry = itemGeometry,
        captureContent = captureSourceContent,
        modifier = geometryModifier,
        onLongClick = trackedLinkLongClick,
    )
}
