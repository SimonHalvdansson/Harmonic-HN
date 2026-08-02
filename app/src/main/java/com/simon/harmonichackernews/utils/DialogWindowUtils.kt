package com.simon.harmonichackernews.utils

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

object DialogWindowUtils {
    const val DEFAULT_MAX_WIDTH_DP: Int = 500

    @JvmOverloads
    fun applyMaxWidth(dialog: Dialog?, maxWidthDp: Int = DEFAULT_MAX_WIDTH_DP) {
        if (dialog == null) {
            return
        }

        val window = dialog.getWindow()
        if (window == null) {
            return
        }

        val context = dialog.getContext()
        val maxWidthPx = Math.round(maxWidthDp * context.getResources().getDisplayMetrics().density)
        val horizontalMarginPx = Math.round(48 * context.getResources().getDisplayMetrics().density)
        val availableWidth =
            context.getResources().getDisplayMetrics().widthPixels - horizontalMarginPx
        val targetWidth = min(maxWidthPx, max(0, availableWidth))
        if (targetWidth <= 0) {
            return
        }

        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
