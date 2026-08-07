package com.simon.harmonichackernews.utils

import android.R
import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.insets.GradientProtection
import androidx.core.view.insets.ProtectionLayout

object StatusBarProtectionUtils {
    @ColorInt
    fun getPaneBackgroundColor(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorBackground, typedValue, true)
        if (typedValue.resourceId != 0) {
            return ContextCompat.getColor(context, typedValue.resourceId)
        }
        return typedValue.data
    }

    fun setTopProtection(
        layout: ProtectionLayout?,
        @ColorInt color: Int,
    ) {
        setTopProtection(layout, true, color)
    }

    fun setTopProtection(
        layout: ProtectionLayout?,
        enabled: Boolean,
        @ColorInt color: Int,
    ) {
        if (layout == null) {
            return
        }

        if (!enabled) {
            layout.setProtections(emptyList())
            return
        }

        layout.setProtections(
            listOf(GradientProtection(WindowInsetsCompat.Side.TOP, color)),
        )
    }
}
