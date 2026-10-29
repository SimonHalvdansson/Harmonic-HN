package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.WindowManager
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

/** Bound old cached files too, before decoding their pixels into memory. */
internal fun decodeWidgetBitmap(path: String, maxBytes: Long): Bitmap? {
    if (maxBytes < 4) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (true) {
        val width = ((bounds.outWidth.toLong() + sample - 1) / sample).coerceAtLeast(1)
        val height = ((bounds.outHeight.toLong() + sample - 1) / sample).coerceAtLeast(1)
        if (width <= 160 && height <= 160 && width * height * 4 <= maxBytes) break
        if (sample > Int.MAX_VALUE / 2) return null
        sample *= 2
    }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    })?.withinWidgetBitmapBudget(maxBytes)
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
