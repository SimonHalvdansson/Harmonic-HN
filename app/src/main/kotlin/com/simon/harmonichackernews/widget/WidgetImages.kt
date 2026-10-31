package com.simon.harmonichackernews.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground

/** Image-dependent work retained across theme and tint-setting changes. */
internal data class WidgetImages(
    val image: Bitmap?,
    val palette: PreviewTintPalette?,
    val favicon: Bitmap?,
) {
    fun visual(colors: WidgetColors, tintConfig: String): WidgetVisual {
        fun tint(theme: ColorScheme) = palette?.let {
            val rawTint = PreviewTintPolicy.calculateCardTint(theme.cardBackground.toArgb(), it, tintConfig)
            PreviewTintPolicy.ensureCardTintContrast(rawTint, theme.pageBackground.toArgb(), tintConfig)
        }
        return WidgetVisual(image, tint(colors.day), favicon, tint(colors.night))
    }
}

/** Prepare only assets the row uses, retaining the preview's exact crop for shared tint sources. */
internal fun prepareWidgetImages(
    context: Context,
    entry: WidgetEntry,
    previewSize: WidgetImageSize,
    faviconSize: WidgetImageSize,
    maxBytes: Long,
    showPreview: Boolean,
    tint: Boolean,
    showFavicon: Boolean,
    decode: (String, WidgetImageSize, Long) -> Bitmap? = ::decodeWidgetBitmap,
): WidgetImages {
    val image = if (showPreview || (tint && entry.tintPath == entry.imagePath)) {
        entry.imagePath?.let { decode(it, previewSize, maxBytes) }
    } else null
    val tintImage = when {
        !tint -> null
        entry.tintPath == entry.imagePath -> image
        else -> entry.tintPath?.let { decode(it, widgetTintImageSize, maxBytes) }
    }
    val palette = tintImage?.let { bitmap ->
        val sample = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
        val pixels = IntArray(48 * 48)
        sample.getPixels(pixels, 0, 48, 0, 0, 48, 48)
        HarmonicPaletteExtractor(pixels.size).extract(pixels).toPreviewTintPalette()
    }
    val favicon = if (showFavicon) {
        entry.faviconPath?.let { decode(it, faviconSize, maxBytes) }
            ?.roundedWidgetFavicon(context)?.withinWidgetBitmapBudget(maxBytes)?.forWidget()
    } else null
    return WidgetImages(image?.takeIf { showPreview }?.forWidget(), palette, favicon)
}
