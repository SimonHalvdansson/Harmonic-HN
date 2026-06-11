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
    private static final String POINTS_VALUE = "53";
    private static final String POINTS_LABEL = " points";
    private static final String COMPACT_POINTS_SIGN = "+";
    private static final String POINTS_PREFIX = "53 points \u2022 ";
    private static final String COMPACT_POINTS_PREFIX = "+53 \u2022 ";
    private static final String FORMAT_TRANSITION_PREFIX = "+53 points \u2022 ";
    private static final String META_DOMAIN = "quantamagazine";
    private static final String META_TOP_LEVEL_DOMAIN = ".org";
    private static final String META_TIME_SUFFIX = " \u2022 2h";
    private static final String META_SUFFIX_WITH_TOP_LEVEL_DOMAIN = "quantamagazine.org \u2022 2h";
    private static final String META_SUFFIX_WITHOUT_TOP_LEVEL_DOMAIN = "quantamagazine \u2022 2h";
    private static final WeakHashMap<TextView, ValueAnimator> RUNNING_ANIMATORS = new WeakHashMap<>();

    private StoryMetaPreviewAnimator() {
    }

    @SuppressLint("SetTextI18n")
    public static void setPointsVisible(TextView storyMeta, boolean showPoints, boolean animate) {
        setPointsVisible(storyMeta, showPoints, false, true, animate);
    }

    @SuppressLint("SetTextI18n")
    public static void setPointsVisible(TextView storyMeta, boolean showPoints, boolean compactPoints, boolean animate) {
        setPointsVisible(storyMeta, showPoints, compactPoints, true, animate);
    }

    @SuppressLint("SetTextI18n")
    public static void setPointsVisible(
            TextView storyMeta,
            boolean showPoints,
            boolean compactPoints,
            boolean includeTopLevelDomain,
            boolean animate) {
        if (storyMeta == null) {
            return;
        }

        cancelRunningAnimator(storyMeta);
        storyMeta.animate().cancel();
        storyMeta.setAlpha(1f);

        String targetPrefix = compactPoints ? COMPACT_POINTS_PREFIX : POINTS_PREFIX;
        String metaSuffix = getMetaSuffix(includeTopLevelDomain);
        String targetText = showPoints ? targetPrefix + metaSuffix : metaSuffix;
        if (!animate || !storyMeta.isLaidOut()) {
            storyMeta.setText(targetText);
            return;
        }

        CharSequence currentText = storyMeta.getText();
        if (targetText.contentEquals(currentText) && !hasAnimatedPrefixSpan(currentText)) {
            return;
        }

        if (showPoints && isPointsFormatChange(currentText, compactPoints)) {
            animateCompactPointsFormat(storyMeta, compactPoints, metaSuffix);
            return;
        }

        String currentPrefix = getCurrentPointsPrefix(currentText);
        if (isTopLevelDomainChange(currentText, showPoints, targetPrefix, includeTopLevelDomain)) {
            animateTopLevelDomain(storyMeta, showPoints ? targetPrefix : "", includeTopLevelDomain);
            return;
        }

        animatePointsPrefix(storyMeta, showPoints, targetPrefix, currentPrefix, metaSuffix);
    }

    private static String getMetaSuffix(boolean includeTopLevelDomain) {
        return includeTopLevelDomain
                ? META_SUFFIX_WITH_TOP_LEVEL_DOMAIN
                : META_SUFFIX_WITHOUT_TOP_LEVEL_DOMAIN;
    }

    private static String getCurrentPointsPrefix(CharSequence currentText) {
        if (currentText != null) {
            String text = currentText.toString();
            if (text.startsWith(FORMAT_TRANSITION_PREFIX)) {
                return FORMAT_TRANSITION_PREFIX;
            }
            if (text.startsWith(COMPACT_POINTS_PREFIX)) {
                return COMPACT_POINTS_PREFIX;
            }
            if (text.startsWith(POINTS_PREFIX)) {
                return POINTS_PREFIX;
            }
        }
        return POINTS_PREFIX;
    }

    private static boolean isPointsFormatChange(CharSequence currentText, boolean targetCompactPoints) {
        if (currentText == null) {
            return false;
        }

        String text = currentText.toString();
        if (text.startsWith(FORMAT_TRANSITION_PREFIX)) {
            return true;
        }
        return targetCompactPoints
                ? text.startsWith(POINTS_PREFIX)
                : text.startsWith(COMPACT_POINTS_PREFIX);
    }

    private static boolean isTopLevelDomainChange(
            CharSequence currentText,
            boolean targetShowPoints,
            String targetPrefix,
            boolean targetIncludeTopLevelDomain) {
        if (currentText == null) {
            return false;
        }

        String text = currentText.toString();
        boolean currentShowsPoints = text.startsWith(POINTS_PREFIX)
                || text.startsWith(COMPACT_POINTS_PREFIX)
                || text.startsWith(FORMAT_TRANSITION_PREFIX);
        if (currentShowsPoints != targetShowPoints) {
            return false;
        }
        if (targetShowPoints && !text.startsWith(targetPrefix)) {
            return false;
        }

        boolean currentIncludesTopLevelDomain = text.contains(META_DOMAIN + META_TOP_LEVEL_DOMAIN);
        return text.contains(META_DOMAIN)
                && currentIncludesTopLevelDomain != targetIncludeTopLevelDomain;
    }

    private static boolean hasAnimatedPrefixSpan(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return false;
        }

        Spanned spanned = (Spanned) text;
        return spanned.getSpans(0, spanned.length(), AnimatedWidthAlphaSpan.class).length > 0;
    }

    private static void animateCompactPointsFormat(TextView storyMeta, boolean compactPoints, String metaSuffix) {
        CharSequence currentText = storyMeta.getText();
        int labelStart = COMPACT_POINTS_SIGN.length() + POINTS_VALUE.length();
        int labelEnd = labelStart + POINTS_LABEL.length();
        float plusStartProgress = getAnimatedSpanProgress(
                currentText,
                0,
                COMPACT_POINTS_SIGN.length(),
                compactPoints ? 0f : 1f);
        float labelStartProgress = getAnimatedSpanProgress(
                currentText,
                labelStart,
                labelEnd,
                compactPoints ? 1f : 0f);
        AnimatedWidthAlphaSpan plusSpan = new AnimatedWidthAlphaSpan(plusStartProgress);
        AnimatedWidthAlphaSpan labelSpan = new AnimatedWidthAlphaSpan(labelStartProgress);
        SpannableString text = new SpannableString(FORMAT_TRANSITION_PREFIX + metaSuffix);
        text.setSpan(plusSpan, 0, COMPACT_POINTS_SIGN.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(labelSpan, labelStart, labelEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyMeta.setText(text);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        RUNNING_ANIMATORS.put(storyMeta, animator);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            plusSpan.setProgress(lerp(plusStartProgress, compactPoints ? 1f : 0f, progress));
            labelSpan.setProgress(lerp(labelStartProgress, compactPoints ? 0f : 1f, progress));
            storyMeta.invalidate();
            storyMeta.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finishAnimation(storyMeta, animator, true, compactPoints ? COMPACT_POINTS_PREFIX : POINTS_PREFIX, metaSuffix);
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

    private static void animateTopLevelDomain(
            TextView storyMeta,
            String pointsPrefix,
            boolean includeTopLevelDomain) {
        CharSequence currentText = storyMeta.getText();
        int spanStart = pointsPrefix.length() + META_DOMAIN.length();
        int spanEnd = spanStart + META_TOP_LEVEL_DOMAIN.length();
        float startProgress = getAnimatedSpanProgress(
                currentText,
                spanStart,
                spanEnd,
                includeTopLevelDomain ? 0f : 1f);
        AnimatedWidthAlphaSpan topLevelDomainSpan =
                new AnimatedWidthAlphaSpan(startProgress);
        SpannableString text = new SpannableString(
                pointsPrefix + META_DOMAIN + META_TOP_LEVEL_DOMAIN + META_TIME_SUFFIX);
        text.setSpan(topLevelDomainSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyMeta.setText(text);

        ValueAnimator animator = ValueAnimator.ofFloat(
                startProgress,
                includeTopLevelDomain ? 1f : 0f);
        RUNNING_ANIMATORS.put(storyMeta, animator);
        animator.setDuration(ANIMATION_DURATION_MS);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(animation -> {
            topLevelDomainSpan.setProgress((float) animation.getAnimatedValue());
            storyMeta.invalidate();
            storyMeta.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finishTextAnimation(
                        storyMeta,
                        animator,
                        pointsPrefix + getMetaSuffix(includeTopLevelDomain));
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

    private static void animatePointsPrefix(
            TextView storyMeta,
            boolean showPoints,
            String targetPrefix,
            String currentPrefix,
            String metaSuffix) {
        String animatedPrefix = showPoints ? targetPrefix : currentPrefix;
        float startProgress = getAnimatedSpanProgress(
                storyMeta.getText(),
                0,
                animatedPrefix.length(),
                showPoints ? 0f : 1f);
        AnimatedWidthAlphaSpan span = new AnimatedWidthAlphaSpan(startProgress);
        SpannableString text = new SpannableString(animatedPrefix + metaSuffix);
        text.setSpan(span, 0, animatedPrefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        storyMeta.setText(text);

        ValueAnimator animator = ValueAnimator.ofFloat(startProgress, showPoints ? 1f : 0f);
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
                finishAnimation(storyMeta, animator, showPoints, targetPrefix, metaSuffix);
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
    private static void finishAnimation(
            TextView storyMeta,
            ValueAnimator animator,
            boolean showPoints,
            String pointsPrefix,
            String metaSuffix) {
        finishTextAnimation(storyMeta, animator, showPoints ? pointsPrefix + metaSuffix : metaSuffix);
    }

    @SuppressLint("SetTextI18n")
    private static void finishTextAnimation(
            TextView storyMeta,
            ValueAnimator animator,
            String targetText) {
        if (RUNNING_ANIMATORS.get(storyMeta) != animator) {
            return;
        }

        RUNNING_ANIMATORS.remove(storyMeta);
        storyMeta.setText(targetText);
        storyMeta.setAlpha(1f);
    }

    private static void cancelRunningAnimator(TextView storyMeta) {
        ValueAnimator animator = RUNNING_ANIMATORS.remove(storyMeta);
        if (animator != null) {
            animator.cancel();
        }
    }

    private static float getAnimatedSpanProgress(
            CharSequence text,
            int expectedStart,
            int expectedEnd,
            float fallback) {
        if (!(text instanceof Spanned)) {
            return fallback;
        }

        Spanned spanned = (Spanned) text;
        AnimatedWidthAlphaSpan[] spans =
                spanned.getSpans(0, spanned.length(), AnimatedWidthAlphaSpan.class);
        for (AnimatedWidthAlphaSpan span : spans) {
            if (spanned.getSpanStart(span) == expectedStart
                    && spanned.getSpanEnd(span) == expectedEnd) {
                return span.getProgress();
            }
        }
        return fallback;
    }

    private static float lerp(float start, float end, float progress) {
        return start + (end - start) * progress;
    }

    private static final class AnimatedWidthAlphaSpan extends ReplacementSpan {
        private float progress;

        AnimatedWidthAlphaSpan(float progress) {
            this.progress = progress;
        }

        void setProgress(float progress) {
            this.progress = progress;
        }

        float getProgress() {
            return progress;
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
