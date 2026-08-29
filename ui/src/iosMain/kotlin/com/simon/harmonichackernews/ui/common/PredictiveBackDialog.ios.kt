package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable

internal actual val platformDialogPredictiveBackSupported: Boolean = false

@Composable
internal actual fun PlatformDialogEdgeToEdge() = Unit

@Composable
internal actual fun PlatformDialogPredictiveBackHandler(
    enabled: Boolean,
    onProgress: suspend (DialogPredictiveBackEvent) -> Unit,
    onCancelled: suspend () -> Unit,
    onCommitted: suspend () -> Unit,
) = Unit

@Composable
internal actual fun PlatformDialogBackgroundDimAmount(fraction: Float) = Unit
