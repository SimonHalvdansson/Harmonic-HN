@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.benchmark

import android.util.Log
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import com.simon.harmonichackernews.palette.HarmonicPalette
import androidx.compose.ui.graphics.asImageBitmap
import com.simon.harmonichackernews.ui.content.extractResourcePalette
import com.simon.harmonichackernews.palette.HarmonicPaletteExtractor
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.ui.content.toPreviewPaletteSampleBitmap
import com.simon.harmonichackernews.ui.content.extractPreviewPaletteTint
import com.simon.harmonichackernews.ui.content.BitmapPaletteExtractor
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Run this class alone in a fresh instrumentation process to include lookup initialization. */
@RunWith(AndroidJUnit4::class)
class HarmonicPaletteFirstUseTest {
    @Test fun firstUse(): Unit = runBlocking {
        val pixels = checkNotNull(loadPreviewFixture("palette1.webp").asImage()
            .toPreviewPaletteSampleBitmap()).pixels()
        lateinit var palette: HarmonicPalette
        val ownNs = measureNanoTime { palette = HarmonicPaletteExtractor().extract(pixels) }
        assertEquals(paletteGoldens.getValue("preview/palette1.webp"), paletteGolden(pixels, palette))
        Log.i("HarmonicPalette", "firstUse own=${ownNs / 1e6}ms")
    }
}

@RunWith(AndroidJUnit4::class)
class HarmonicPaletteEquivalenceTest {
    @Test fun cancelledCallersDoNotLoseWorkspaces(): Unit = runBlocking {
        val sample = checkNotNull(loadPreviewFixture("palette1.webp").asImage().toPreviewPaletteSampleBitmap())
        val pool = BitmapPaletteExtractor(1)
        val expected = pool.extract(sample)
        repeat(10) {
            val jobs = List(100) { async(Dispatchers.Default) { pool.extract(sample) } }
            jobs.forEach { it.cancel() }
            jobs.forEach { it.cancelAndJoin() }
            withTimeout(5000) { assertEquals(expected, pool.extract(sample)) }
        }
    }

    @Test fun concurrentProductionExtractionsKeepWorkspacesIsolated(): Unit = runBlocking {
        val sources = listOf("palette1.webp", "palette3.webp", "web_preview.webp", "library_logo_kotlin.png")
            .map { loadPreviewFixture(it).asImage() }
        val expected = sources.map { image ->
            val sample = checkNotNull(image.toPreviewPaletteSampleBitmap())
            PreviewTintPolicy.calculateCardTint(0xfff8f7f4.toInt(),
                BitmapPaletteExtractor(1).extract(sample).toPreviewTintPalette(), "default|100|110|0")
        }
        repeat(3) {
            List(200) { index ->
                async(Dispatchers.Default) {
                    val fixture = index % sources.size
                    assertEquals(expected[fixture], sources[fixture]
                        .extractPreviewPaletteTint(0xfff8f7f4.toInt(), "default|100|110|0"))
                }
            }.awaitAll()
        }
    }

    @Test fun settingsResourcePalettesMatchGoldens() {
        for (name in listOf("palette1.webp", "palette2.webp", "palette3.webp",
            "palette4.webp", "palette5.webp", "web_preview.webp")) {
            val bitmap = loadPreviewFixture(name)
            assertEquals(name, paletteGoldens.getValue("resource/$name"),
                paletteGolden(null, bitmap.asImageBitmap().extractResourcePalette()))
            bitmap.recycle()
        }
    }

}

/** Current production path, including dispatch, sampling, readback, and tint selection. */
@RunWith(Parameterized::class)
class HarmonicPalettePipelineBenchmark(private val fixture: String) {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val image = loadPreviewFixture(fixture).asImage()
    private var sink: Any? = null

    @Test fun harmonicColdExtraction() = benchmarkRule.measureRepeated {
        sink = runBlocking {
            withContext(Dispatchers.Main) {
                image.extractPreviewPaletteTint(0xfff8f7f4.toInt(), "default|100|110|0")
            }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf("palette1.webp", "palette3.webp",
            "web_preview.webp", "library_logo_kotlin.png").map { arrayOf(it) }
    }
}

/** Quantizer microbenchmark on pre-sampled pixels with no result cache. */
@RunWith(Parameterized::class)
class HarmonicPaletteBenchmark(private val fixture: String) {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val pixels = if (fixture == "noise") {
        val random = Random(123)
        IntArray(96 * 96) { random.nextInt() }
    } else runBlocking {
        checkNotNull(loadPreviewFixture(fixture).asImage().toPreviewPaletteSampleBitmap()).pixels()
    }
    private val extractor = HarmonicPaletteExtractor()
    private var sink: Any? = null

    @Test fun harmonicQuantizer() = benchmarkRule.measureRepeated {
        sink = extractor.extract(pixels)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf("palette1.webp", "palette3.webp",
            "web_preview.webp", "library_logo_kotlin.png", "noise").map { arrayOf(it) }
    }
}
