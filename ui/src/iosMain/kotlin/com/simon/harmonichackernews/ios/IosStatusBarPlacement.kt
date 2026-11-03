package com.simon.harmonichackernews.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A wide asymmetric safe-area strip contains side indicators; symmetric cutouts do not. */
internal fun iosHasSideStatusBarInsets(left: Dp, right: Dp): Boolean =
    abs((right - left).value) >= 56f

internal fun iosBrowserTopInset(
    statusBarTop: Dp,
    sideStatusBar: Boolean,
    reserveUpButtonInset: Boolean,
    backInTopInset: Boolean,
): Dp {
    // Back can occupy a tall top safe area even when the indicators live at the side.
    if (reserveUpButtonInset && backInTopInset) return statusBarTop
    val protection = if (sideStatusBar) 0.dp else statusBarTop
    return protection + if (reserveUpButtonInset) 64.dp else 0.dp
}

@Composable
internal fun iosWindowHasSideStatusBar(): Boolean {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    // Use physical window insets. Pane consumption can make symmetric cutouts look asymmetric.
    val insets = WindowInsets.safeDrawing
    return with(density) {
        iosHasSideStatusBarInsets(
            insets.getLeft(this, direction).toDp(),
            insets.getRight(this, direction).toDp(),
        )
    }
}
