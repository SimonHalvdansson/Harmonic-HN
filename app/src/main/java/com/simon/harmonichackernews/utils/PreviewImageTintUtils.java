package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.data.Story;

public class PreviewImageTintUtils {
    private static final int TINT_SAMPLE_SIZE = 32;
    private static final float CARD_TINT_ALPHA_LIGHT = 0.24f;
    private static final float CARD_TINT_ALPHA_DARK = 0.34f;

    public static int calculateCardTint(Context context, Drawable drawable) {
        int baseColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
        return calculateCardTint(baseColor, drawable);
    }

    public static int calculateCardTint(int baseColor, Drawable drawable) {
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

    public static boolean updateStoryPreviewImageTintColor(Story story, Drawable drawable, int baseColor) {
        return updateStoryPreviewImageTintColor(
                story,
                story == null ? null : story.previewImageUrl,
                drawable,
                baseColor);
    }

    public static boolean updateStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            Drawable drawable,
            int baseColor) {
        if (story == null || drawable == null || TextUtils.isEmpty(imageUrl)) {
            return false;
        }

        Integer cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
                story.id,
                imageUrl,
                baseColor);
        if (cachedTintColor != null) {
            return setCurrentStoryPreviewImageTintColor(story, imageUrl, baseColor, cachedTintColor);
        }

        try {
            int tintColor = calculateCardTint(baseColor, drawable);
            StoryPreviewImageMemoryCache.putTintColor(story.id, imageUrl, baseColor, tintColor);
            return setCurrentStoryPreviewImageTintColor(story, imageUrl, baseColor, tintColor);
        } catch (RuntimeException e) {
            if (TextUtils.equals(story.previewImageUrl, imageUrl)) {
                clearStoryPreviewImageTintColor(story);
            }
            return false;
        }
    }

    public static boolean syncStoryPreviewImageTintColorFromCache(Story story, int baseColor) {
        if (story == null || TextUtils.isEmpty(story.previewImageUrl)) {
            return false;
        }

        if (isStoryPreviewImageTintColorCurrent(story, baseColor)) {
            return true;
        }

        Integer cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
                story.id,
                story.previewImageUrl,
                baseColor);
        if (cachedTintColor == null) {
            return false;
        }

        setStoryPreviewImageTintColor(story, story.previewImageUrl, baseColor, cachedTintColor);
        return true;
    }

    public static boolean isStoryPreviewImageTintColorCurrent(Story story, int baseColor) {
        return story != null
                && story.previewImageTintColorLoaded
                && baseColor == story.previewImageTintBaseColor
                && TextUtils.equals(story.previewImageTintSourceUrl, story.previewImageUrl);
    }

    public static void clearStoryPreviewImageTintColor(Story story) {
        if (story == null) {
            return;
        }

        story.previewImageTintColorLoaded = false;
        story.previewImageTintSourceUrl = null;
        story.previewImageTintBaseColor = Color.TRANSPARENT;
    }

    private static void setStoryPreviewImageTintColor(Story story, String imageUrl, int baseColor, int tintColor) {
        story.previewImageTintColor = tintColor;
        story.previewImageTintColorLoaded = true;
        story.previewImageTintSourceUrl = imageUrl;
        story.previewImageTintBaseColor = baseColor;
        story.previewImageLoadFailed = false;
    }

    private static boolean setCurrentStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            int baseColor,
            int tintColor) {
        if (!TextUtils.equals(story.previewImageUrl, imageUrl)) {
            return false;
        }

        setStoryPreviewImageTintColor(story, imageUrl, baseColor, tintColor);
        return true;
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
