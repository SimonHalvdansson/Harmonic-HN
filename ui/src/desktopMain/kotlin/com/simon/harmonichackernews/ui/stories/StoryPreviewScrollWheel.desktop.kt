package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.storyPreviewScrollWheelPaging(
    onScroll: (deltaY: Float) -> Unit,
): Modifier = onPointerEvent(
    eventType = PointerEventType.Scroll,
    pass = PointerEventPass.Initial,
) { event ->
    val scrollDeltaY = event.changes.firstOrNull()?.scrollDelta?.y
        ?: return@onPointerEvent
    if (!scrollDeltaY.isFinite() || scrollDeltaY == 0f) return@onPointerEvent
    // Consume before Pager's continuous scroll handler so the shared callback can snap to a page.
    event.changes.forEach { it.consume() }
    onScroll(scrollDeltaY)
}
