package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.WindowManager
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.ui.widget.WidgetDimensions
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal fun widgetWidth(context: Context, widgetId: Int): Int = widgetWidth(
    AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId),
    context.resources.configuration.orientation,
)

/** Single-size RemoteViews need the launcher's width for the current orientation. */
@Suppress("DEPRECATION")
internal fun widgetWidth(options: Bundle, orientation: Int): Int {
    val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
    val sizes = if (Build.VERSION.SDK_INT >= 33) {
        options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
    } else if (Build.VERSION.SDK_INT >= 31) {
        options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    } else null
    val widths = sizes.orEmpty().filter {
        it.width.isFinite() && it.width > 0 && it.height.isFinite() && it.height > 0
    }.map { it.width }
    if (widths.isNotEmpty()) {
        return (if (landscape) widths.max() else widths.min()).roundToInt().coerceAtLeast(1)
    }
    val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }
    val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 }
    return if (landscape) maxWidth ?: minWidth ?: 280 else minWidth ?: maxWidth ?: 280
}

internal fun widgetBitmapBudget(context: Context, imageCount: Int): Long {
    val bounds = if (Build.VERSION.SDK_INT >= 30) {
        context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
    } else null
    val metrics = context.resources.displayMetrics
    return widgetBitmapBudget(
        bounds?.width()?.takeIf { it > 0 } ?: metrics.widthPixels,
        bounds?.height()?.takeIf { it > 0 } ?: metrics.heightPixels,
        imageCount,
    )
}

/** Reserve half the estimated 6 * display pixels cap for resources and display changes. */
internal fun widgetBitmapBudget(widthPx: Int, heightPx: Int, imageCount: Int): Long =
    3L * widthPx.coerceAtLeast(0) * heightPx.coerceAtLeast(0) / imageCount.coerceAtLeast(1)

internal data class WidgetImageSize(val widthPx: Int, val heightPx: Int) {
    fun withinBudget(maxBytes: Long): WidgetImageSize? {
        if (maxBytes < 4) return null
        val scale = minOf(1.0, sqrt(maxBytes.toDouble() / (4.0 * widthPx * heightPx)))
        return WidgetImageSize((widthPx * scale).toInt().coerceAtLeast(1), (heightPx * scale).toInt().coerceAtLeast(1))
    }
}

internal val widgetTintImageSize = WidgetImageSize(48, 48)

internal fun widgetPreviewImageSize(mode: StoryPreviewMode, density: Float): WidgetImageSize {
    val (width, height) = when (mode) {
        StoryPreviewMode.MEDIUM -> WidgetDimensions.mediumImageWidth to WidgetDimensions.mediumImageHeight
        StoryPreviewMode.SMALL, StoryPreviewMode.LARGE -> WidgetDimensions.smallImageWidth to WidgetDimensions.smallImageHeight
        StoryPreviewMode.OFF -> return widgetTintImageSize
    }
    return WidgetImageSize(ceil(width.value * density).toInt().coerceAtLeast(1), ceil(height.value * density).toInt().coerceAtLeast(1))
}

internal fun widgetFaviconImageSize(density: Float): WidgetImageSize {
    val size = ceil(WidgetDimensions.faviconSize.value * density).toInt().coerceAtLeast(1)
    return WidgetImageSize(size, size)
}

/** Bound old cached files too, before decoding their pixels into memory. */
internal fun decodeWidgetBitmap(path: String, targetSize: WidgetImageSize, maxBytes: Long): Bitmap? {
    val target = targetSize.withinBudget(maxBytes) ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (true) {
        val width = ((bounds.outWidth.toLong() + sample - 1) / sample).coerceAtLeast(1)
        val height = ((bounds.outHeight.toLong() + sample - 1) / sample).coerceAtLeast(1)
        // Keep enough pixels for center-crop without decoding extreme banners at full size.
        val oversized = (width / 2 >= target.widthPx && height / 2 >= target.heightPx) ||
            width > target.widthPx * 2L || height > target.heightPx * 2L
        if (!oversized && width * height * 4 <= maxBytes) break
        if (sample > Int.MAX_VALUE / 2) return null
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })?.forWidgetImage(target, maxBytes)
}

/** Match ContentScale.Crop while avoiding enlargement of low-resolution sources. */
internal fun Bitmap.forWidgetImage(targetSize: WidgetImageSize, maxBytes: Long): Bitmap? {
    val target = targetSize.withinBudget(maxBytes) ?: return null
    val sourceScale = minOf(1.0, width.toDouble() / target.widthPx, height.toDouble() / target.heightPx)
    val outputWidth = (target.widthPx * sourceScale).toInt().coerceAtLeast(1)
    val outputHeight = (target.heightPx * sourceScale).toInt().coerceAtLeast(1)
    if (width == outputWidth && height == outputHeight) return withinWidgetBitmapBudget(maxBytes)
    val scale = maxOf(outputWidth.toFloat() / width, outputHeight.toFloat() / height)
    val left = (outputWidth - width * scale) / 2f
    val top = (outputHeight - height * scale) / 2f
    return Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawBitmap(this, null, RectF(left, top, left + width * scale, top + height * scale),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }.withinWidgetBitmapBudget(maxBytes)
}

/** Check actual allocations, including favicon rounding and decoder row padding. */
internal fun Bitmap.withinWidgetBitmapBudget(maxBytes: Long): Bitmap? {
    if (maxBytes < 4) return null
    var bitmap = this
    while (bitmap.allocationByteCount.toLong() > maxBytes) {
        if (bitmap.width == 1 && bitmap.height == 1) return null
        val scale = sqrt(maxBytes.toDouble() / bitmap.allocationByteCount)
        bitmap = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
    return bitmap
}
