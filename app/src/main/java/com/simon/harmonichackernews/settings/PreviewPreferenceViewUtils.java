package com.simon.harmonichackernews.settings;

import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

final class PreviewPreferenceViewUtils {

    private PreviewPreferenceViewUtils() {
    }

    static void copyTextViewForMeasurement(TextView source, TextView target) {
        if (source == null || target == null) {
            return;
        }

        target.setText(source.getText());
        target.setVisibility(source.getVisibility());
        target.setTextSize(TypedValue.COMPLEX_UNIT_PX, source.getTextSize());
        target.setTypeface(source.getTypeface());
    }

    static void copyViewVisibilityForMeasurement(View source, View target) {
        if (source == null || target == null) {
            return;
        }

        target.setVisibility(source.getVisibility());
    }

    static void setExactHeight(View view, int height) {
        if (view == null || height <= 0) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
        view.setMinimumHeight(height);
    }

    static void setWrapContentHeight(View view) {
        if (view == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(layoutParams);
        }
        view.setMinimumHeight(0);
    }

    static int getOuterHeight(View view) {
        if (view == null || view.getVisibility() == View.GONE) {
            return 0;
        }

        int height = view.getMeasuredHeight();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height > 0) {
            height = layoutParams.height;
        }
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
            height += margins.topMargin + margins.bottomMargin;
        }
        return height;
    }

    static int getMeasuredOuterHeight(View view) {
        if (view == null || view.getVisibility() == View.GONE) {
            return 0;
        }

        int height = view.getMeasuredHeight();
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) layoutParams;
            height += margins.topMargin + margins.bottomMargin;
        }
        return height;
    }

    static void requestPreviewRemeasure(
            ViewGroup previewItemContainer,
            ViewGroup previewRoot,
            View boundItemView) {
        if (previewItemContainer != null) {
            previewItemContainer.requestLayout();
        }
        if (previewRoot != null) {
            previewRoot.requestLayout();
            ViewGroup settingsList = findAncestorOfType(previewRoot, RecyclerView.class);
            if (settingsList != null) {
                settingsList.requestLayout();
            }
        }
        if (boundItemView != null) {
            boundItemView.requestLayout();
        }
    }

    private static <T extends ViewGroup> T findAncestorOfType(View view, Class<T> type) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (type.isInstance(parent)) {
                return type.cast(parent);
            }
            parent = parent.getParent();
        }
        return null;
    }
}
