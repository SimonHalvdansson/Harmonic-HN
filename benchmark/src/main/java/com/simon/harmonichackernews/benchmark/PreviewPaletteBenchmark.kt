@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asImage
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.content.BitmapPaletteExtractor
import com.simon.harmonichackernews.ui.content.calculateTint
import com.simon.harmonichackernews.ui.content.extractPreviewPaletteTint
import com.simon.harmonichackernews.ui.content.toPreviewPaletteSampleBitmap
import com.simon.harmonichackernews.ui.content.toPainterPaletteSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Fixed reference outputs captured before dependency removal, including every sampled pixel. */
@RunWith(AndroidJUnit4::class)
class PreviewPaletteEquivalenceTest {
    @Test fun sampledPixelsSwatchesAndTintsMatchGoldens(): Unit = runBlocking {
        val extractor = BitmapPaletteExtractor(4)
        for ((name, source) in previewPaletteFixtures()) {
            val image = source.asImage()
            for (density in listOf(Density(1f), Density(2.75f, 1.3f))) {
                for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    val painterSample = BitmapPainter(source.asImageBitmap()).toPainterPaletteSample(density, direction)
                    val sample = withContext(Dispatchers.Default) { checkNotNull(image.toPreviewPaletteSampleBitmap()) }
                    val label = "$name/$density/$direction"
                    assertEquals("$label width", sample.width, painterSample.width)
                    assertEquals("$label height", sample.height, painterSample.height)
                    assertArrayEquals(label, sample.pixels(), painterSample.pixels())
                    val palette = extractor.extract(sample)
                    assertEquals(label, paletteGoldens.getValue("preview/$name"), paletteGolden(sample.pixels(), palette))
                    for (base in listOf(0xfff8f7f4.toInt(), 0xff151515.toInt())) {
                        for (mode in listOf("default", "vibrant", "dominant")) {
                            for (settings in listOf("100|110|0", "0|0|-20", "200|200|20")) {
                                val config = "$mode|$settings"
                                val expected = PreviewTintPolicy.calculateCardTint(base, palette.toPreviewTintPalette(), config)
                                assertEquals("$label/$config preview", expected,
                                    withContext(Dispatchers.Main) { image.extractPreviewPaletteTint(base, config) })
                                assertEquals("$label/$config painter", expected,
                                    withContext(Dispatchers.Default) { painterSample.calculateTint(base, config) })
                            }
                        }
                    }
                }
            }
            source.recycle()
        }
    }

    @Test fun nonShareableBitmapUsesPainterFallback() {
        val source = loadPreviewFixture("palette1.webp")
        assertNull(source.asImage(shareable = false).toPreviewPaletteSampleBitmap())
        source.recycle()
    }
}

/** Every iteration bypasses tint caches and exercises the current production sampler/extractor. */
@RunWith(Parameterized::class)
class PreviewPaletteBenchmark(private val fixture: String) {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val source = loadPreviewFixture(fixture)
    private val image = source.asImage()
    private val painter = BitmapPainter(source.asImageBitmap())
    private var sink: Any? = null

    @Test fun painterSampling() = benchmarkRule.measureRepeated {
        sink = runBlocking { painter.toPainterPaletteSample(Density(2.75f), LayoutDirection.Ltr) }
    }

    @Test fun bitmapSampling() = benchmarkRule.measureRepeated {
        sink = runBlocking { image.toPreviewPaletteSampleBitmap() }
    }

    @Test fun bitmapColdExtraction() = benchmarkRule.measureRepeated {
        sink = runBlocking {
            withContext(Dispatchers.Main) { image.extractPreviewPaletteTint(0xfff8f7f4.toInt(), "default|100|110|0") }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf(
            arrayOf("palette1.webp"), arrayOf("palette3.webp"), arrayOf("web_preview.webp"),
        )
    }
}

internal fun ImageBitmap.pixels() = IntArray(width * height).also { readPixels(it) }

internal fun loadPreviewFixture(name: String): Bitmap =
    InstrumentationRegistry.getInstrumentation().context.assets.open(
        "composeResources/com.simon.harmonichackernews.resources/drawable/$name",
    ).use { checkNotNull(BitmapFactory.decodeStream(it)) }

internal fun previewPaletteFixtures(): Sequence<Pair<String, Bitmap>> = sequence {
    for (name in listOf("palette1.webp", "palette2.webp", "palette3.webp", "palette4.webp",
        "palette5.webp", "web_preview.webp", "quanta.png", "library_logo_kotlin.png")) {
        yield(name to loadPreviewFixture(name))
    }
    for ((width, height) in listOf(1 to 1, 1 to 173, 173 to 1, 32 to 32, 96 to 96,
        101 to 67, 67 to 101, 641 to 479, 1920 to 1080)) {
        for (alpha in listOf(false, true)) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height) { index ->
                val x = index % width
                val y = index / width
                val a = if (alpha) (x * 17 + y * 7) and 255 else 255
                (a shl 24) or (((x * 13) and 255) shl 16) or
                    (((y * 19) and 255) shl 8) or ((x * 3 + y * 5) and 255)
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            yield("${width}x$height/alpha=$alpha" to bitmap)
        }
    }
    for (config in listOf(Bitmap.Config.RGB_565, Bitmap.Config.RGBA_F16)) {
        val source = loadPreviewFixture("palette1.webp")
        yield(config.name to source.copy(config, false))
        source.recycle()
    }
    val wide = Bitmap.createBitmap(101, 67, Bitmap.Config.RGBA_F16, true,
        ColorSpace.get(ColorSpace.Named.DISPLAY_P3))
    android.graphics.Canvas(wide).drawColor(android.graphics.Color.rgb(0.8f, 0.2f, 0.3f))
    yield("display-p3" to wide)
}
