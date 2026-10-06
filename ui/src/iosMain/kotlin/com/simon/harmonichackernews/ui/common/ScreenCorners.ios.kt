package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Supplied by the scene's native UIKit host; older hosts naturally keep square corners. */
internal val LocalIosScreenCorners = staticCompositionLocalOf { ScreenCorners() }

@Composable
internal actual fun rememberScreenCorners(): ScreenCorners = LocalIosScreenCorners.current
