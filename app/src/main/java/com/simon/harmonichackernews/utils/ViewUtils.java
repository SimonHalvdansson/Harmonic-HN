package com.simon.harmonichackernews.utils;

import android.content.res.Resources;
import android.text.Layout;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class ViewUtils {
    /**
     * Sets up {@link SwipeRefreshLayout}'s progress view position to depend on status bar.
     * <p>
     * The view's starting position is always just outside the visible area,
     * i.e. with transparent status bar it is just outside the screen and with
     * non-transparent status bar it is just to the top of status bar's bottom edge.
     * <p>
     * The view's end position is always the same distance from the status bar's bottom edge.
     * Thus the distance between start and end position depends on the status bar transparency.
     * The distance is equal to the default one when status bar is non-transparent and
     * {@code default + status bar} when status bar is transparent.
     */
    public static void setUpSwipeRefreshWithStatusBarOffset(SwipeRefreshLayout layout) {
        int start = layout.getProgressViewStartOffset();
        int end = layout.getProgressViewEndOffset();

        ViewCompat.setOnApplyWindowInsetsListener(layout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            if (SettingsUtils.shouldUseTransparentStatusBar(layout.getContext())) {
                layout.setProgressViewOffset(false, start, end + top);
            } else {
                layout.setProgressViewOffset(false, start + top, end + top);
            }
            return insets;
        });
        requestApplyInsetsWhenAttached(layout);
    }

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

    public static int getNavigationBarHeight(Resources res) {
        int resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return res.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    public static boolean isTextTruncated(TextView textView) {
        Layout layout = textView.getLayout();
        if (layout != null) {
            int maxLines = textView.getMaxLines();
            if (maxLines == -1) {  // If maxLines is not set, it returns -1
                return false;  // TextView isn't set to truncate, so it can't be truncated
            }
            int lineCount = layout.getLineCount();
            if (lineCount <= maxLines) {
                return false;  // Number of lines is within limit
            } else {
                return true;  // Number of lines is more than limit, thus truncated
            }
        }
        return false;  // Layout is null (not rendered yet), cannot determine if truncated
    }

    public abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

}
