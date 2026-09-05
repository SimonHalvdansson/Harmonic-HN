package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.unit.IntSize
import com.simon.harmonichackernews.palette.HarmonicPalette
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.settings.PreviewTintPalette
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

/** Settings samples retain their original 112² area limit and nearest-neighbor scaling. */
@Composable
internal fun rememberResourceTintPalette(resource: DrawableResource): PreviewTintPalette? {
    val environment = rememberResourceEnvironment()
    var palette by remember(resource, environment) { mutableStateOf<PreviewTintPalette?>(null) }
    LaunchedEffect(resource, environment) {
        palette = try {
            paletteExtractionRunner.run {
                getDrawableResourceBytes(environment, resource).decodeToImageBitmap()
                    .extractResourcePalette().toPreviewTintPalette()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }
    return palette
}

internal fun resourcePaletteSampleDimensions(width: Int, height: Int): IntSize {
    require(width > 0 && height > 0)
    val area = width.toLong() * height
    if (area <= 112 * 112) return IntSize(width, height)
    val scale = sqrt((112 * 112).toDouble() / area)
    return IntSize(ceil(width * scale).toInt().coerceAtLeast(1), ceil(height * scale).toInt().coerceAtLeast(1))
}

/** Called on a worker; resource dialogs are infrequent and use a workspace sized to their sample. */
internal fun ImageBitmap.extractResourcePalette(): HarmonicPalette {
    val dimensions = resourcePaletteSampleDimensions(width, height)
    val sample = if (dimensions.width == width && dimensions.height == height) this
        else scalePaletteResource(dimensions.width, dimensions.height)
    val pixels = IntArray(sample.width * sample.height)
    sample.readPixels(pixels)
    return HarmonicPaletteExtractor(pixels.size).extract(pixels)
}

internal expect fun ImageBitmap.scalePaletteResource(width: Int, height: Int): ImageBitmap
