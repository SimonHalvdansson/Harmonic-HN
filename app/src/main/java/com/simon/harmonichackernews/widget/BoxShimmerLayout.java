package com.simon.harmonichackernews.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.R;

import java.util.ArrayList;

/**
 * Draws Harmonic's rounded loading boxes with a lightweight moving highlight.
 *
 * <p>The child views are used only to measure and position the boxes. This layout draws their
 * rounded silhouettes directly, avoiding an off-screen mask and per-instance animator.
 */
public final class BoxShimmerLayout extends FrameLayout {

    private static final long DEFAULT_DURATION_MS = 2000L;
    private static final float DEFAULT_BASE_ALPHA = 0.15f;
    private static final float DEFAULT_HIGHLIGHT_ALPHA = 0.18f;
    private static final float DEFAULT_CORNER_RADIUS_DP = 6f;
    private static final float HIGHLIGHT_WIDTH_RATIO = 0.55f;
    private static final float MIN_HIGHLIGHT_WIDTH_DP = 72f;
    private static final float HIGHLIGHT_TILT_DEGREES = 20f;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxBounds = new RectF();
    private final Matrix highlightMatrix = new Matrix();

    private long durationMs = DEFAULT_DURATION_MS;
    private float baseAlpha = DEFAULT_BASE_ALPHA;
    private float highlightAlpha = DEFAULT_HIGHLIGHT_ALPHA;
    private float cornerRadius;
    private float minimumHighlightWidth;
    private boolean autoStart = true;
    private boolean animationRequested;
    private boolean aggregatedVisible;
    private boolean registeredForFrames;
    private long animationStartNanos;
    private long lastFrameNanos;
    private float frozenProgress;
    private float highlightWidth;
    private @Nullable LinearGradient highlightGradient;

    public BoxShimmerLayout(@NonNull Context context) {
        this(context, null);
    }

    public BoxShimmerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoxShimmerLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        float density = getResources().getDisplayMetrics().density;
        cornerRadius = DEFAULT_CORNER_RADIUS_DP * density;
        minimumHighlightWidth = MIN_HIGHLIGHT_WIDTH_DP * density;

        if (attrs != null) {
            TypedArray values = context.obtainStyledAttributes(
                    attrs, R.styleable.BoxShimmerLayout, defStyleAttr, 0);
            try {
                autoStart = values.getBoolean(
                        R.styleable.BoxShimmerLayout_boxShimmerAutoStart, true);
                durationMs = Math.max(1L, values.getInt(
                        R.styleable.BoxShimmerLayout_boxShimmerDuration,
                        (int) DEFAULT_DURATION_MS));
                baseAlpha = clampAlpha(values.getFloat(
                        R.styleable.BoxShimmerLayout_boxShimmerBaseAlpha,
                        DEFAULT_BASE_ALPHA));
                highlightAlpha = clampAlpha(values.getFloat(
                        R.styleable.BoxShimmerLayout_boxShimmerHighlightAlpha,
                        DEFAULT_HIGHLIGHT_ALPHA));
            } finally {
                values.recycle();
            }
        }

        int placeholderColor = resolveThemeColor(context, R.attr.textColorDisabled);
        basePaint.setColor(placeholderColor);
        basePaint.setAlpha(Math.round(baseAlpha * 255f));
        animationRequested = autoStart;
        frozenProgress = 0f;
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Starts the highlight sweep, if it is not already requested. */
    public void startShimmer() {
        if (!animationRequested) {
            animationRequested = true;
            animationStartNanos = System.nanoTime();
            lastFrameNanos = animationStartNanos;
            frozenProgress = 0f;
        } else if (animationStartNanos == 0L) {
            animationStartNanos = System.nanoTime();
            lastFrameNanos = animationStartNanos;
        }
        updateFrameRegistration();
        invalidate();
    }

