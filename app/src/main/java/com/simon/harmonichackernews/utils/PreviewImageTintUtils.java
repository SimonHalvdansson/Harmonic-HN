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
        return calculateCardTint(baseColor, drawable, SettingsUtils.getPreferredPaletteTintConfigKey(context));
    }

    public static int calculateCardTint(Context context, Drawable drawable, String paletteTintMode) {
        int baseColor = MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
        return calculateCardTint(baseColor, drawable, paletteTintMode);
    }

    public static int calculateCardTint(int baseColor, Drawable drawable) {
        return calculateCardTint(baseColor, drawable, SettingsUtils.PALETTE_TINT_DEFAULT);
    }

    public static int calculateCardTint(int baseColor, Drawable drawable, String paletteTintMode) {
        String paletteTintConfigKey = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        Bitmap bitmap = renderDrawableToSampleBitmap(drawable);
        if (bitmap == null) {
            return baseColor;
        }

        Palette palette = Palette.from(bitmap)
                .maximumColorCount(16)
                .generate();
        Palette.Swatch swatch = chooseCardTintSwatch(palette, paletteTintConfigKey);
        if (swatch == null) {
            return baseColor;
        }

        boolean darkBase = ColorUtils.calculateLuminance(baseColor) < 0.5;
        float tintAlpha = clamp01(
                (darkBase ? CARD_TINT_ALPHA_DARK : CARD_TINT_ALPHA_LIGHT)
                        * SettingsUtils.getPaletteTintStrengthMultiplier(paletteTintConfigKey));
        float[] hsl = swatch.getHsl();
        float targetSaturation = clamp01(
                hsl[1] * SettingsUtils.getPaletteTintColorfulnessMultiplier(paletteTintConfigKey));
        if (SettingsUtils.getPaletteTintColorfulness(paletteTintConfigKey)
                >= SettingsUtils.DEFAULT_PALETTE_TINT_COLORFULNESS) {
            targetSaturation = Math.max(0.25f, targetSaturation);
        }
        float targetLuminance = clamp(
                (darkBase ? 0.42f : 0.66f) + SettingsUtils.getPaletteTintToneOffset(paletteTintConfigKey),
                0.05f,
                0.95f);
        int tintColor = ColorUtils.HSLToColor(new float[]{hsl[0], targetSaturation, targetLuminance});
        return ColorUtils.blendARGB(baseColor, tintColor, tintAlpha);
    }

    public static boolean updateStoryPreviewImageTintColor(Story story, Drawable drawable, int baseColor) {
        return updateStoryPreviewImageTintColor(
                story,
                story == null ? null : story.previewImageUrl,
                drawable,
                baseColor,
                SettingsUtils.PALETTE_TINT_DEFAULT);
    }

    public static boolean updateStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            Drawable drawable,
            int baseColor) {
        return updateStoryPreviewImageTintColor(
                story,
                imageUrl,
                drawable,
                baseColor,
                SettingsUtils.PALETTE_TINT_DEFAULT);
    }

    public static boolean updateStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            Drawable drawable,
            int baseColor,
            String paletteTintMode) {
        if (story == null || drawable == null || TextUtils.isEmpty(imageUrl)) {
            return false;
        }

        String safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        Integer cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
                story.id,
                imageUrl,
                baseColor,
                safePaletteTintMode);
        if (cachedTintColor != null) {
            return setCurrentStoryPreviewImageTintColor(
                    story,
                    imageUrl,
                    baseColor,
                    safePaletteTintMode,
                    cachedTintColor);
        }

        try {
            int tintColor = calculateCardTint(baseColor, drawable, safePaletteTintMode);
            StoryPreviewImageMemoryCache.putTintColor(story.id, imageUrl, baseColor, safePaletteTintMode, tintColor);
            return setCurrentStoryPreviewImageTintColor(
                    story,
                    imageUrl,
                    baseColor,
                    safePaletteTintMode,
                    tintColor);
        } catch (RuntimeException e) {
            if (TextUtils.equals(story.previewImageUrl, imageUrl)) {
                clearStoryPreviewImageTintColor(story);
            }
            return false;
        }
    }

    public static boolean syncStoryPreviewImageTintColorFromCache(Story story, int baseColor) {
        return syncStoryPreviewImageTintColorFromCache(story, baseColor, SettingsUtils.PALETTE_TINT_DEFAULT);
    }

    public static boolean syncStoryPreviewImageTintColorFromCache(Story story, int baseColor, String paletteTintMode) {
        if (story == null || TextUtils.isEmpty(story.previewImageUrl)) {
            return false;
        }

        String safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        if (isStoryPreviewImageTintColorCurrent(story, baseColor, safePaletteTintMode)) {
            return true;
        }

        Integer cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
                story.id,
                story.previewImageUrl,
                baseColor,
                safePaletteTintMode);
        if (cachedTintColor == null) {
            return false;
        }

        setStoryPreviewImageTintColor(
                story,
                story.previewImageUrl,
                baseColor,
                safePaletteTintMode,
                cachedTintColor);
        return true;
    }

    public static boolean applyCachedStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            int baseColor,
            String paletteTintMode,
            int tintColor) {
        if (story == null || TextUtils.isEmpty(imageUrl)) {
            return false;
        }

        String safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        StoryPreviewImageMemoryCache.putTintColor(story.id, imageUrl, baseColor, safePaletteTintMode, tintColor);
        return setCurrentStoryPreviewImageTintColor(
                story,
                imageUrl,
                baseColor,
                safePaletteTintMode,
                tintColor);
    }

    public static boolean isStoryPreviewImageTintColorCurrent(Story story, int baseColor) {
        return isStoryPreviewImageTintColorCurrent(story, baseColor, SettingsUtils.PALETTE_TINT_DEFAULT);
    }

    public static boolean isStoryPreviewImageTintColorCurrent(Story story, int baseColor, String paletteTintMode) {
        return story != null
                && story.previewImageTintColorLoaded
                && baseColor == story.previewImageTintBaseColor
                && SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
                .equals(SettingsUtils.getPaletteTintConfigKey(story.previewImageTintMode))
                && TextUtils.equals(story.previewImageTintSourceUrl, story.previewImageUrl);
    }

    public static void clearStoryPreviewImageTintColor(Story story) {
        if (story == null) {
            return;
        }

        story.previewImageTintColorLoaded = false;
        story.previewImageTintSourceUrl = null;
        story.previewImageTintBaseColor = Color.TRANSPARENT;
        story.previewImageTintMode = null;
    }

    private static void setStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            int baseColor,
            String paletteTintMode,
            int tintColor) {
        story.previewImageTintColor = tintColor;
        story.previewImageTintColorLoaded = true;
        story.previewImageTintSourceUrl = imageUrl;
        story.previewImageTintBaseColor = baseColor;
        story.previewImageTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
        story.previewImageLoadFailed = false;
    }

    private static boolean setCurrentStoryPreviewImageTintColor(
            Story story,
            String imageUrl,
            int baseColor,
            String paletteTintMode,
            int tintColor) {
        if (!TextUtils.equals(story.previewImageUrl, imageUrl)) {
            return false;
        }

        setStoryPreviewImageTintColor(story, imageUrl, baseColor, paletteTintMode, tintColor);
        return true;
    }

    private static Palette.Swatch chooseCardTintSwatch(Palette palette, String paletteTintMode) {
        switch (SettingsUtils.sanitizePaletteTintMode(paletteTintMode)) {
            case SettingsUtils.PALETTE_TINT_VIBRANT:
                return firstSwatch(
                        palette.getVibrantSwatch(),
                        palette.getLightVibrantSwatch(),
                        palette.getDarkVibrantSwatch(),
                        palette.getDominantSwatch(),
                        palette.getMutedSwatch(),
                        palette.getLightMutedSwatch(),
                        palette.getDarkMutedSwatch());
            case SettingsUtils.PALETTE_TINT_DOMINANT:
                return firstSwatch(
                        palette.getDominantSwatch(),
                        palette.getMutedSwatch(),
                        palette.getVibrantSwatch(),
                        palette.getLightMutedSwatch(),
                        palette.getLightVibrantSwatch(),
                        palette.getDarkMutedSwatch(),
                        palette.getDarkVibrantSwatch());
            case SettingsUtils.PALETTE_TINT_DEFAULT:
            default:
                return firstSwatch(
                        palette.getMutedSwatch(),
                        palette.getLightMutedSwatch(),
                        palette.getDarkMutedSwatch(),
                        palette.getVibrantSwatch(),
                        palette.getLightVibrantSwatch(),
                        palette.getDarkVibrantSwatch(),
                        palette.getDominantSwatch());
        }
    }

    private static Palette.Swatch firstSwatch(Palette.Swatch... swatches) {
        for (Palette.Swatch swatch : swatches) {
            if (swatch != null) {
                return swatch;
            }
        }
        return null;
    }

    private static float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
