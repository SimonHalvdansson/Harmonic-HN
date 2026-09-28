package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

internal actual val platformDialogPredictiveBackSupported: Boolean = false

@Composable
internal actual fun PlatformDialogDimHost(content: @Composable () -> Unit) = content()

internal actual fun platformDialogProperties(
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    usePlatformDefaultWidth: Boolean,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
)

@Composable
internal actual fun PlatformDialogPredictiveBackHandler(
    enabled: Boolean,
    onProgress: suspend (DialogPredictiveBackEvent) -> Unit,
    onCancelled: suspend () -> Unit,
    onCommitted: suspend () -> Unit,
) = Unit

@Composable
internal actual fun PlatformDialogBackgroundDimAmount(fraction: Float) = Unit

@Composable
internal actual fun PlatformDisableDialogWindowAnimations() = Unit
