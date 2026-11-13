@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.ui.comments.streamingTextFadeRange
import com.simon.harmonichackernews.ui.theme.CommentDepthPaletteCatalog
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.colorContrast
import com.simon.harmonichackernews.ui.theme.contrastingColor
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.theme.perceptualColorDistance
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Algorithms from 1bfa9b10 paired with production in one non-debuggable APK. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ThemeAndStreamingAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    @Volatile private var colorSink: Long = 0

    private val backgrounds = listOf(Color(0xFFF8F4FF), Color(0xFFF0ECF5))
    private val foreground = Color(0xFFD0BCD8)
    private val lightScheme = HarmonicThemeCatalog.scheme("dynamic", false).colorScheme
    private val darkScheme = HarmonicThemeCatalog.scheme("dynamic", true).colorScheme
    private val prefix4k = "Measured improvements preserve the reader's existing behavior. ".repeat(70) // 4,410 chars
    private val prefix16k = prefix4k.repeat(4) // 17,640 chars
    private val summary4k = prefix4k + "**unfinished"
    private val summary16k = prefix16k + "**unfinished"
    private val next4k = prefix4k + "finished bold tail"
    private val next16k = prefix16k + "finished bold tail"
    private val appendOnly4k = prefix4k + "The next streamed sentence."

    @Before fun verifyEquivalence() {
        check(legacyContrastingColor(foreground, backgrounds, 3f) ==
            contrastingColor(foreground, backgrounds, 3f))
        check(legacyContrastingColor(Color.Black, backgrounds, 3f) ==
            contrastingColor(Color.Black, backgrounds, 3f))
        for (scheme in listOf(lightScheme, darkScheme)) {
            check(legacyMaterialPalette(scheme) == materialPalette(scheme))
        }
        for ((previous, current) in listOf(summary4k to next4k, summary16k to next16k, prefix4k to appendOnly4k)) {
            check(legacyFadeRange(previous, current, true, true) ==
                streamingTextFadeRange(previous, current, true, true))
        }
    }

    @Test fun contrastTwoSurfacesBefore() = benchmarkRule.measureRepeated {
        colorSink = legacyContrastingColor(foreground, backgrounds, 3f).value.toLong()
    }
    @Test fun contrastTwoSurfacesOptimized() = benchmarkRule.measureRepeated {
        colorSink = contrastingColor(foreground, backgrounds, 3f).value.toLong()
    }
    @Test fun contrastAlreadyPassesBefore() = benchmarkRule.measureRepeated {
        colorSink = legacyContrastingColor(Color.Black, backgrounds, 3f).value.toLong()
    }
    @Test fun contrastAlreadyPassesOptimized() = benchmarkRule.measureRepeated {
        colorSink = contrastingColor(Color.Black, backgrounds, 3f).value.toLong()
    }
    @Test fun materialPaletteLightBefore() = benchmarkRule.measureRepeated {
        sink = legacyMaterialPalette(lightScheme)
    }
    @Test fun materialPaletteLightOptimized() = benchmarkRule.measureRepeated {
        sink = materialPalette(lightScheme)
    }
    @Test fun materialPaletteDarkBefore() = benchmarkRule.measureRepeated {
        sink = legacyMaterialPalette(darkScheme)
    }
    @Test fun materialPaletteDarkOptimized() = benchmarkRule.measureRepeated {
        sink = materialPalette(darkScheme)
    }
    @Test fun streamingPrefix4kBefore() = benchmarkRule.measureRepeated {
        sink = legacyFadeRange(summary4k, next4k, true, true)
    }
    @Test fun streamingPrefix4kOptimized() = benchmarkRule.measureRepeated {
        sink = streamingTextFadeRange(summary4k, next4k, true, true)
    }
    @Test fun streamingPrefix16kBefore() = benchmarkRule.measureRepeated {
        sink = legacyFadeRange(summary16k, next16k, true, true)
    }
    @Test fun streamingPrefix16kOptimized() = benchmarkRule.measureRepeated {
        sink = streamingTextFadeRange(summary16k, next16k, true, true)
    }
    @Test fun streamingAppendOnly4kBefore() = benchmarkRule.measureRepeated {
        sink = legacyFadeRange(prefix4k, appendOnly4k, true, true)
    }
    @Test fun streamingAppendOnly4kOptimized() = benchmarkRule.measureRepeated {
        sink = streamingTextFadeRange(prefix4k, appendOnly4k, true, true)
    }

    private fun materialPalette(scheme: ColorScheme): List<Color> =
        CommentDepthPaletteCatalog.colors(CommentDepthPreferences.MATERIAL_YOU, "dynamic", false, scheme)
}

private fun legacyContrastingColor(color: Color, backgrounds: List<Color>, minimum: Float): Color {
    fun passes(candidate: Color) = backgrounds.all { colorContrast(candidate, it) >= minimum }
    val opaque = color.copy(alpha = 1f)
    if (passes(opaque)) return opaque
    val endpoint = listOf(Color.Black, Color.White).maxBy { candidate ->
        backgrounds.minOf { colorContrast(candidate, it) }
    }
    var low = 0f
    var high = 1f
    repeat(16) {
        val middle = (low + high) / 2f
        if (passes(lerp(opaque, endpoint, middle))) high = middle else low = middle
    }
    return lerp(opaque, endpoint, high)
}

// Uses the optimized contrast helper on both sides, isolating palette conversion and selection.
private fun legacyMaterialPalette(scheme: ColorScheme): List<Color> {
    val backgrounds = listOf(scheme.pageBackground, scheme.cardBackground)
    val roles = listOf(scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.primaryContainer, scheme.tertiaryContainer)
    val palette = mutableListOf<Color>()
    val alternatives by lazy {
        (0 until 36).flatMap { hue ->
            listOf(0.45f, 0.65f).map { saturation ->
                contrastingColor(Color.hsl(hue * 10f, saturation, 0.5f), backgrounds, 3f)
            }
        }
    }
    roles.forEach { role ->
        val preferred = contrastingColor(role, backgrounds, 3f)
        val distinct = { color: Color -> palette.all { perceptualColorDistance(it, color) >= 22f } }
        palette += if (distinct(preferred)) preferred else {
            alternatives.filter(distinct).minByOrNull { perceptualColorDistance(it, preferred) }
                ?: alternatives.maxBy { candidate -> palette.minOf { perceptualColorDistance(it, candidate) } }
        }
    }
    return palette
}

private fun legacyFadeRange(previous: String, current: String, streaming: Boolean, wasStreaming: Boolean): IntRange? {
    if ((!streaming && !wasStreaming) || current.isEmpty() || current == previous) return null
    val commonPrefixLength = previous.commonPrefixWith(current).length
    if (commonPrefixLength >= current.length) return null
    return commonPrefixLength until current.length
}
