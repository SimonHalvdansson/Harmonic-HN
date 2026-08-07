package com.simon.harmonichackernews.utils

import android.view.View
import android.view.View.OnAttachStateChangeListener
import androidx.core.view.ViewCompat

object ViewUtils {
    /**
     * Requests that insets should be applied to this view once it is attached.
     *
     * Copied from [com.google.android.material.internal.ViewUtils.requestApplyInsetsWhenAttached]
     */
    fun requestApplyInsetsWhenAttached(view: View) {
        if (ViewCompat.isAttachedToWindow(view)) {
            // We're already attached, just request as normal.
            ViewCompat.requestApplyInsets(view)
        } else {
            // We're not attached to the hierarchy, add a listener to request when we are.
            view.addOnAttachStateChangeListener(
                object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        ViewCompat.requestApplyInsets(v)
                    }

                    override fun onViewDetachedFromWindow(v: View) = Unit
                })
        }
    }
}
