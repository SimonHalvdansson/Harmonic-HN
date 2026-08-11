package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.Image
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import com.kmpalette.loader.ImageBitmapLoader
import com.kmpalette.palette.graphics.Palette
import com.kmpalette.rememberPaletteState
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PaletteSampleSize = 96

/**
 * Generates a tint directly from Coil's multiplatform [Image]. The image is sampled through
 * Compose graphics and KMPalette; no Android Drawable or Bitmap conversion is involved.
 */
@Composable
fun rememberCoilPaletteTint(
    image: Image?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
): Int? {
    val platformContext = LocalPlatformContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val loader = remember(platformContext, density, layoutDirection) {
        CoilImageBitmapLoader(platformContext, density, layoutDirection)
    }
    val paletteState = rememberPaletteState(
        loader = loader,
        cacheSize = 0,
    ) {
        maximumColorCount(16)
    }
    var tint by remember(image, baseColorArgb, paletteTintConfigKey, enabled) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(image, baseColorArgb, paletteTintConfigKey, enabled) {
        if (!enabled || image == null) {
            tint = null
            return@LaunchedEffect
        }
        paletteState.generate(image)
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

/** Shared palette extraction for Compose resource painters used by settings previews. */
@Composable
fun rememberPainterPaletteTint(
    painter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
): Int? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val loader = remember(density, layoutDirection) {
        SampledPainterBitmapLoader(density, layoutDirection)
    }
    val paletteState = rememberPaletteState(
        loader = loader,
        cacheSize = 0,
    ) {
        maximumColorCount(16)
    }
    var tint by remember(painter, baseColorArgb, paletteTintConfigKey, enabled) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(painter, baseColorArgb, paletteTintConfigKey, enabled) {
        if (!enabled || painter == null) {
            tint = null
            return@LaunchedEffect
        }
        paletteState.generate(painter)
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

private class CoilImageBitmapLoader(
    private val platformContext: PlatformContext,
    private val density: Density,
    private val layoutDirection: LayoutDirection,
) : ImageBitmapLoader<Image> {
    override suspend fun load(input: Image): ImageBitmap = samplePainter(
        painter = input.asPainter(platformContext),
        sourceWidth = input.width,
        sourceHeight = input.height,
        density = density,
        layoutDirection = layoutDirection,
    )
}

private class SampledPainterBitmapLoader(
    private val density: Density,
    private val layoutDirection: LayoutDirection,
) : ImageBitmapLoader<Painter> {
    override suspend fun load(input: Painter): ImageBitmap = samplePainter(
        painter = input,
        sourceWidth = input.intrinsicSize.width.takeIf { it.isFinite() }?.roundToInt() ?: 1,
        sourceHeight = input.intrinsicSize.height.takeIf { it.isFinite() }?.roundToInt() ?: 1,
        density = density,
        layoutDirection = layoutDirection,
    )
}

private fun samplePainter(
    painter: Painter,
    sourceWidth: Int,
    sourceHeight: Int,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val width = max(1, sourceWidth)
    val height = max(1, sourceHeight)
    val scale = min(PaletteSampleSize.toFloat() / width, PaletteSampleSize.toFloat() / height)
    val sampleWidth = max(1, (width * scale).roundToInt())
    val sampleHeight = max(1, (height * scale).roundToInt())
    val bitmap = ImageBitmap(sampleWidth, sampleHeight)
    val size = Size(sampleWidth.toFloat(), sampleHeight.toFloat())
    bitmap.prepareToDraw()
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(bitmap),
        size = size,
    ) {
        with(painter) { draw(size) }
    }
    return bitmap
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
