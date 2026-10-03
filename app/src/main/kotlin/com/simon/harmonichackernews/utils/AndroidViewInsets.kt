package com.simon.harmonichackernews.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.doOnAttach

object AndroidViewInsets {
    /** Request insets now if attached, or once the view joins its window. */
    fun requestApplyInsetsWhenAttached(view: View) {
        view.doOnAttach { ViewCompat.requestApplyInsets(it) }
    }
}
