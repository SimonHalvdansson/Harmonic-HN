package com.simon.harmonichackernews.ui.common

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
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
