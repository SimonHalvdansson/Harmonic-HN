package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/** Invokes [onClick] for a mouse or trackpad secondary click. */
internal fun Modifier.onSecondaryClick(
    enabled: Boolean = true,
    onClick: (Offset) -> Unit,
): Modifier = if (enabled) {
    onSecondaryClickIfHandled { position ->
        onClick(position)
        true
    }
} else {
    this
}

/** Like [onSecondaryClick], but leaves the event unconsumed when [onClick] returns false. */
internal fun Modifier.onSecondaryClickIfHandled(
    onClick: (Offset) -> Boolean,
): Modifier =
    pointerInput(onClick) {
        awaitEachGesture {
            var down: PointerInputChange
            while (true) {
                val event = awaitPointerEvent()
                if (
                    event.changes.isNotEmpty() &&
                    event.buttons.isSecondaryPressed &&
                    event.changes.all { it.changedToDown() }
                ) {
                    down = event.changes.first()
                    break
                }
            }
            if (onClick(down.position)) {
                down.consume()
                waitForUpOrCancellation()?.consume()
            }
        }
    }
