package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import coil3.Image
import com.kmpalette.generatePalette
import com.kmpalette.extensions.painter.rememberPainterPaletteState
import com.kmpalette.palette.graphics.Palette
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

private const val PaletteSampleSize = 96
private const val MaxSharedPaletteTintEntries = 256
private const val MaxConcurrentPaletteExtractions = 4
private val SharedPaletteTintCache = PaletteTintCache(MaxSharedPaletteTintEntries)
private val SharedPaletteExtractionRunner = PaletteExtractionRunner(
    maxConcurrentExtractions = MaxConcurrentPaletteExtractions,
)

/**
 * Extracts a tint directly from a shareable Coil bitmap. Rasterization, palette generation, and
 * tint selection all run behind a bounded background-work gate; Compose receives only the result.
 */
@Composable
internal fun rememberCoilImagePaletteTint(
    image: Image?,
    fallbackPainter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
    sharedCacheKey: String? = null,
): Int? = if (image?.supportsOffMainPaletteSampling() == true) {
    rememberImagePaletteTint(
        image = image,
        baseColorArgb = baseColorArgb,
        paletteTintConfigKey = paletteTintConfigKey,
        enabled = enabled,
        sharedCacheKey = sharedCacheKey,
    )
} else {
    rememberPainterPaletteTint(
        painter = fallbackPainter,
        baseColorArgb = baseColorArgb,
        paletteTintConfigKey = paletteTintConfigKey,
        enabled = enabled,
        sharedCacheKey = sharedCacheKey,
    )
}

