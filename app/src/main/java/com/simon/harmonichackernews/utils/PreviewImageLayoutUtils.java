package com.simon.harmonichackernews.utils;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.Objects;

public final class PreviewImageLayoutUtils {

    private PreviewImageLayoutUtils() {
    }

    public static void applyWideImageHeight(
            ImageView imageView,
            Drawable drawable,
            int defaultHeightDp) {
        applyWideImageHeight(imageView, imageView, drawable, defaultHeightDp);
    }

    public static void applyWideImageHeight(
            ImageView imageView,
            View heightTarget,
            Drawable drawable,
            int defaultHeightDp) {
        if (imageView == null || heightTarget == null || drawable == null) {
            return;
        }

        int defaultHeight = Utils.pxFromDpInt(imageView.getResources(), defaultHeightDp);
        Object expectedTag = imageView.getTag();
        Runnable updateHeight = () -> {
            if (!Objects.equals(expectedTag, imageView.getTag())) {
                return;
            }

            int width = imageView.getWidth();
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (width <= 0 || intrinsicWidth <= 0 || intrinsicHeight <= 0) {
                return;
            }

            int aspectRatioHeight = Math.max(
                    1,
                    Math.round((float) width * intrinsicHeight / intrinsicWidth));
            setHeight(heightTarget, Math.min(defaultHeight, aspectRatioHeight));
        };

        resetHeight(heightTarget, defaultHeightDp);
        if (imageView.getWidth() > 0) {
            updateHeight.run();
        } else {
            imageView.post(updateHeight);
        }
    }

    public static void resetHeight(View view, int defaultHeightDp) {
        if (view == null) {
            return;
        }
        setHeight(view, Utils.pxFromDpInt(view.getResources(), defaultHeightDp));
    }

    private static void setHeight(View view, int height) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams != null && layoutParams.height != height) {
            layoutParams.height = height;
            view.setLayoutParams(layoutParams);
        }
    }
}
