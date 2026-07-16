package com.simon.harmonichackernews.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.Map;
import java.util.WeakHashMap;

/** Animates reserved preview-image space away after an asynchronous load failure. */
public final class PreviewImageFailureAnimator {

    public enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    public interface ProgressListener {
        void onProgress(float progress);
    }

    private static final long COLLAPSE_DURATION_MS = 220;
    private static final PathInterpolator COLLAPSE_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    private static final Map<View, CollapseAnimation> ACTIVE_ANIMATIONS = new WeakHashMap<>();

    private PreviewImageFailureAnimator() {
    }

    public static void collapse(View view, Axis axis) {
        collapse(view, axis, null, null);
    }

    public static void collapse(
            View view,
            Axis axis,
            @Nullable ProgressListener progressListener,
            @Nullable Runnable endAction) {
        if (view == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        cancel(view);
        if (view.getVisibility() == View.GONE) {
            if (progressListener != null) {
                progressListener.onProgress(1f);
            }
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        int startSize = axis == Axis.VERTICAL ? view.getHeight() : view.getWidth();
        if (layoutParams == null
                || startSize <= 0
                || !ViewCompat.isAttachedToWindow(view)
                || !ViewCompat.isLaidOut(view)) {
            view.setVisibility(View.GONE);
            if (progressListener != null) {
                progressListener.onProgress(1f);
            }
            if (endAction != null) {
                endAction.run();
            }
            return;
        }

        CollapseAnimation collapseAnimation = new CollapseAnimation(
                view,
                axis,
                startSize,
                progressListener,
                endAction);
        ACTIVE_ANIMATIONS.put(view, collapseAnimation);
        collapseAnimation.start();
    }

    public static void cancel(View view) {
        if (view == null) {
            return;
        }
        CollapseAnimation animation = ACTIVE_ANIMATIONS.remove(view);
        if (animation != null) {
            animation.cancelAndRestore();
        }
    }

    private static int lerp(int start, int end, float progress) {
        return Math.round(start + (end - start) * progress);
    }

    private static final class CollapseAnimation {
        private final View view;
        private final Axis axis;
        private final int startSize;
        private final int originalWidth;
        private final int originalHeight;
        private final int originalMarginStart;
        private final int originalMarginTop;
        private final int originalMarginEnd;
        private final int originalMarginBottom;
        @Nullable private final ProgressListener progressListener;
        @Nullable private final Runnable endAction;
        private final ValueAnimator animator;
        private boolean cancelled;

        CollapseAnimation(
                View view,
                Axis axis,
                int startSize,
                @Nullable ProgressListener progressListener,
                @Nullable Runnable endAction) {
            this.view = view;
            this.axis = axis;
            this.startSize = startSize;
            this.progressListener = progressListener;
            this.endAction = endAction;

            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            originalWidth = layoutParams.width;
            originalHeight = layoutParams.height;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                originalMarginStart = marginParams.getMarginStart();
                originalMarginTop = marginParams.topMargin;
                originalMarginEnd = marginParams.getMarginEnd();
                originalMarginBottom = marginParams.bottomMargin;
            } else {
                originalMarginStart = 0;
                originalMarginTop = 0;
                originalMarginEnd = 0;
                originalMarginBottom = 0;
            }

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(COLLAPSE_DURATION_MS);
            animator.setInterpolator(COLLAPSE_INTERPOLATOR);
            animator.addUpdateListener(valueAnimator -> applyProgress(
                    (Float) valueAnimator.getAnimatedValue()));
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (cancelled || ACTIVE_ANIMATIONS.get(view) != CollapseAnimation.this) {
                        return;
                    }
                    ACTIVE_ANIMATIONS.remove(view);
                    view.setVisibility(View.GONE);
                    restoreLayoutParams();
                    if (progressListener != null) {
                        progressListener.onProgress(1f);
                    }
                    if (endAction != null) {
                        endAction.run();
                    }
                }
            });
        }

        void start() {
            animator.start();
        }

        void cancelAndRestore() {
            cancelled = true;
            animator.cancel();
            restoreLayoutParams();
        }

        private void applyProgress(float progress) {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams == null) {
                return;
            }

            int size = lerp(startSize, 0, progress);
            if (axis == Axis.VERTICAL) {
                layoutParams.height = size;
            } else {
                layoutParams.width = size;
            }

            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                if (axis == Axis.VERTICAL) {
                    marginParams.topMargin = lerp(originalMarginTop, 0, progress);
                    marginParams.bottomMargin = lerp(originalMarginBottom, 0, progress);
                } else {
                    marginParams.setMarginStart(lerp(originalMarginStart, 0, progress));
                    marginParams.setMarginEnd(lerp(originalMarginEnd, 0, progress));
                }
            }

            view.setLayoutParams(layoutParams);
            if (progressListener != null) {
                progressListener.onProgress(progress);
            }
        }

        private void restoreLayoutParams() {
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams == null) {
                return;
            }
            layoutParams.width = originalWidth;
            layoutParams.height = originalHeight;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams =
                        (ViewGroup.MarginLayoutParams) layoutParams;
                marginParams.setMarginStart(originalMarginStart);
                marginParams.topMargin = originalMarginTop;
                marginParams.setMarginEnd(originalMarginEnd);
                marginParams.bottomMargin = originalMarginBottom;
            }
            view.setLayoutParams(layoutParams);
        }
    }
}
