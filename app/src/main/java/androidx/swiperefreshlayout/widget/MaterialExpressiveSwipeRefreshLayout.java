package androidx.swiperefreshlayout.widget;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.loadingindicator.LoadingIndicatorDrawable;

/**
 * SwipeRefreshLayout does not expose a drawable hook for its progress view. This class lives in
 * the AndroidX package so it can replace the package-private progress ImageView drawable while
 * keeping the library's nested scrolling and refresh behavior.
 */
public class MaterialExpressiveSwipeRefreshLayout extends SwipeRefreshLayout {
    private static final int INDICATOR_CONTAINER_PADDING_DP = 5;
    private static final int MAX_ALPHA = 255;

    private LoadingIndicator materialIndicator;
    private LoadingIndicatorDrawable materialIndicatorDrawable;
    private boolean materialIndicatorVisible;
    private boolean materialIndicatorAnimating;

    public MaterialExpressiveSwipeRefreshLayout(Context context) {
        super(context);
        useMaterialIndicator(context);
    }

    public MaterialExpressiveSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        useMaterialIndicator(context);
    }

    @Override
    public void setSize(int size) {
        super.setSize(size);
        useMaterialIndicator(getContext());
    }

    @Override
    public void setColorSchemeColors(int... colors) {
        super.setColorSchemeColors(colors);
        if (materialIndicator != null && colors.length > 0) {
            materialIndicator.setIndicatorColor(colors);
        }
    }

    @Override
    public void setProgressBackgroundColorSchemeColor(int color) {
        super.setProgressBackgroundColorSchemeColor(color);
        if (materialIndicator != null) {
            materialIndicator.setContainerColor(color);
            mCircleView.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    @Override
    void reset() {
        super.reset();
        hideMaterialIndicator();
        applyMaterialReveal(0f);
    }

    @Override
    void setAnimationProgress(float progress) {
        super.setAnimationProgress(progress);
        applyMaterialReveal(progress);
        if (mRefreshing && progress > 0f) {
            startMaterialIndicatorAnimation();
        }
    }

    @Override
    void setTargetOffsetTopAndBottom(int offset) {
        super.setTargetOffsetTopAndBottom(offset);
        syncMaterialIndicatorWithOffset();
    }

    @Override
    void startScaleDownAnimation(Animation.AnimationListener listener) {
        if (!mRefreshing && mCurrentTargetOffsetTop == mOriginalOffsetTop) {
            hideMaterialIndicator();
            applyMaterialReveal(0f);
            if (listener != null) {
                listener.onAnimationStart(null);
                listener.onAnimationEnd(null);
            }
            return;
        }
        super.startScaleDownAnimation(listener);
    }

    private void useMaterialIndicator(Context context) {
        materialIndicator = new LoadingIndicator(context);
        int containerSize = getProgressCircleDiameter();
        int indicatorPadding = Math.round(INDICATOR_CONTAINER_PADDING_DP * getResources().getDisplayMetrics().density);
        int indicatorSize = Math.max(0, containerSize - (indicatorPadding * 2));

        materialIndicator.setIndicatorSize(indicatorSize);
        materialIndicator.setContainerWidth(containerSize);
        materialIndicator.setContainerHeight(containerSize);
        materialIndicator.setIndicatorColor(MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0));
        materialIndicator.setContainerColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0));

        materialIndicatorDrawable = materialIndicator.getDrawable();
        mCircleView.setImageDrawable(materialIndicatorDrawable);
        mCircleView.setBackgroundColor(Color.TRANSPARENT);

        materialIndicatorVisible = false;
        materialIndicatorAnimating = false;
        syncMaterialIndicatorWithOffset();
    }

    private void syncMaterialIndicatorWithOffset() {
        if (materialIndicatorDrawable == null || mCircleView == null) {
            return;
        }

        if (mRefreshing) {
            applyMaterialReveal(1f);
            startMaterialIndicatorAnimation();
            return;
        }

        float dragProgress = 0f;
        int dragDistance = mSpinnerOffsetEnd - mOriginalOffsetTop;
        if (dragDistance > 0) {
            dragProgress = (mCurrentTargetOffsetTop - mOriginalOffsetTop) / (float) dragDistance;
        }
        applyMaterialReveal(dragProgress);
    }

    private void applyMaterialReveal(float progress) {
        float clampedProgress = Math.max(0f, Math.min(1f, progress));

        if (mCircleView != null) {
            mCircleView.setScaleX(clampedProgress);
            mCircleView.setScaleY(clampedProgress);
        }

        if (materialIndicatorDrawable != null) {
            int alpha = Math.round(MAX_ALPHA * clampedProgress);
            materialIndicatorDrawable.setAlpha(alpha);
            if (alpha > 0 && mCircleView != null && mCircleView.getVisibility() == View.VISIBLE) {
                ensureMaterialIndicatorVisible();
            }
        }
    }

    private void ensureMaterialIndicatorVisible() {
        if (materialIndicatorDrawable != null && !materialIndicatorVisible) {
            materialIndicatorDrawable.setVisible(true, false, false);
            materialIndicatorVisible = true;
        }
    }

    private void startMaterialIndicatorAnimation() {
        if (materialIndicatorDrawable != null && !materialIndicatorAnimating) {
            materialIndicatorDrawable.setVisible(true, true, true);
            materialIndicatorVisible = true;
            materialIndicatorAnimating = true;
        }
    }

    private void hideMaterialIndicator() {
        if (materialIndicatorDrawable != null && materialIndicatorVisible) {
            materialIndicatorDrawable.setVisible(false, false, false);
        }
        materialIndicatorVisible = false;
        materialIndicatorAnimating = false;
    }
}
