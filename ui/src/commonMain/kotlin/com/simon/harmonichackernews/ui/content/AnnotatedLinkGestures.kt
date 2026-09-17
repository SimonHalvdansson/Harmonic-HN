package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import com.simon.harmonichackernews.ui.common.onSecondaryClickIfHandled

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
    hapticFeedback: HapticFeedback,
    onLongPress: (url: String, label: String, bounds: Rect) -> Unit,
): Modifier = pointerInput(text, linkGestureState, hapticFeedback, onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        linkGestureState.beginGesture()
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        val linkAtPress = annotatedLinkAtPosition(
            text = text,
            layout = layoutResult(),
            coordinates = coordinates(),
            position = longPress.position,
        ) ?: return@awaitEachGesture
        longPress.consume()
        linkGestureState.markLongPress()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress(linkAtPress.url, linkAtPress.label, linkAtPress.bounds)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}.onSecondaryClickIfHandled { position ->
    val link = annotatedLinkAtPosition(
        text = text,
        layout = layoutResult(),
        coordinates = coordinates(),
        position = position,
    ) ?: return@onSecondaryClickIfHandled false
    onLongPress(link.url, link.label, link.bounds)
    true
}

private data class AnnotatedLinkAtPosition(
    val url: String,
    val label: String,
    val bounds: Rect,
)

private fun annotatedLinkAtPosition(
    text: AnnotatedString,
    layout: TextLayoutResult?,
    coordinates: LayoutCoordinates?,
    position: androidx.compose.ui.geometry.Offset,
): AnnotatedLinkAtPosition? {
    layout ?: return null
    if (text.isEmpty()) return null
    val offset = layout.getOffsetForPosition(position).coerceIn(0, text.length - 1)
    val range = text.getLinkAnnotations(offset, (offset + 1).coerceAtMost(text.length))
        .firstOrNull { it.item is LinkAnnotation.Url } ?: return null
    val link = range.item as LinkAnnotation.Url
    val textCoordinates = coordinates?.takeIf { it.isAttached } ?: return null
    val pressInWindow = textCoordinates.localToWindow(position)
    return AnnotatedLinkAtPosition(
        url = link.url,
        label = text.text.substring(range.start, range.end).trim().ifBlank { link.url },
        bounds = Rect(
            left = pressInWindow.x - 0.5f,
            top = pressInWindow.y - 0.5f,
            right = pressInWindow.x + 0.5f,
            bottom = pressInWindow.y + 0.5f,
        ),
    )
}
