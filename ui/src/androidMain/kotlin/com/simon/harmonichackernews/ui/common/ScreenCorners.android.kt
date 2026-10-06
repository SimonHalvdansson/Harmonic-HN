package com.simon.harmonichackernews.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.RoundedCorner
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun rememberScreenCorners(): ScreenCorners {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ScreenCorners()
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    var corners by remember(view) { mutableStateOf(ScreenCorners()) }
    DisposableEffect(view, configuration, density) {
        fun update() {
            val activity = view.context.cornerActivity()
            val manager = view.context.getSystemService(WindowManager::class.java)
            val fullScreen = activity != null && !activity.isInMultiWindowMode &&
                !activity.isInPictureInPictureMode &&
                manager.currentWindowMetrics.bounds == manager.maximumWindowMetrics.bounds
            val insets = view.rootWindowInsets
            fun radius(position: Int) = (insets?.getRoundedCorner(position)?.radius ?: 0) / density
            corners = if (fullScreen) ScreenCorners(
                radius(RoundedCorner.POSITION_TOP_LEFT), radius(RoundedCorner.POSITION_TOP_RIGHT),
                radius(RoundedCorner.POSITION_BOTTOM_RIGHT), radius(RoundedCorner.POSITION_BOTTOM_LEFT),
            ) else ScreenCorners()
        }
        // Observe rather than replace Compose's window-insets listener. Rotation and window
        // resizing re-query both the radii and full-screen eligibility.
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener { update() }
        observer.addOnGlobalLayoutListener(listener)
        update()
        onDispose { if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener) }
    }
    return corners
}

private fun Context.cornerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.cornerActivity()
    else -> null
}
