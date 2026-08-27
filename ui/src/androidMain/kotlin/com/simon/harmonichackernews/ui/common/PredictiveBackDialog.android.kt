package com.simon.harmonichackernews.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

internal actual val platformDialogPredictiveBackSupported: Boolean = true

@Composable
internal actual fun PlatformDialogPredictiveBackHandler(
    enabled: Boolean,
    onProgress: suspend (DialogPredictiveBackEvent) -> Unit,
    onCancelled: suspend () -> Unit,
    onCommitted: suspend () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                onProgress(
                    DialogPredictiveBackEvent(
                        progress = event.progress,
                        swipeDirection = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) {
                            -1f
                        } else {
                            1f
                        },
                    ),
                )
            }
            onCommitted()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { onCancelled() }
        }
    }
}

@Composable
internal actual fun PlatformDialogBackgroundDimAmount(fraction: Float) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val restingDimAmount = remember(dialogWindow) { dialogWindow.attributes.dimAmount }

    SideEffect {
        dialogWindow.setDimAmount(restingDimAmount * fraction.coerceIn(0f, 1f))
    }
    DisposableEffect(dialogWindow, restingDimAmount) {
        onDispose { dialogWindow.setDimAmount(restingDimAmount) }
    }
}
