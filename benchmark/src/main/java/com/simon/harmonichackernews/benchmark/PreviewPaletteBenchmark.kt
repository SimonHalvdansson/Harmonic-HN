@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.benchmark

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.asImage
import com.kmpalette.from
import com.kmpalette.loader.PainterLoader
import com.kmpalette.palette.graphics.Palette
import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import com.simon.harmonichackernews.ui.content.extractPreviewPaletteTint
import com.simon.harmonichackernews.ui.content.toPreviewPaletteSampleBitmap
import kotlin.math.max
import kotlin.math.min
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

/** Pixel-exact comparison against the existing KMPalette PainterLoader, not a second sampler copy. */
@RunWith(AndroidJUnit4::class)
class PreviewPaletteEquivalenceTest {
    @Test
    fun sampledPixelsSwatchesAndTintsMatchPainter(): Unit = runBlocking {
        var fixtureCount = 0
        var tintCount = 0
        for ((name, source) in previewPaletteFixtures()) {
            val image = source.asImage()
            for (density in listOf(Density(1f), Density(2.75f, 1.3f))) {
                for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                    val old = withContext(Dispatchers.Main) {
                        PainterLoader(density, direction).load(ReferencePreviewPainter(source))
                    }
                    val new = withContext(Dispatchers.Default) {
                        checkNotNull(image.toPreviewPaletteSampleBitmap())
                    }
                    val label = "$name/$density/$direction"
                    assertEquals("$label width", old.width, new.width)
                    assertEquals("$label height", old.height, new.height)
                    assertArrayEquals(label, old.pixels(), new.pixels())
                    val oldPalette = Palette.from(old).maximumColorCount(16).generate()
                    val newPalette = Palette.from(new).maximumColorCount(16).generate()
                    assertEquals(label, oldPalette.swatches.map { it.rgb to it.population },
                        newPalette.swatches.map { it.rgb to it.population })
                    assertEquals(label, oldPalette.previewSwatches(), newPalette.previewSwatches())
                    for (base in listOf(0xfff8f7f4.toInt(), 0xff151515.toInt())) {
                        for (mode in listOf("default", "vibrant", "dominant")) {
                            for (settings in listOf("100|110|0", "0|0|-20", "200|200|20")) {
                                val config = "$mode|$settings"
                                assertEquals("$label/$config/$base",
                                    PreviewTintPolicy.calculateCardTint(base, oldPalette.previewSwatches(), config),
                                    PreviewTintPolicy.calculateCardTint(base, newPalette.previewSwatches(), config))
                                assertEquals("$label/$config/$base production extraction",
                                    PreviewTintPolicy.calculateCardTint(base, oldPalette.previewSwatches(), config),
                                    withContext(Dispatchers.Main) {
                                        image.extractPreviewPaletteTint(base, config)
                                    })
                                tintCount++
                            }
                        }
                    }
                    fixtureCount++
                }
            }
            source.recycle()
        }
        Log.i("PreviewPaletteTest", "PASS $fixtureCount pixel/palette comparisons; $tintCount tint comparisons")
    }

    @Test
    fun nonShareableBitmapUsesPainterFallback() {
        val source = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertNull(source.asImage(shareable = false).toPreviewPaletteSampleBitmap())
        source.recycle()
    }
}

/** Cold extraction bypasses every app/library tint cache on every measured iteration. */
@RunWith(Parameterized::class)
class PreviewPaletteBenchmark(private val fixture: String) {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val source = loadPreviewFixture(fixture)
    private val image = source.asImage()
    private val painter = ReferencePreviewPainter(source)
    private val loader = PainterLoader(Density(2.75f), LayoutDirection.Ltr)
    private var sink: Any? = null

    @Test fun painterSampling() = benchmarkRule.measureRepeated {
        sink = runBlocking { loader.load(painter) }
    }

    @Test fun bitmapSampling() = benchmarkRule.measureRepeated {
        sink = runBlocking { image.toPreviewPaletteSampleBitmap() }
    }

    @Test fun painterColdExtraction() = benchmarkRule.measureRepeated {
        sink = runBlocking {
            withContext(Dispatchers.Main) {
                val sample = loader.load(painter)
                val palette = withContext(Dispatchers.Default) {
                    Palette.from(sample).maximumColorCount(16).generate()
                }
                PreviewTintPolicy.calculateCardTint(0xfff8f7f4.toInt(),
                    palette.previewSwatches(), "default|100|110|0")
            }
        }
    }

    @Test fun bitmapColdExtraction() = benchmarkRule.measureRepeated {
        sink = runBlocking {
            withContext(Dispatchers.Main) {
                image.extractPreviewPaletteTint(0xfff8f7f4.toInt(), "default|100|110|0")
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf(
            arrayOf("palette1.webp"), arrayOf("palette3.webp"), arrayOf("web_preview.webp"),
        )
    }
}

/** Frozen pre-change wrapper: keep rounding inside KMPalette's real PainterLoader. */
private class ReferencePreviewPainter(bitmap: Bitmap) : Painter() {
    private val source = BitmapPainter(bitmap.asImageBitmap())
    override val intrinsicSize: Size = source.intrinsicSize.let {
        val scale = min(96f / it.width, 96f / it.height)
        Size(max(1f, it.width * scale), max(1f, it.height * scale))
    }
    override fun DrawScope.onDraw() { with(source) { draw(size) } }
}

private fun ImageBitmap.pixels() = IntArray(width * height).also { readPixels(it) }

private fun Palette.previewSwatches() = PreviewTintPalette(
    vibrant = vibrantSwatch?.previewSwatch(), lightVibrant = lightVibrantSwatch?.previewSwatch(),
    darkVibrant = darkVibrantSwatch?.previewSwatch(), dominant = dominantSwatch?.previewSwatch(),
    muted = mutedSwatch?.previewSwatch(), lightMuted = lightMutedSwatch?.previewSwatch(),
    darkMuted = darkMutedSwatch?.previewSwatch(),
)
private fun Palette.Swatch.previewSwatch() = PreviewTintSwatch(hsl[0], hsl[1])

private fun loadPreviewFixture(name: String): Bitmap =
    InstrumentationRegistry.getInstrumentation().context.assets.open(
        "composeResources/com.simon.harmonichackernews.resources/drawable/$name",
    ).use { checkNotNull(BitmapFactory.decodeStream(it)) }

private fun previewPaletteFixtures(): Sequence<Pair<String, Bitmap>> = sequence {
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
