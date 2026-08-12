package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.kmpalette.extensions.painter.rememberPainterPaletteState
import com.kmpalette.palette.graphics.Palette
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlin.math.max
import kotlin.math.min

private const val PaletteSampleSize = 96

/** Shared palette extraction for Coil and Compose resource painters. */
@Composable
fun rememberPainterPaletteTint(
    painter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
): Int? {
    val sampledPainter = remember(painter) { painter?.let(::PaletteSamplePainter) }
    val paletteState = rememberPainterPaletteState(
        cacheSize = 0,
    ) {
        maximumColorCount(16)
    }
    var tint by remember(painter, baseColorArgb, paletteTintConfigKey, enabled) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(sampledPainter, baseColorArgb, paletteTintConfigKey, enabled) {
        if (!enabled || sampledPainter == null) {
            tint = null
            return@LaunchedEffect
        }
        paletteState.generate(sampledPainter)
        tint = paletteState.palette?.let { palette ->
            PreviewTintPolicy.calculateCardTint(
                baseColorArgb,
                palette.toPreviewTintPalette(),
                paletteTintConfigKey,
            )
        }
    }
    return tint
}

/**
 * Gives KMPalette a bounded-size [Painter] so its built-in loader does not first rasterize a large
 * preview at its full intrinsic dimensions. The source painter remains the one returned by Coil.
 */
private class PaletteSamplePainter(
    private val source: Painter,
) : Painter() {
    override val intrinsicSize: Size = source.intrinsicSize.paletteSampleSize()

    override fun DrawScope.onDraw() {
        with(source) { draw(size) }
    }
}

private fun Size.paletteSampleSize(): Size {
    val width = width.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = height.takeIf { it.isFinite() && it > 0f } ?: 1f
    val scale = min(PaletteSampleSize / width, PaletteSampleSize / height)
    return Size(
        width = max(1f, width * scale),
        height = max(1f, height * scale),
    )
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
