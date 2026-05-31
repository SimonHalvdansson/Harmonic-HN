package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import com.google.android.material.color.MaterialColors;

public class PreviewImageTintUtils {
    private static final int TINT_SAMPLE_SIZE = 32;
    private static final float CARD_TINT_ALPHA_LIGHT = 0.24f;
    private static final float CARD_TINT_ALPHA_DARK = 0.34f;

    public static int calculateCardTint(Context context, Drawable drawable) {
        int baseColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
        Bitmap bitmap = renderDrawableToSampleBitmap(drawable);
        if (bitmap == null) {
            return baseColor;
        }

        Palette palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate();
        Palette.Swatch swatch = chooseCardTintSwatch(palette);
        if (swatch == null) {
            return baseColor;
        }

        boolean darkBase = ColorUtils.calculateLuminance(baseColor) < 0.5;
        float tintAlpha = darkBase ? CARD_TINT_ALPHA_DARK : CARD_TINT_ALPHA_LIGHT;
        float[] hsl = swatch.getHsl();
        float targetSaturation = Math.min(1.0f, Math.max(0.25f, hsl[1] * 1.1f));
        float targetLuminance = darkBase ? 0.42f : 0.66f;
        int tintColor = ColorUtils.HSLToColor(new float[]{hsl[0], targetSaturation, targetLuminance});
        return ColorUtils.blendARGB(baseColor, tintColor, tintAlpha);
    }

    private static Palette.Swatch chooseCardTintSwatch(Palette palette) {
        Palette.Swatch swatch = palette.getMutedSwatch();
        if (swatch != null) {
            return swatch;
        }

        swatch = palette.getLightMutedSwatch();
        if (swatch != null) {
            return swatch;
        }

        swatch = palette.getDarkMutedSwatch();
        if (swatch != null) {
            return swatch;
        }

        swatch = palette.getVibrantSwatch();
        if (swatch != null) {
            return swatch;
        }

        swatch = palette.getLightVibrantSwatch();
        if (swatch != null) {
            return swatch;
        }

        swatch = palette.getDarkVibrantSwatch();
        if (swatch != null) {
            return swatch;
        }

        return palette.getDominantSwatch();
    }

    private static Bitmap renderDrawableToSampleBitmap(Drawable drawable) {
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        float scale = Math.min((float) TINT_SAMPLE_SIZE / width, (float) TINT_SAMPLE_SIZE / height);
        int sampleWidth = Math.max(1, Math.round(width * scale));
        int sampleHeight = Math.max(1, Math.round(height * scale));

        Bitmap bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Rect oldBounds = new Rect(drawable.getBounds());
        drawable.setBounds(0, 0, sampleWidth, sampleHeight);
        drawable.draw(canvas);
        drawable.setBounds(oldBounds);
        return bitmap;
    }
}
