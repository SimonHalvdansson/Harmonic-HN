package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.style.ReplacementSpan;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class TextSizeImageSpan extends ReplacementSpan {

    private static final float DEFAULT_HEIGHT_SCALE = 0.96f;

    private final Drawable drawable;
    private final float aspectRatio;

    public TextSizeImageSpan(Context context, @DrawableRes int drawableRes) {
        Drawable loadedDrawable = ContextCompat.getDrawable(context, drawableRes);
        if (loadedDrawable == null) {
            throw new IllegalArgumentException("Drawable resource could not be loaded: " + drawableRes);
        }

        drawable = loadedDrawable.mutate();
        int intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth());
        int intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
        aspectRatio = (float) intrinsicWidth / intrinsicHeight;
    }

    @Override
    public int getSize(@NonNull Paint paint,
                       CharSequence text,
                       int start,
                       int end,
                       @Nullable Paint.FontMetricsInt fm) {
        return getDrawableWidth(paint);
    }

    @Override
    public void draw(@NonNull Canvas canvas,
                     CharSequence text,
                     int start,
                     int end,
                     float x,
                     int top,
                     int y,
                     int bottom,
                     @NonNull Paint paint) {
        int drawableWidth = getDrawableWidth(paint);
        int drawableHeight = getDrawableHeight(paint);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        float textCenterY = y + (fontMetrics.ascent + fontMetrics.descent) / 2f;
        int drawableTop = Math.round(textCenterY - drawableHeight / 2f);

        canvas.save();
        canvas.translate(x, drawableTop);
        drawable.setBounds(0, 0, drawableWidth, drawableHeight);
        drawable.draw(canvas);
        canvas.restore();
    }

    private int getDrawableWidth(Paint paint) {
        return Math.round(getDrawableHeight(paint) * aspectRatio);
    }

    private int getDrawableHeight(Paint paint) {
        return Math.round(paint.getTextSize() * DEFAULT_HEIGHT_SCALE);
    }
}
