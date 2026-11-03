package com.simon.harmonichackernews.ui.common

import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.AnimationUtils
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
import com.simon.harmonichackernews.ui.R

internal actual val platformDialogPredictiveBackSupported: Boolean = true

private val LocalDialogDimTarget = staticCompositionLocalOf<View?> { null }
private class ManagedDialogWindow(val window: Window) {
    var suppressExit = false
}
private val LocalManagedDialogWindow = staticCompositionLocalOf<ManagedDialogWindow?> { null }

@Composable
internal actual fun PlatformDialogWindowMotion(suppressExit: Boolean, content: @Composable () -> Unit) {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    if (window == null) {
        content()
        return
    }
    val managed = remember(window) { ManagedDialogWindow(window) }
    SideEffect {
        managed.suppressExit = suppressExit
        // Native window exit retains the dismissed surface after Compose disposes its content.
        // A committed predictive gesture has already drawn that exit and must not play it twice.
        window.setWindowAnimations(
            if (suppressExit || !dialogMotionEnabled(window.decorView)) 0
            else R.style.HarmonicDialogWindowAnimation,
        )
    }
    CompositionLocalProvider(LocalManagedDialogWindow provides managed, content = content)
}

@Composable
internal actual fun PlatformDialogDimHost(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDialogDimTarget provides LocalView.current.rootView, content = content)
}

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
    // Configure the Compose window itself before it is shown. Updating WindowCompat afterward
    // leaves DialogLayout and the soft-input mode using the fitting-window behavior, so the
    // window jumps to its resized bounds before Compose applies the animated IME insets.
    decorFitsSystemWindows = false,
)

@Composable
internal actual fun PlatformDialogBackgroundDimAmount(fraction: Float) {
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    val target = LocalDialogDimTarget.current ?: return
    val managed = LocalManagedDialogWindow.current?.takeIf { it.window === dialogWindow }
    val dim = remember(dialogWindow, target) { DialogDimOverlay(target, DialogScrimAlpha) }
    val darkTheme = HarmonicTheme.isDark

    SideEffect {
        // WindowManager's dim surface is absent from cross-activity predictive-back snapshots.
        // Draw it in the underlying window so the snapshot and the resumed app match exactly.
        dialogWindow.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dim.setFraction(fraction)
        // Dialogs own a separate window and do not inherit the activity's live icon appearance.
        WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
    DisposableEffect(dialogWindow, target, dim) {
        dim.attach(animated = managed != null)
        onDispose {
            dim.detach(animated = managed != null && !managed.suppressExit)
        }
    }
}

@Composable
internal actual fun PlatformDisableDialogWindowAnimations() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    // Settings editors used to suppress all native motion to avoid the platform's translation.
    // The shared host now supplies alpha/center-scale motion. Source-element transforms still
    // own their complete animation and must keep native motion disabled in their own window.
    if (LocalManagedDialogWindow.current?.window !== window) {
        SideEffect { window.setWindowAnimations(0) }
    }
}

private fun dialogMotionEnabled(view: View): Boolean =
    ValueAnimator.areAnimatorsEnabled() && Settings.Global.getFloat(
        view.context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f,
    ) > 0f

/** The underlying window's snapshot includes this layer throughout native enter and exit. */
private class DialogScrimDrawable : Drawable() {
    private val paint = Paint().apply {
        color = Color.BLACK
        // An enclosing dialog is transparent outside its card. Dim its existing pixels only,
        // keeping the application's already-dimmed backdrop stable when a child opens.
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    override fun draw(canvas: Canvas) = canvas.drawRect(bounds, paint)
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class DialogDimOverlay(private val target: View, private val restingDim: Float) {
    private val drawable = DialogScrimDrawable()
    private var fraction = 1f
    private var lifecycle = 1f
    private var animation: ValueAnimator? = null
    private var finished = false
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateBounds() }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = finish()
    }

    fun setFraction(value: Float) {
        fraction = value.coerceIn(0f, 1f)
        if (fraction < 1f) {
            // A live gesture replaces the opening preview, rather than multiplying two fades.
            animation?.cancel()
            animation = null
            lifecycle = 1f
        }
        updateAlpha()
    }

    fun attach(animated: Boolean) {
        updateBounds()
        target.addOnLayoutChangeListener(layoutListener)
        target.addOnAttachStateChangeListener(attachListener)
        lifecycle = if (animated && dialogMotionEnabled(target)) 0f else 1f
        updateAlpha()
        target.overlay.add(drawable)
        if (lifecycle == 0f) animateTo(1f, 180L)
    }

    fun detach(animated: Boolean) {
        animation?.cancel()
        animation = null
        if (!finished && animated && target.isAttachedToWindow && drawable.alpha > 0 && dialogMotionEnabled(target)) {
            animateTo(0f, 120L, ::finish)
        } else finish()
    }

    private fun animateTo(value: Float, duration: Long, onEnd: (() -> Unit)? = null) {
        val windowScale = Settings.Global.getFloat(target.context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        // The runtime scale can differ from the persisted setting (including its null/default
        // value). Use the same effective scale ValueAnimator applies to this animation.
        val animatorScale = if (Build.VERSION.SDK_INT >= 33) ValueAnimator.getDurationScale() else {
            Settings.Global.getFloat(target.context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
        animation = ValueAnimator.ofFloat(lifecycle, value).apply {
            // ValueAnimator applies its own scale. Match the independently scaled window XML.
            this.duration = (duration * windowScale / animatorScale.coerceAtLeast(0.001f)).toLong()
            interpolator = AnimationUtils.loadInterpolator(target.context, android.R.interpolator.fast_out_slow_in)
            addUpdateListener { lifecycle = it.animatedValue as Float; updateAlpha() }
            if (onEnd != null) addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    private fun updateBounds() = drawable.setBounds(0, 0, target.width, target.height)
    private fun updateAlpha() { drawable.alpha = (255 * restingDim * fraction * lifecycle).toInt() }
    private fun finish() {
        if (finished) return
        finished = true
        animation?.cancel()
        animation = null
        target.overlay.remove(drawable)
        target.removeOnLayoutChangeListener(layoutListener)
        target.removeOnAttachStateChangeListener(attachListener)
    }
}
