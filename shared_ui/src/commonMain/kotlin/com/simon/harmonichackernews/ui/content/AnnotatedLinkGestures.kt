package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult

/** Gesture state shared by rich-text rows and headers to suppress a click after a long press. */
class AnnotatedLinkGestureState {
    private var suppressNextLinkClick = false

    fun beginGesture() {
        suppressNextLinkClick = false
    }

    fun markLongPress() {
        suppressNextLinkClick = true
    }

    fun consumeSuppressedLinkClick(): Boolean = suppressNextLinkClick.also {
        suppressNextLinkClick = false
    }
}

fun Modifier.detectAnnotatedLinkLongPress(
    text: AnnotatedString,
    layoutResult: () -> TextLayoutResult?,
    coordinates: () -> LayoutCoordinates?,
    linkGestureState: AnnotatedLinkGestureState,
    onLongPress: (url: String, label: String, bounds: Rect) -> Unit,
): Modifier = pointerInput(text, linkGestureState, onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        linkGestureState.beginGesture()
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        val layout = layoutResult() ?: return@awaitEachGesture
        if (text.isEmpty()) return@awaitEachGesture
        val offset = layout.getOffsetForPosition(longPress.position).coerceIn(0, text.length - 1)
        val range = text.getLinkAnnotations(offset, (offset + 1).coerceAtMost(text.length))
            .firstOrNull { it.item is LinkAnnotation.Url } ?: return@awaitEachGesture
        val link = range.item as LinkAnnotation.Url
        val bounds = annotatedRangeBoundsInWindow(
            range.start,
            range.end,
            text.length,
            layout,
            coordinates(),
        ) ?: return@awaitEachGesture
        longPress.consume()
        linkGestureState.markLongPress()
        onLongPress(
            link.url,
            text.text.substring(range.start, range.end).trim().ifBlank { link.url },
            bounds,
        )
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

private fun annotatedRangeBoundsInWindow(
    start: Int,
    end: Int,
    textLength: Int,
    layout: TextLayoutResult,
    coordinates: LayoutCoordinates?,
): Rect? {
    if (coordinates == null || !coordinates.isAttached || textLength <= 0) return null
    val first = start.coerceIn(0, textLength - 1)
    val lastExclusive = end.coerceIn(first + 1, textLength)
    var localBounds = layout.getBoundingBox(first)
    for (offset in (first + 1) until lastExclusive) {
        localBounds = localBounds.expandToInclude(layout.getBoundingBox(offset))
    }
    val topLeft = coordinates.localToWindow(Offset(localBounds.left, localBounds.top))
    val bottomRight = coordinates.localToWindow(Offset(localBounds.right, localBounds.bottom))
    return Rect(topLeft, bottomRight)
}

private fun Rect.expandToInclude(other: Rect): Rect = Rect(
    left = minOf(left, other.left),
    top = minOf(top, other.top),
    right = maxOf(right, other.right),
    bottom = maxOf(bottom, other.bottom),
)
