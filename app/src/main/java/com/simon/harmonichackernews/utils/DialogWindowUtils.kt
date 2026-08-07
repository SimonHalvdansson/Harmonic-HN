package com.simon.harmonichackernews.utils

import android.app.Dialog
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object DialogWindowUtils {
    const val DEFAULT_MAX_WIDTH_DP = 500

    @JvmOverloads
    fun applyMaxWidth(dialog: Dialog?, maxWidthDp: Int = DEFAULT_MAX_WIDTH_DP) {
        val window = dialog?.window ?: return
        val displayMetrics = dialog.context.resources.displayMetrics
        val maxWidthPx = (maxWidthDp * displayMetrics.density).roundToInt()
        val horizontalMarginPx = (48 * displayMetrics.density).roundToInt()
        val availableWidth = displayMetrics.widthPixels - horizontalMarginPx
        val targetWidth = min(maxWidthPx, max(0, availableWidth))
        if (targetWidth <= 0) {
            return
        }

        window.setLayout(targetWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }
}
