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
    usePlatformInsets: Boolean,
    scrimEnabled: Boolean,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    usePlatformInsets = usePlatformInsets,
    scrimColor = if (scrimEnabled) DialogScrimColor else androidx.compose.ui.graphics.Color.Transparent,
)

@Composable
internal actual fun PlatformDialogBackgroundDimAmount(fraction: Float) = Unit

@Composable
internal actual fun PlatformDisableDialogWindowAnimations() = Unit

@Composable
internal actual fun PlatformDialogWindowMotion(suppressExit: Boolean, content: @Composable () -> Unit) = content()
