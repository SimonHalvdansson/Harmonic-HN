package com.simon.harmonichackernews.utils;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.WeakHashMap;

public final class StoryMetaPreviewAnimator {
    private static final long ANIMATION_DURATION_MS = 180;
    private static final String POINTS_PREFIX = "53 points \u2022 ";
    private static final String META_WITH_POINTS = POINTS_PREFIX + "quantamagazine.org \u2022 2h";
    private static final String META_WITHOUT_POINTS = "quantamagazine.org \u2022 2h";
    private static final WeakHashMap<TextView, ValueAnimator> RUNNING_ANIMATORS = new WeakHashMap<>();

    private StoryMetaPreviewAnimator() {
    }

    @SuppressLint("SetTextI18n")
    public static void setPointsVisible(TextView storyMeta, boolean showPoints, boolean animate) {
        if (storyMeta == null) {
            return;
        }

        cancelRunningAnimator(storyMeta);
        storyMeta.animate().cancel();
        storyMeta.setAlpha(1f);

        String targetText = showPoints ? META_WITH_POINTS : META_WITHOUT_POINTS;
        if (!animate || !storyMeta.isLaidOut()) {
            storyMeta.setText(targetText);
            return;
        }

        CharSequence currentText = storyMeta.getText();
        if (targetText.contentEquals(currentText) && !hasAnimatedPrefixSpan(currentText)) {
            return;
        }

        animatePointsPrefix(storyMeta, showPoints);
    }

    private static boolean hasAnimatedPrefixSpan(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return false;
        }

        Spanned spanned = (Spanned) text;
        return spanned.getSpans(0, spanned.length(), AnimatedPrefixSpan.class).length > 0;
    }

    private static void animatePointsPrefix(TextView storyMeta, boolean showPoints) {
        AnimatedPrefixSpan span = new AnimatedPrefixSpan(showPoints ? 0f : 1f);
        SpannableString text = new SpannableString(META_WITH_POINTS);
        text.setSpan(span, 0, POINTS_PREFIX.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyMeta.setText(text);

        ValueAnimator animator = ValueAnimator.ofFloat(showPoints ? 0f : 1f, showPoints ? 1f : 0f);
        RUNNING_ANIMATORS.put(storyMeta, animator);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            span.setProgress((float) animation.getAnimatedValue());
            storyMeta.invalidate();
            storyMeta.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finishAnimation(storyMeta, animator, showPoints);
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                if (RUNNING_ANIMATORS.get(storyMeta) == animator) {
                    RUNNING_ANIMATORS.remove(storyMeta);
                }
            }
        });
        animator.start();
    }

    @SuppressLint("SetTextI18n")
    private static void finishAnimation(TextView storyMeta, ValueAnimator animator, boolean showPoints) {
        if (RUNNING_ANIMATORS.get(storyMeta) != animator) {
            return;
        }

        RUNNING_ANIMATORS.remove(storyMeta);
        storyMeta.setText(showPoints ? META_WITH_POINTS : META_WITHOUT_POINTS);
        storyMeta.setAlpha(1f);
    }

    private static void cancelRunningAnimator(TextView storyMeta) {
        ValueAnimator animator = RUNNING_ANIMATORS.remove(storyMeta);
        if (animator != null) {
            animator.cancel();
        }
    }

    private static final class AnimatedPrefixSpan extends ReplacementSpan {
        private float progress;

        AnimatedPrefixSpan(float progress) {
            this.progress = progress;
        }

        void setProgress(float progress) {
            this.progress = progress;
        }

        @Override
        public int getSize(
                @NonNull Paint paint,
                CharSequence text,
                int start,
                int end,
                @Nullable Paint.FontMetricsInt fm) {
            return Math.round(paint.measureText(text, start, end) * progress);
        }

        @Override
        public void draw(
                @NonNull Canvas canvas,
                CharSequence text,
                int start,
                int end,
                float x,
                int top,
                int y,
                int bottom,
                @NonNull Paint paint) {
            float fullWidth = paint.measureText(text, start, end);
            float visibleWidth = fullWidth * progress;
            if (visibleWidth <= 0f) {
                return;
            }

            int saveCount = canvas.save();
            int previousAlpha = paint.getAlpha();
            canvas.clipRect(x, top, x + visibleWidth, bottom);
            paint.setAlpha(Math.round(previousAlpha * progress));
            canvas.drawText(text, start, end, x, y, paint);
            paint.setAlpha(previousAlpha);
            canvas.restoreToCount(saveCount);
        }
    }
}
