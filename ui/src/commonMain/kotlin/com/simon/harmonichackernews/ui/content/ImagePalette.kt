package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import coil3.Image
import com.simon.harmonichackernews.settings.PreviewTintPolicy
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
private const val MaxPaletteTintEntries = 256
private const val MaxConcurrentPaletteExtractions = 4
private val paletteTintCache = PaletteTintCache(MaxPaletteTintEntries)
internal val paletteExtractionRunner = PaletteExtractionRunner(
    maxConcurrentExtractions = MaxConcurrentPaletteExtractions,
)
private val bitmapPaletteExtractor = BitmapPaletteExtractor(MaxConcurrentPaletteExtractions)

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

/** Keeps preview sampling identical to BitmapPainter while moving its work off the UI thread. */
@Composable
internal fun rememberPreviewImagePaletteTint(
    image: Image?,
    fallbackPainter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean,
): Int? = if (image?.supportsOffMainPaletteSampling() == true && fallbackPainter is BitmapPainter) {
    rememberImagePaletteTint(
        image = image,
        baseColorArgb = baseColorArgb,
        paletteTintConfigKey = paletteTintConfigKey,
        enabled = enabled,
        sharedCacheKey = null,
        preview = true,
    )
} else {
    rememberPainterPaletteTint(
        painter = fallbackPainter,
        baseColorArgb = baseColorArgb,
        paletteTintConfigKey = paletteTintConfigKey,
        enabled = enabled,
    )
}

@Composable
private fun rememberImagePaletteTint(
    image: Image,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean,
    sharedCacheKey: String?,
    preview: Boolean = false,
): Int? {
    var tint by remember(image, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey, preview) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(image, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey, preview) {
        if (!enabled) {
            tint = null
            return@LaunchedEffect
        }
        val extract: suspend () -> Int? = {
            if (preview) {
                image.extractPreviewPaletteTint(baseColorArgb, paletteTintConfigKey)
            } else {
                paletteExtractionRunner.run {
                    image.toPaletteSampleBitmap()?.calculateTint(
                        baseColorArgb = baseColorArgb,
                        paletteTintConfigKey = paletteTintConfigKey,
                    )
                }
            }
        }
        tint = if (sharedCacheKey != null) {
            paletteTintCache.getOrExtract(
                PaletteTintCacheKey(sharedCacheKey, baseColorArgb, paletteTintConfigKey),
                extract,
            )
        } else {
            extract()
        }
    }
    return tint
}

internal suspend fun Image.extractPreviewPaletteTint(
    baseColorArgb: Int,
    paletteTintConfigKey: String,
): Int? = try {
    paletteExtractionRunner.run {
        toPreviewPaletteSampleBitmap()?.calculateTint(baseColorArgb, paletteTintConfigKey)
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    // Leave the existing card color in place when extraction fails.
    null
}

/** Common palette extraction for Coil and Compose resource painters. */
@Composable
fun rememberPainterPaletteTint(
    painter: Painter?,
    baseColorArgb: Int,
    paletteTintConfigKey: String,
    enabled: Boolean = true,
    sharedCacheKey: String? = null,
): Int? {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var tint by remember(painter, baseColorArgb, paletteTintConfigKey, enabled, sharedCacheKey) {
        mutableStateOf<Int?>(null)
    }

    LaunchedEffect(
        painter,
        density,
        layoutDirection,
        baseColorArgb,
        paletteTintConfigKey,
        enabled,
        sharedCacheKey,
    ) {
        if (!enabled || painter == null) {
            tint = null
            return@LaunchedEffect
        }
        val extract: suspend () -> Int? = {
            try {
                val sample = painter.toPainterPaletteSample(density, layoutDirection)
                paletteExtractionRunner.run {
                    sample.calculateTint(baseColorArgb, paletteTintConfigKey)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
        tint = if (sharedCacheKey != null) {
            paletteTintCache.getOrExtract(
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

internal suspend fun ImageBitmap.calculateTint(
    baseColorArgb: Int,
    paletteTintConfigKey: String,
): Int {
    val palette = bitmapPaletteExtractor.extract(this)
    return PreviewTintPolicy.calculateCardTint(
        baseColorArgb,
        palette.toPreviewTintPalette(),
        paletteTintConfigKey,
    )
}