    /** Stops the sweep and leaves the highlight frozen at its current position. */
    public void stopShimmer() {
        if (!animationRequested) {
            return;
        }
        long now = lastFrameNanos != 0L ? lastFrameNanos : System.nanoTime();
        frozenProgress = animationProgress(now);
        animationRequested = false;
        updateFrameRegistration();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (autoStart) {
            animationRequested = true;
            animationStartNanos = System.nanoTime();
            lastFrameNanos = animationStartNanos;
            frozenProgress = 0f;
        } else if (animationRequested && animationStartNanos == 0L) {
            animationStartNanos = System.nanoTime();
            lastFrameNanos = animationStartNanos;
        }
        aggregatedVisible = isShown();
        updateFrameRegistration();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!autoStart && animationRequested) {
            long now = lastFrameNanos != 0L ? lastFrameNanos : System.nanoTime();
            frozenProgress = animationProgress(now);
            animationRequested = false;
        }
        unregisterForFrames();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        aggregatedVisible = isVisible;
        updateFrameRegistration();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateFrameRegistration();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        updateFrameRegistration();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildHighlight(width);
        updateFrameRegistration();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        float progress = animationRequested
                ? animationProgress(lastFrameNanos != 0L ? lastFrameNanos : System.nanoTime())
                : frozenProgress;
        positionHighlight(progress);
        drawBoxes(canvas, this, 0f, 0f);
    }

    private void drawBoxes(Canvas canvas, ViewGroup parent, float parentX, float parentY) {
        float contentX = parentX - parent.getScrollX();
        float contentY = parentY - parent.getScrollY();
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE) {
                continue;
            }

            float childX = contentX + child.getLeft() + child.getTranslationX();
            float childY = contentY + child.getTop() + child.getTranslationY();
            if (child instanceof ViewGroup && ((ViewGroup) child).getChildCount() > 0) {
                drawBoxes(canvas, (ViewGroup) child, childX, childY);
                continue;
            }
            if (child.getBackground() == null || child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }

            boxBounds.set(
                    childX,
                    childY,
                    childX + child.getWidth(),
                    childY + child.getHeight());
            float radius = Math.min(cornerRadius,
                    Math.min(boxBounds.width(), boxBounds.height()) / 2f);
            canvas.drawRoundRect(boxBounds, radius, radius, basePaint);
            if (highlightGradient != null && highlightAlpha > baseAlpha) {
                canvas.drawRoundRect(boxBounds, radius, radius, highlightPaint);
            }
        }
    }

    private void rebuildHighlight(int width) {
        if (width <= 0 || highlightAlpha <= baseAlpha) {
            highlightGradient = null;
            highlightPaint.setShader(null);
            return;
        }

        highlightWidth = Math.max(width * HIGHLIGHT_WIDTH_RATIO, minimumHighlightWidth);
        int color = basePaint.getColor();
        float overlayAlpha = (highlightAlpha - baseAlpha) / (1f - baseAlpha);
        int peak = Color.argb(
                Math.round(clampAlpha(overlayAlpha) * 255f),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
        int transparent = Color.argb(
                0, Color.red(color), Color.green(color), Color.blue(color));
        highlightGradient = new LinearGradient(
                -highlightWidth / 2f,
                0f,
                highlightWidth / 2f,
                0f,
                new int[]{transparent, peak, transparent},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP);
        highlightPaint.setShader(highlightGradient);
    }

    private void positionHighlight(float progress) {
        if (highlightGradient == null) {
            return;
        }
        float centerX = -highlightWidth + progress * (getWidth() + 2f * highlightWidth);
        highlightMatrix.reset();
        highlightMatrix.setRotate(HIGHLIGHT_TILT_DEGREES);
        highlightMatrix.postTranslate(centerX, getHeight() / 2f);
        highlightGradient.setLocalMatrix(highlightMatrix);
    }

    private float animationProgress(long frameTimeNanos) {
        if (animationStartNanos == 0L) {
            return 0f;
        }
        float durationScale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? ValueAnimator.getDurationScale()
                : 1f;
        if (durationScale <= 0f) {
            return 0f;
        }
        long scaledDuration = Math.max(
                1L, (long) (durationMs * 1_000_000d * durationScale));
        long elapsed = Math.max(0L, frameTimeNanos - animationStartNanos);
        float linear = (elapsed % scaledDuration) / (float) scaledDuration;
        return (float) (Math.cos((linear + 1f) * Math.PI) / 2f + 0.5f);
    }

    private void onShimmerFrame(long frameTimeNanos) {
        lastFrameNanos = frameTimeNanos;
        postInvalidateOnAnimation();
    }

    private void updateFrameRegistration() {
        boolean shouldRegister = animationRequested
                && isAttachedToWindow()
                && aggregatedVisible
                && getWindowVisibility() == VISIBLE
                && hasWindowFocus()
                && getWidth() > 0
                && getHeight() > 0
                && ValueAnimator.areAnimatorsEnabled();
        if (shouldRegister && !registeredForFrames) {
            registeredForFrames = true;
            FrameTicker.get().add(this);
        } else if (!shouldRegister && registeredForFrames) {
            unregisterForFrames();
        }
    }

    private void unregisterForFrames() {
        if (!registeredForFrames) {
            return;
        }
        registeredForFrames = false;
        FrameTicker.get().remove(this);
    }

    private static int resolveThemeColor(Context context, int attribute) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attribute, value, true)) {
            return Color.GRAY;
        }
        if (value.resourceId != 0) {
            return context.getColor(value.resourceId);
        }
        return value.data;
    }

    private static float clampAlpha(float alpha) {
        return Math.max(0f, Math.min(1f, alpha));
    }

    private static final class FrameTicker implements Choreographer.FrameCallback {
        private static @Nullable FrameTicker instance;

        private final ArrayList<BoxShimmerLayout> views = new ArrayList<>();
        private boolean framePosted;

        static FrameTicker get() {
            if (instance == null) {
                instance = new FrameTicker();
            }
            return instance;
        }

        void add(BoxShimmerLayout view) {
            if (!views.contains(view)) {
                views.add(view);
            }
            postNextFrame();
        }

        void remove(BoxShimmerLayout view) {
            views.remove(view);
        }

        @Override
        public void doFrame(long frameTimeNanos) {
            framePosted = false;
            for (int i = views.size() - 1; i >= 0; i--) {
                BoxShimmerLayout view = views.get(i);
                if (view.registeredForFrames) {
                    view.onShimmerFrame(frameTimeNanos);
                } else {
                    views.remove(i);
                }
            }
            postNextFrame();
        }

        private void postNextFrame() {
            if (!framePosted && !views.isEmpty()) {
                framePosted = true;
                Choreographer.getInstance().postFrameCallback(this);
            }
        }
    }
}
