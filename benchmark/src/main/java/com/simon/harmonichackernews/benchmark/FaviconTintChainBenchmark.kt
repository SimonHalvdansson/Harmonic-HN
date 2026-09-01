@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.benchmark

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.asImage
import coil3.memory.MemoryCache
import com.kmpalette.from
import com.kmpalette.palette.graphics.Palette
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.network.FaviconUrlBuilder
import com.simon.harmonichackernews.network.StoryPreviewResourceRuntime
import com.simon.harmonichackernews.network.StoryPreviewResourceService
import com.simon.harmonichackernews.network.StoryPreviewResourceRequest
import com.simon.harmonichackernews.network.CachedStoryPreviewResource
import com.simon.harmonichackernews.network.PreviewContent
import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import com.simon.harmonichackernews.settings.StoryPreviewMode
import com.simon.harmonichackernews.settings.StoryPreviewTintState
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.ui.content.PaletteTintCache
import com.simon.harmonichackernews.ui.content.PaletteTintCacheKey
import com.simon.harmonichackernews.ui.content.storyItemUiModel
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Microbenchmarks the CPU stages used by a favicon-backed story-card tint. */
@RunWith(AndroidJUnit4::class)
class FaviconTintChainBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageUrl = "https://news.ycombinator.com/item?id=123"
    private val faviconUrl = "https://www.google.com/s2/favicons?domain=news.ycombinator.com&sz=128"
    private val baseColor = 0xfff8f7f4.toInt()
    private val configKey = "default|100|100|100"
    private val sourceBitmap = createSourceBitmap()
    private val encodedPng = ByteArrayOutputStream().use { output ->
        sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
    private val samplePainter = PaletteSamplePainter(BitmapPainter(sourceBitmap.asImageBitmap()))
    private val density = Density(1f)
    private val sampledBitmap = rasterize(samplePainter, density)
    private val palette = Palette.from(sampledBitmap)
        .maximumColorCount(16)
        .generate()
    private val previewPalette = palette.toPreviewTintPalette()
    private val tint = PreviewTintPolicy.calculateCardTint(baseColor, previewPalette, configKey)

    @Test
    fun urlParseAndFaviconUrlBuild() = benchmarkRule.measureRepeated {
        check(FaviconUrlBuilder.faviconUrl(pageUrl, FaviconUrlBuilder.PROVIDER_GOOGLE).isNotEmpty())
    }

    @Test
    fun coilSoftwareImageRequestBuild() = benchmarkRule.measureRepeated {
        check(
            ImageRequest.Builder(context)
                .data(faviconUrl)
                .allowHardware(false)
                .build()
                .data == faviconUrl,
        )
    }

    @Test
    fun coilMemoryCacheHotRead() {
        val cache = MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()
        val key = MemoryCache.Key(faviconUrl)
        cache[key] = MemoryCache.Value(sourceBitmap.asImage())
        benchmarkRule.measureRepeated {
            check(cache[key] != null)
        }
    }

    @Test
    fun pngDecode128() = benchmarkRule.measureRepeated {
        val decoded = BitmapFactory.decodeByteArray(encodedPng, 0, encodedPng.size)
        check(decoded.width == 128)
        decoded.recycle()
    }

    @Test
    fun painterRasterization96() = benchmarkRule.measureRepeated {
        check(rasterize(samplePainter, density).width == 96)
    }

    @Test
    fun palettePixelExtractionAndQuantization96() = benchmarkRule.measureRepeated {
        check(
            Palette.from(sampledBitmap)
                .maximumColorCount(16)
                .generate()
                .swatches.isNotEmpty(),
        )
    }

    @Test
    fun paletteToSharedSwatches() = benchmarkRule.measureRepeated {
        check(palette.toPreviewTintPalette().dominant != null)
    }

    @Test
    fun tintPolicyCalculation() = benchmarkRule.measureRepeated {
        check(PreviewTintPolicy.calculateCardTint(baseColor, previewPalette, configKey) != 0)
    }

    @Test
    fun persistentTintHotRead() {
        val repository = StoryResourceTintRepository(MemoryKeyValueStore())
        repository.write(
            123,
            StoryResourceTintKind.FAVICON,
            tintState(),
        )
        benchmarkRule.measureRepeated {
            check(
                repository.read(
                    123,
                    StoryResourceTintKind.FAVICON,
                    faviconUrl,
                    baseColor,
                    StoryPreviewTintState.storedMode(configKey),
                )?.tintColorArgb == tint,
            )
        }
    }

    @Test
    fun tintRepositoryWrite() {
        val repository = StoryResourceTintRepository(MemoryKeyValueStore())
        benchmarkRule.measureRepeated {
            repository.write(123, StoryResourceTintKind.FAVICON, tintState())
        }
    }

    @Test
    fun androidSharedPreferencesTintWriteReturn() {
        val preferences = context.getSharedPreferences(
            "favicon_tint_benchmark",
            Context.MODE_PRIVATE,
        )
        preferences.edit().clear().commit()
        val repository = StoryResourceTintRepository(SharedPreferencesKeyValueStore(preferences))
        benchmarkRule.measureRepeated {
            repository.write(123, StoryResourceTintKind.FAVICON, tintState())
        }
    }

    @Test
    fun sharedPaletteTintCacheHotRead() {
        val cache = PaletteTintCache(16)
        val key = PaletteTintCacheKey(faviconUrl, baseColor, configKey)
        runBlocking { cache.getOrExtract(key) { tint } }
        benchmarkRule.measureRepeated {
            check(runBlocking { cache.getOrExtract(key) { error("cache miss") } } == tint)
        }
    }

    @Test
    fun tintedStoryRowModelRebuild() {
        val repository = StoryResourceTintRepository(MemoryKeyValueStore())
        repository.write(123, StoryResourceTintKind.FAVICON, tintState())
        val story = Story("Benchmark story", 123, true, false).apply {
            url = pageUrl
            score = 42
            descendants = 7
            time = 1_700_000_000
        }
        val settings = StoryDisplaySettings(
            showPoints = true,
            compactPoints = false,
            includeTopLevelDomain = true,
            showCommentsCount = true,
            compactView = false,
            thumbnails = false,
            previewImageMode = StoryPreviewMode.OFF,
            borderlessLargePreviewImage = false,
            showSummary = false,
            storyTextSize = 16f,
            showIndex = true,
            compactHeader = false,
            leftAlign = false,
            cardStyle = true,
            tintCardUsingPreview = true,
            paletteTintMode = configKey,
            grayOutClicked = true,
            hotness = 0,
            faviconProvider = FaviconUrlBuilder.PROVIDER_GOOGLE,
            font = "default",
            commentTextSize = 14f,
        )
        benchmarkRule.measureRepeated {
            check(
                storyItemUiModel(
                    story = story,
                    position = 0,
                    settings = settings,
                    previewResource = null,
                    tintBaseColor = baseColor,
                    tintStore = repository,
                    nowMillis = 1_700_000_100_000,
                ).faviconTintArgb == tint,
            )
        }
    }

    @Test
    fun resourceStateUpdate() {
        val runtime = StoryPreviewResourceRuntime(
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            NoOpStoryPreviewResourceService,
        )
        benchmarkRule.measureRepeated {
            check(
                runtime.recordTint(
                    storyId = 123,
                    pageUrl = pageUrl,
                    kind = StoryResourceTintKind.FAVICON,
                    tint = tintState(),
                ),
            )
        }
    }

    @Test
    fun legacyStoryTintMutation() {
        val story = Story().apply {
            id = 123
            url = pageUrl
        }
        benchmarkRule.measureRepeated {
            check(
                StoryPreviewTintState.applyFavicon(
                    story,
                    faviconUrl,
                    baseColor,
                    configKey,
                    tint,
                ),
            )
        }
    }

    private fun tintState() = StoryResourceTintState(
        sourceUrl = faviconUrl,
        baseColorArgb = baseColor,
        paletteConfigKey = StoryPreviewTintState.storedMode(configKey),
        tintColorArgb = tint,
    )

    private fun createSourceBitmap(): Bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        .also { bitmap ->
            val pixels = IntArray(128 * 128) { index ->
                val x = index % 128
                val y = index / 128
                0xff000000.toInt() or (x * 2 shl 16) or (y * 2 shl 8) or ((x + y) and 0xff)
            }
            bitmap.setPixels(pixels, 0, 128, 0, 0, 128, 128)
        }
}

