package com.simon.harmonichackernews

import android.app.Activity
import android.graphics.Color
import androidx.core.view.insets.ProtectionLayout
import androidx.core.graphics.ColorUtils
import com.simon.harmonichackernews.utils.AndroidStatusBarProtection

/** Owns the comments pane's system-bar colors and restores the host window on disposal. */
internal class CommentsStatusBarAppearance(private val activity: Activity) {
    private var originalColor: Int? = null
    var paneColor: Int = Color.TRANSPARENT
        private set
    var headerColor: Int = Color.TRANSPARENT
    var headerCoverage: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f) }
    private var appliedProtection: Pair<Boolean, Int>? = null

    fun capture() {
        originalColor = activity.window.statusBarColor
        refreshPaneColor()
        headerColor = paneColor
        appliedProtection = null
    }

    fun refreshPaneColor() {
        paneColor = AndroidStatusBarProtection.getPaneBackgroundColor(activity)
    }

    fun update(root: ProtectionLayout, sheetExpanded: Boolean, adaptive: Boolean, transparent: Boolean) {
        val color = if (sheetExpanded) {
            ColorUtils.blendARGB(paneColor, headerColor, headerCoverage)
        } else {
            paneColor
        }
        val protection = sheetExpanded to if (sheetExpanded) color else Color.TRANSPARENT
        if (appliedProtection != protection) {
            AndroidStatusBarProtection.setTopProtection(root, sheetExpanded, color)
            appliedProtection = protection
        }
        val windowColor = if (adaptive || transparent) Color.TRANSPARENT else color
        if (activity.window.statusBarColor != windowColor) {
            activity.window.statusBarColor = windowColor
        }
    }

    fun restore() {
        originalColor?.let { activity.window.statusBarColor = it }
        originalColor = null
        appliedProtection = null
    }
}
