package com.simon.harmonichackernews.utils;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;

public class ViewUtils {
    /**
     * Requests that insets should be applied to this view once it is attached.
     * <p>
     * Copied from {@link com.google.android.material.internal.ViewUtils#requestApplyInsetsWhenAttached(View)}
     */
    public static void requestApplyInsetsWhenAttached(@NonNull View view) {
        if (ViewCompat.isAttachedToWindow(view)) {
            // We're already attached, just request as normal.
            ViewCompat.requestApplyInsets(view);
        } else {
            // We're not attached to the hierarchy, add a listener to request when we are.
            view.addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(@NonNull View v) {
                            v.removeOnAttachStateChangeListener(this);
                            ViewCompat.requestApplyInsets(v);
                        }

                        @Override
                        public void onViewDetachedFromWindow(View v) {
                        }
                    });
        }
    }

}
