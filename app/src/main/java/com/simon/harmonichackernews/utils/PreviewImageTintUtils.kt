package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Palette.Swatch
import com.google.android.material.color.MaterialColors
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.clearCachedPreviewImageTintColors
import kotlin.math.max
import kotlin.math.min

object PreviewImageTintUtils {
    private const val TINT_RESULT_VERSION = "worker-v3"
    private const val TINT_SAMPLE_SIZE = 96
    private const val MIN_CHROMATIC_SOURCE_SATURATION = 0.05f
    private const val CARD_TINT_ALPHA_LIGHT = 0.24f
    private const val CARD_TINT_ALPHA_DARK = 0.34f

    fun calculateCardTint(context: Context, drawable: Drawable?): Int {
        val baseColor = getTintBaseColor(context)
        return calculateCardTint(
            baseColor,
            drawable,
            SettingsUtils.getPreferredPaletteTintConfigKey(context)
        )
    }

    fun calculateCardTint(context: Context, drawable: Drawable?, paletteTintMode: String?): Int {
        val baseColor = getTintBaseColor(context)
        return calculateCardTint(baseColor, drawable, paletteTintMode)
    }

    fun getTintBaseColor(context: Context): Int {
        if (context == null) {
            return Color.TRANSPARENT
        }

        return MaterialColors.getColor(
            context,
            R.attr.storyCardBackgroundColor,
            MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT
            )
        )
    }

    fun clearTintColorCaches(context: Context?) {
        StoryPreviewImageMemoryCache.clearTintColors()
        clearCachedPreviewImageTintColors(context)
    }

    @JvmOverloads
    fun calculateCardTint(
        baseColor: Int,
        drawable: Drawable?,
        paletteTintMode: String? = SettingsUtils.PALETTE_TINT_DEFAULT
    ): Int {
        val bitmap = renderDrawableToSampleBitmap(drawable)
        if (bitmap == null) {
            return baseColor
        }

        try {
            return calculateCardTint(baseColor, bitmap, paletteTintMode)
        } finally {
            bitmap.recycle()
        }
    }

    fun calculateCardTint(baseColor: Int, bitmap: Bitmap?, paletteTintMode: String?): Int {
        if (bitmap == null) {
            return baseColor
        }

        val paletteTintConfigKey = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        val palette = Palette.from(bitmap)
            .maximumColorCount(16)
            .generate()
        val swatch = chooseCardTintSwatch(palette, paletteTintConfigKey)
        if (swatch == null) {
            return baseColor
        }

        val darkBase = ColorUtils.calculateLuminance(baseColor) < 0.5
        val tintAlpha = clamp01(
            (if (darkBase) CARD_TINT_ALPHA_DARK else CARD_TINT_ALPHA_LIGHT)
                    * SettingsUtils.getPaletteTintStrengthMultiplier(paletteTintConfigKey)
        )
        val hsl = swatch.getHsl()
        var targetSaturation = clamp01(
            hsl[1] * SettingsUtils.getPaletteTintColorfulnessMultiplier(paletteTintConfigKey)
        )
        if (hsl[1] >= MIN_CHROMATIC_SOURCE_SATURATION
            && (SettingsUtils.getPaletteTintColorfulness(paletteTintConfigKey)
                    >= SettingsUtils.DEFAULT_PALETTE_TINT_COLORFULNESS)
        ) {
            targetSaturation = max(0.25f, targetSaturation)
        }
        val targetLuminance = clamp(
            (if (darkBase) 0.42f else 0.66f) + SettingsUtils.getPaletteTintToneOffset(
                paletteTintConfigKey
            ),
            0.05f,
            0.95f
        )
        val tintColor =
            ColorUtils.HSLToColor(floatArrayOf(hsl[0], targetSaturation, targetLuminance))
        return ColorUtils.blendARGB(baseColor, tintColor, tintAlpha)
    }

    fun updateStoryPreviewImageTintColor(
        story: Story?,
        drawable: Drawable?,
        baseColor: Int
    ): Boolean {
        return updateStoryPreviewImageTintColor(
            story,
            if (story == null) null else story.previewImageUrl,
            drawable,
            baseColor,
            SettingsUtils.PALETTE_TINT_DEFAULT
        )
    }

    @JvmOverloads
    fun updateStoryPreviewImageTintColor(
        story: Story?,
        imageUrl: String?,
        drawable: Drawable?,
        baseColor: Int,
        paletteTintMode: String? = SettingsUtils.PALETTE_TINT_DEFAULT
    ): Boolean {
        if (story == null || drawable == null || TextUtils.isEmpty(imageUrl)) {
            return false
        }

        val safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        val cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
            story.id,
            imageUrl,
            baseColor
        )
        if (cachedTintColor != null) {
            return setCurrentStoryPreviewImageTintColor(
                story,
                imageUrl,
                baseColor,
                safePaletteTintMode,
                cachedTintColor
            )
        }

        try {
            val tintColor = calculateCardTint(baseColor, drawable, safePaletteTintMode)
            StoryPreviewImageMemoryCache.putTintColor(story.id, imageUrl, baseColor, tintColor)
            return setCurrentStoryPreviewImageTintColor(
                story,
                imageUrl,
                baseColor,
                safePaletteTintMode,
                tintColor
            )
        } catch (e: RuntimeException) {
            if (TextUtils.equals(story.previewImageUrl, imageUrl)) {
                clearStoryPreviewImageTintColor(story)
            }
            return false
        }
    }

    @JvmOverloads
    fun syncStoryPreviewImageTintColorFromCache(
        story: Story?,
        baseColor: Int,
        paletteTintMode: String? = SettingsUtils.PALETTE_TINT_DEFAULT
    ): Boolean {
        if (story == null || TextUtils.isEmpty(story.previewImageUrl)) {
            return false
        }

        val safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        if (isStoryPreviewImageTintColorCurrent(story, baseColor, safePaletteTintMode)) {
            return true
        }

        val cachedTintColor = StoryPreviewImageMemoryCache.getTintColor(
            story.id,
            story.previewImageUrl,
            baseColor
        )
        if (cachedTintColor == null) {
            return false
        }

        setStoryPreviewImageTintColor(
            story,
            story.previewImageUrl,
            baseColor,
            safePaletteTintMode,
            cachedTintColor
        )
        return true
    }

    fun applyCachedStoryPreviewImageTintColor(
        story: Story?,
        imageUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int
    ): Boolean {
        if (story == null || TextUtils.isEmpty(imageUrl)) {
            return false
        }

        val safePaletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        StoryPreviewImageMemoryCache.putTintColor(story.id, imageUrl, baseColor, tintColor)
        return setCurrentStoryPreviewImageTintColor(
            story,
            imageUrl,
            baseColor,
            safePaletteTintMode,
            tintColor
        )
    }

    fun isStoryPreviewImageTintColorCurrent(story: Story?, baseColor: Int): Boolean {
        return isStoryPreviewImageTintColorCurrent(
            story,
            baseColor,
            SettingsUtils.PALETTE_TINT_DEFAULT
        )
    }

    fun isStoryPreviewImageTintColorCurrent(
        story: Story?,
        baseColor: Int,
        paletteTintMode: String?
    ): Boolean {
        return story != null && story.previewImageTintColorLoaded
                && baseColor == story.previewImageTintBaseColor
                && isTintModeCurrent(story.previewImageTintMode, paletteTintMode)
                && TextUtils.equals(story.previewImageTintSourceUrl, story.previewImageUrl)
    }

    fun isTintModeCurrent(storedMode: String?, paletteTintMode: String?): Boolean =
        storedMode == storedTintMode(paletteTintMode)

    fun storedTintMode(paletteTintMode: String?): String =
        SettingsUtils.getPaletteTintConfigKey(paletteTintMode) + ":" + TINT_RESULT_VERSION

    fun clearStoryPreviewImageTintColor(story: Story?) {
        if (story == null) {
            return
        }

        story.previewImageTintColorLoaded = false
        story.previewImageTintSourceUrl = null
        story.previewImageTintBaseColor = Color.TRANSPARENT
        story.previewImageTintMode = null
    }

    private fun setStoryPreviewImageTintColor(
        story: Story,
        imageUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int
    ) {
        story.previewImageTintColor = tintColor
        story.previewImageTintColorLoaded = true
        story.previewImageTintSourceUrl = imageUrl
        story.previewImageTintBaseColor = baseColor
        story.previewImageTintMode = storedTintMode(paletteTintMode)
        story.previewImageLoadFailed = false
    }

    private fun setCurrentStoryPreviewImageTintColor(
        story: Story,
        imageUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        tintColor: Int
    ): Boolean {
        if (!TextUtils.equals(story.previewImageUrl, imageUrl)) {
            return false
        }

        setStoryPreviewImageTintColor(story, imageUrl, baseColor, paletteTintMode, tintColor)
        return true
    }

    private fun chooseCardTintSwatch(palette: Palette, paletteTintMode: String?): Swatch? {
        when (SettingsUtils.sanitizePaletteTintMode(paletteTintMode)) {
            SettingsUtils.PALETTE_TINT_VIBRANT -> return firstSwatch(
                palette.getVibrantSwatch(),
                palette.getLightVibrantSwatch(),
                palette.getDarkVibrantSwatch(),
                palette.getDominantSwatch(),
                palette.getMutedSwatch(),
                palette.getLightMutedSwatch(),
                palette.getDarkMutedSwatch()
            )

            SettingsUtils.PALETTE_TINT_DOMINANT -> return firstSwatch(
                palette.getDominantSwatch(),
                palette.getMutedSwatch(),
                palette.getVibrantSwatch(),
                palette.getLightMutedSwatch(),
                palette.getLightVibrantSwatch(),
                palette.getDarkMutedSwatch(),
                palette.getDarkVibrantSwatch()
            )

            SettingsUtils.PALETTE_TINT_DEFAULT -> return firstSwatch(
                palette.getMutedSwatch(),
                palette.getLightMutedSwatch(),
                palette.getDarkMutedSwatch(),
                palette.getVibrantSwatch(),
                palette.getLightVibrantSwatch(),
                palette.getDarkVibrantSwatch(),
                palette.getDominantSwatch()
            )

            else -> return firstSwatch(
                palette.getMutedSwatch(),
                palette.getLightMutedSwatch(),
                palette.getDarkMutedSwatch(),
                palette.getVibrantSwatch(),
                palette.getLightVibrantSwatch(),
                palette.getDarkVibrantSwatch(),
                palette.getDominantSwatch()
            )
        }
    }

    private fun firstSwatch(vararg swatches: Swatch?): Swatch? {
        for (swatch in swatches) {
            if (swatch != null) {
                return swatch
            }
        }
        return null
    }

    private fun clamp01(value: Float): Float {
        return clamp(value, 0f, 1f)
    }

    private fun clamp(value: Float, min: Float, max: Float): Float {
        return max(min, min(max, value))
    }

    fun renderDrawableToSampleBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) {
            return null
        }

        val width = max(1, drawable.getIntrinsicWidth())
        val height = max(1, drawable.getIntrinsicHeight())
        val scale = min(TINT_SAMPLE_SIZE.toFloat() / width, TINT_SAMPLE_SIZE.toFloat() / height)
        val sampleWidth = max(1, Math.round(width * scale))
        val sampleHeight = max(1, Math.round(height * scale))

        val bitmap = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val oldBounds = Rect(drawable.getBounds())
        try {
            drawable.setBounds(0, 0, sampleWidth, sampleHeight)
            drawable.draw(canvas)
        } catch (e: RuntimeException) {
            bitmap.recycle()
            throw e
        } finally {
            drawable.setBounds(oldBounds)
        }
        return bitmap
    }
}
