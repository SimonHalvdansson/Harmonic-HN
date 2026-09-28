package com.simon.harmonichackernews.ui.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal actual val platformDialogPredictiveBackSupported: Boolean = true

private val LocalDialogDimTarget = staticCompositionLocalOf<View?> { null }

@Composable
internal actual fun PlatformDialogDimHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDialogDimTarget provides LocalView.current.rootView, content = content)
}

internal actual fun platformDialogProperties(
    dismissOnBackPress: Boolean,
    dismissOnClickOutside: Boolean,
    usePlatformDefaultWidth: Boolean,
): DialogProperties = DialogProperties(
    dismissOnBackPress = dismissOnBackPress,
    dismissOnClickOutside = dismissOnClickOutside,
    usePlatformDefaultWidth = usePlatformDefaultWidth,
    // Configure the Compose window itself before it is shown. Updating WindowCompat afterward
    // leaves DialogLayout and the soft-input mode using the fitting-window behavior, so the
    // window jumps to its resized bounds before Compose applies the animated IME insets.
    decorFitsSystemWindows = false,
)

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
    val target = LocalDialogDimTarget.current ?: return
    val dim = remember(target) { ColorDrawable(Color.BLACK) }
    val darkTheme = HarmonicTheme.isDark

    SideEffect {
        // WindowManager's dim surface is absent from cross-activity predictive-back snapshots.
        // Draw it in the underlying window so the snapshot and the resumed app match exactly.
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dim.alpha = (255 * restingDimAmount * fraction.coerceIn(0f, 1f)).toInt()
        // Dialogs own a separate window and do not inherit the activity's live icon appearance.
        WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    DisposableEffect(dialogWindow, target, dim) {
        fun updateBounds() = dim.setBounds(0, 0, target.width, target.height)
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
        updateBounds()
        target.addOnLayoutChangeListener(listener)
        target.overlay.add(dim)
        onDispose {
            target.overlay.remove(dim)
            target.removeOnLayoutChangeListener(listener)
        }
    }
}

@Composable
internal actual fun PlatformDisableDialogWindowAnimations() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    SideEffect { window.setWindowAnimations(0) }
}