@Composable
private fun rememberImagePaletteTint(
    image: Image,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean,
    sharedCacheKey: String?,
): Int? {
    var tint by remember(image, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(image, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey) {
        if (!enabled) {
            tint = null
            return@LaunchedEffect
        }
        val extract: suspend () -> Int? = {
            SharedPaletteExtractionRunner.run {
                image.toPaletteSampleBitmap()?.calculateTint(
                    baseColorArgb = baseColorArgb,
                    paletteTintConfigKey = paletteTintConfigKey,
                )
            }
        }
        tint = if (sharedCacheKey != null) {
            SharedPaletteTintCache.getOrExtract(
                PaletteTintCacheKey(sharedCacheKey, baseColorArgb, paletteTintConfigKey),
                extract,
            )
        } else {
            extract()
        }
    }
    return tint
}

/** Shared palette extraction for Coil and Compose resource painters. */
@Composable
fun rememberPainterPaletteTint(
    painter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
    sharedCacheKey: String? = null,
): Int? {
    val sampledPainter = remember(painter) { painter?.let(::PaletteSamplePainter) }
    val paletteState = rememberPainterPaletteState(
        cacheSize = 0,
    ) {
        maximumColorCount(16)
    }
    var tint by remember(painter, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(
        sampledPainter,
        baseColorArgb,
        paletteTintConfigKey,
        enabled,
        sharedCacheKey,
    ) {
        if (!enabled || sampledPainter == null) {
            tint = null
            return@LaunchedEffect
        }
        val painterToSample = sampledPainter
        val extract: suspend () -> Int? = {
            paletteState.generate(painterToSample)
            paletteState.palette?.let { palette ->
                PreviewTintPolicy.calculateCardTint(
                    baseColorArgb,
                    palette.toPreviewTintPalette(),
                    paletteTintConfigKey,
                )
            }
        }
        tint = if (sharedCacheKey != null) {
            SharedPaletteTintCache.getOrExtract(
                PaletteTintCacheKey(sharedCacheKey, baseColorArgb, paletteTintConfigKey),
                extract,
            )
        } else {
            extract()
        }
    }
    return tint
}

internal data class PaletteTintCacheKey(
    val resourceKey: String,
    val baseColorArgb: Int,
    val paletteTintConfigKey: String,
)

/** Coalesces palette work and retains a bounded set of completed resource tint results. */
internal class PaletteTintCache(
    private val maxEntries: Int,
) {
    private val mutex = Mutex()
    private val completed = LinkedHashMap<PaletteTintCacheKey, Int>()
    private val pending = mutableMapOf<PaletteTintCacheKey, CompletableDeferred<ExtractionResult>>()

    init {
        require(maxEntries > 0)
    }

    suspend fun getOrExtract(
        key: PaletteTintCacheKey,
        extract: suspend () -> Int?,
    ): Int? {
        while (true) {
            when (val lookup = lookup(key)) {
                is Lookup.Completed -> return lookup.tint
                is Lookup.Pending -> {
                    if (!lookup.owner) {
                        when (val result = lookup.result.await()) {
                            is ExtractionResult.Completed -> return result.tint
                            ExtractionResult.Retry -> continue
                        }
                    }

                    try {
                        val tint = extract()
                        mutex.withLock {
                            if (pending[key] === lookup.result) {
                                pending.remove(key)
                                tint?.let { cacheCompleted(key, it) }
                            }
                        }
                        lookup.result.complete(ExtractionResult.Completed(tint))
                        return tint
                    } catch (error: CancellationException) {
                        withContext(NonCancellable) {
                            mutex.withLock {
                                if (pending[key] === lookup.result) pending.remove(key)
                            }
                            lookup.result.complete(ExtractionResult.Retry)
                        }
                        throw error
                    } catch (_: Throwable) {
                        mutex.withLock {
                            if (pending[key] === lookup.result) pending.remove(key)
                        }
                        lookup.result.complete(ExtractionResult.Completed(null))
                        return null
                    }
                }
            }
        }
    }

    private suspend fun lookup(key: PaletteTintCacheKey): Lookup = mutex.withLock {
        completed.remove(key)?.let { tint ->
            completed[key] = tint
            return@withLock Lookup.Completed(tint)
        }
        pending[key]?.let { return@withLock Lookup.Pending(it, owner = false) }
        val result = CompletableDeferred<ExtractionResult>()
        pending[key] = result
        Lookup.Pending(result, owner = true)
    }

    private fun cacheCompleted(key: PaletteTintCacheKey, tint: Int) {
        completed.remove(key)
        completed[key] = tint
        while (completed.size > maxEntries) {
            completed.entries.iterator().let { entries ->
                if (entries.hasNext()) {
                    entries.next()
                    entries.remove()
                }
            }
        }
    }

    private sealed interface Lookup {
        data class Completed(val tint: Int) : Lookup
        data class Pending(
            val result: CompletableDeferred<ExtractionResult>,
            val owner: Boolean,
        ) : Lookup
    }

    private sealed interface ExtractionResult {
        data class Completed(val tint: Int?) : ExtractionResult
        data object Retry : ExtractionResult
    }
}

/** Limits simultaneous image processing so favicon bursts do not saturate every CPU core. */
internal class PaletteExtractionRunner(
    maxConcurrentExtractions: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val semaphore = Semaphore(maxConcurrentExtractions)

    init {
        require(maxConcurrentExtractions > 0)
    }

    suspend fun <T> run(block: suspend () -> T): T = withContext(dispatcher) {
        semaphore.withPermit { block() }
    }
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

private fun Image.toPaletteSampleBitmap(): ImageBitmap? {
    val dimensions = paletteSampleDimensions(width, height)
    return toPaletteImageBitmap(dimensions.first, dimensions.second)
}

internal fun paletteSampleDimensions(width: Int, height: Int): Pair<Int, Int> {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val scale = min(PaletteSampleSize.toFloat() / safeWidth, PaletteSampleSize.toFloat() / safeHeight)
    return max(1, (safeWidth * scale).toInt()) to max(1, (safeHeight * scale).toInt())
}

private suspend fun ImageBitmap.calculateTint(
    baseColorArgb: Int,
    paletteTintConfigKey: String,
): Int {
    val palette = generatePalette {
        maximumColorCount(16)
    }
    return PreviewTintPolicy.calculateCardTint(
        baseColorArgb,
        palette.toPreviewTintPalette(),
        paletteTintConfigKey,
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