/** Mirrors the production 96 px wrapper in SharedImagePalette.kt. */
private class PaletteSamplePainter(private val source: Painter) : Painter() {
    override val intrinsicSize: Size = source.intrinsicSize.paletteSampleSize()

    override fun DrawScope.onDraw() {
        with(source) { draw(size) }
    }
}

private fun Size.paletteSampleSize(): Size {
    val width = width.takeIf { it.isFinite() && it > 0f } ?: 1f
    val height = height.takeIf { it.isFinite() && it > 0f } ?: 1f
    val scale = min(96f / width, 96f / height)
    return Size(max(1f, width * scale), max(1f, height * scale))
}

/** Mirrors KMPalette's PainterImage.asBitmap, which production calls before Dispatchers.Default. */
private fun rasterize(painter: Painter, density: Density): ImageBitmap {
    val width = painter.intrinsicSize.width.roundToInt()
    val height = painter.intrinsicSize.height.roundToInt()
    val bitmap = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    bitmap.prepareToDraw()
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
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

private fun Palette.Swatch.toPreviewTintSwatch() = PreviewTintSwatch(hsl[0], hsl[1])

private class MemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any?>()
    override fun contains(key: String) = key in values
    override fun keys(): Set<String> = values.keys
    override fun remove(key: String) { values.remove(key) }
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun putString(key: String, value: String?) { values[key] = value }
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
    override fun putFloat(key: String, value: Float) { values[key] = value }
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String) = values[key] as? Set<String> ?: emptySet()
    override fun putStringSet(key: String, value: Set<String>?) { values[key] = value }
}

private class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences,
) : KeyValueStore {
    override fun contains(key: String) = preferences.contains(key)
    override fun keys(): Set<String> = preferences.all.keys
    override fun remove(key: String) { preferences.edit().remove(key).apply() }
    override fun getString(key: String, default: String?) = preferences.getString(key, default)
    override fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }
    override fun getBoolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }
    override fun getInt(key: String, default: Int) = preferences.getInt(key, default)
    override fun putInt(key: String, value: Int) { preferences.edit().putInt(key, value).apply() }
    override fun getFloat(key: String, default: Float) = preferences.getFloat(key, default)
    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).apply()
    }
    override fun getStringSet(key: String) =
        preferences.getStringSet(key, emptySet())?.toSet().orEmpty()
    override fun putStringSet(key: String, value: Set<String>?) {
        preferences.edit().putStringSet(key, value?.toSet()).apply()
    }
}

private object NoOpStoryPreviewResourceService : StoryPreviewResourceService {
    override suspend fun readCached(request: StoryPreviewResourceRequest) =
        CachedStoryPreviewResource(imageUrlResolved = false, imageUrl = null, summary = null)

    override suspend fun load(request: StoryPreviewResourceRequest) = PreviewContent(null, null)
}
