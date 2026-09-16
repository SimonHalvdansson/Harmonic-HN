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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment

internal data class ResourcePreview(val image: ImageBitmap, val palette: PreviewTintPalette)

private data class ResourcePreviewKey(val resource: DrawableResource, val environment: ResourceEnvironment)

// Five bundled samples fit without retaining decoded images for every previous configuration.
private val resourcePreviewCache = ResourcePreviewCache<ResourcePreviewKey, ResourcePreview>(maxEntries = 5)

/** Shares a single worker decode between the settings thumbnail and its original 112² palette. */
@Composable
internal fun rememberResourcePreview(resource: DrawableResource): ResourcePreview? {
    val environment = rememberResourceEnvironment()
    val key = remember(resource, environment) { ResourcePreviewKey(resource, environment) }
    var preview by remember(key) { mutableStateOf(resourcePreviewCache[key]) }
    LaunchedEffect(key) {
        preview = loadResourcePreview(key)
    }
    return preview
}

internal suspend fun preloadResourcePreview(resource: DrawableResource, environment: ResourceEnvironment) {
    loadResourcePreview(ResourcePreviewKey(resource, environment))
}

private suspend fun loadResourcePreview(key: ResourcePreviewKey): ResourcePreview? = try {
    resourcePreviewCache[key] ?: paletteExtractionRunner.run {
        resourcePreviewCache.getOrLoad(key) {
            val image = getDrawableResourceBytes(key.environment, key.resource).decodeToImageBitmap()
            val palette = image.extractResourcePalette().toPreviewTintPalette()
            image.prepareToDraw()
            ResourcePreview(image, palette)
        }
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}

@Composable
internal fun rememberResourceTintPalette(resource: DrawableResource): PreviewTintPalette? =
    rememberResourcePreview(resource)?.palette

/** Bounded completed results; concurrent requests share a load, cancelled/failed loads can retry. */
internal class ResourcePreviewCache<K, V : Any>(private val maxEntries: Int) {
    private val mutex = Mutex()
    private val completed = MutableStateFlow<Map<K, V>>(emptyMap())

    init {
        require(maxEntries > 0)
    }

    operator fun get(key: K): V? = completed.value[key]

    suspend fun getOrLoad(key: K, load: suspend () -> V): V = mutex.withLock {
        completed.value[key]?.let { return@withLock it }
        val result = load()
        val entries = LinkedHashMap(completed.value)
        entries[key] = result
        while (entries.size > maxEntries) entries.remove(entries.keys.first())
        completed.value = entries
        result
    }
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
