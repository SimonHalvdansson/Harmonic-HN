package com.simon.harmonichackernews;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

/**
 * Prevents a bottom sheet from reacting to a gesture that Android reserves for system UI.
 *
 * <p>The app can receive the start of a back, home, or other edge gesture before the system
 * claims it. Ignoring the complete touch sequence keeps the sheet from moving or settling in
 * response to the portion delivered before Android cancels the app's touch handling.</p>
 */
public class SystemGestureSafeBottomSheetBehavior<V extends View> extends BottomSheetBehavior<V> {
    private boolean systemGestureInProgress;

    public SystemGestureSafeBottomSheetBehavior() {
        super();
    }

    public SystemGestureSafeBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child,
                                        @NonNull MotionEvent event) {
        updateSystemGestureState(parent, event);
        if (systemGestureInProgress) {
            return false;
        }
        return super.onInterceptTouchEvent(parent, child, event);
    }

    @Override
    public boolean onTouchEvent(@NonNull CoordinatorLayout parent, @NonNull V child,
                                @NonNull MotionEvent event) {
        updateSystemGestureState(parent, event);
        if (systemGestureInProgress) {
            return false;
        }
        return super.onTouchEvent(parent, child, event);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout parent, @NonNull V child,
                                       @NonNull View directTargetChild, @NonNull View target,
                                       int axes, int type) {
        if (systemGestureInProgress && type == ViewCompat.TYPE_TOUCH) {
            return false;
        }
        return super.onStartNestedScroll(parent, child, directTargetChild, target, axes, type);
    }

    private void updateSystemGestureState(@NonNull CoordinatorLayout parent,
                                          @NonNull MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return;
        }

        WindowInsetsCompat windowInsets = ViewCompat.getRootWindowInsets(parent);
        if (windowInsets == null) {
            systemGestureInProgress = false;
            return;
        }

        Insets gestureInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemGestures()
                        | WindowInsetsCompat.Type.mandatorySystemGestures());
        float x = event.getX();
        float y = event.getY();
        systemGestureInProgress = x < gestureInsets.left
                || x >= parent.getWidth() - gestureInsets.right
                || y < gestureInsets.top
                || y >= parent.getHeight() - gestureInsets.bottom;
    }
}
