package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import com.kmpalette.palette.graphics.Palette
import com.google.android.material.color.MaterialColors
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryPreviewImageLoader
import com.simon.harmonichackernews.network.StoryPreviewImageLoader.clearCachedPreviewImageTintColors
import com.simon.harmonichackernews.settings.PaletteTintPreferences
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlin.math.max
import kotlin.math.min

object PreviewImageTintUtils {
    private const val TINT_SAMPLE_SIZE = 96

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

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val palette = Palette.Builder(pixels, bitmap.width, bitmap.height)
            .maximumColorCount(16)
            .generate()
        return calculateCardTint(baseColor, palette, paletteTintMode)
    }

    fun calculateCardTint(baseColor: Int, palette: Palette?, paletteTintMode: String?): Int {
        return PreviewTintPolicy.calculateCardTint(
            baseColor,
            palette?.toPreviewTintPalette(),
            paletteTintMode,
        )
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

        val safePaletteTintMode = PaletteTintPreferences.normalizeConfigKey(paletteTintMode)
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

        val safePaletteTintMode = PaletteTintPreferences.normalizeConfigKey(paletteTintMode)
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

        val safePaletteTintMode = PaletteTintPreferences.normalizeConfigKey(paletteTintMode)
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
        PreviewTintPolicy.storedMode(paletteTintMode)

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

    private fun Palette.toPreviewTintPalette(): PreviewTintPalette = PreviewTintPalette(
        vibrant = vibrantSwatch?.toPreviewTintSwatch(),
        lightVibrant = lightVibrantSwatch?.toPreviewTintSwatch(),
        darkVibrant = darkVibrantSwatch?.toPreviewTintSwatch(),
        dominant = dominantSwatch?.toPreviewTintSwatch(),
        muted = mutedSwatch?.toPreviewTintSwatch(),
        lightMuted = lightMutedSwatch?.toPreviewTintSwatch(),
        darkMuted = darkMutedSwatch?.toPreviewTintSwatch(),
    )

    private fun Palette.Swatch.toPreviewTintSwatch(): PreviewTintSwatch =
        PreviewTintSwatch(hue = hsl[0], saturation = hsl[1])

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
