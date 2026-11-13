package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import com.simon.harmonichackernews.settings.ColorSchemePreferences
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorOptimizationEquivalenceTest {
    @Test
    fun contrastSearchKeepsExactColorsAcrossSurfacesAndThresholds() {
        val random = Random(391)
        val colors = listOf(Color.Black, Color.White, Color.Gray, Color.Transparent) +
            List(24) { Color(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat()) } +
            listOf(Color(0.6f, 0.3f, 0.8f, 0.4f, ColorSpaces.DisplayP3))
        val backgrounds = listOf(
            emptyList(), listOf(Color.Black), listOf(Color.White), listOf(Color.Gray),
            listOf(Color.Black, Color.White), listOf(Color(0xFFF6F6EF), Color(0xFFEBEBE2)),
            listOf(Color(0xFF202124), Color(0xFF303135), Color(0xFF414246)),
            listOf(Color(0.2f, 0.3f, 0.1f, 1f, ColorSpaces.DisplayP3)),
        )
        for (color in colors) for (surfaces in backgrounds) {
            for (minimum in listOf(-1f, 0f, 1f, 1.5f, 3f, 4.5f, 7f, 21f, 22f, Float.NaN)) {
                assertEquals(
                    originalContrastingColor(color, surfaces, minimum),
                    contrastingColor(color, surfaces, minimum),
                    "color=$color, surfaces=$surfaces, minimum=$minimum",
                )
            }
        }
    }

    @Test
    fun materialIndicatorsKeepExactPaletteOrderForEveryTheme() {
        for (theme in ColorSchemePreferences.values) for (dark in listOf(false, true)) {
            for (style in ColorSchemeStyle.entries) {
                assertPalette(HarmonicThemeCatalog.scheme(theme, dark, style).colorScheme)
            }
        }
        val base = HarmonicThemeCatalog.scheme("dynamic", false).colorScheme
        val random = Random(612)
        repeat(12) {
            fun color() = Color(random.nextFloat(), random.nextFloat(), random.nextFloat())
            assertPalette(base.copy(
                primary = color(), secondary = color(), tertiary = color(),
                primaryContainer = color(), tertiaryContainer = color(),
            ))
        }
        // Colliding roles exercise alternative selection and its stable first-match tie policy.
        assertPalette(base.copy(
            primary = Color.Gray, secondary = Color.Gray, tertiary = Color.Gray,
            primaryContainer = Color.Gray, tertiaryContainer = Color.Gray,
        ))
    }

    private fun assertPalette(scheme: ColorScheme) = assertEquals(
        originalMaterialColors(scheme),
        CommentDepthPaletteCatalog.colors(CommentDepthPreferences.MATERIAL_YOU, "dynamic", false, scheme),
    )
}

/** Previous algorithms are independent oracles for exact floating-point and tie-breaking output. */
private fun originalContrastingColor(color: Color, backgrounds: List<Color>, minimum: Float): Color {
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

private fun originalMaterialColors(scheme: ColorScheme): List<Color> {
    val backgrounds = listOf(scheme.pageBackground, scheme.cardBackground)
    val roles = listOf(scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.primaryContainer, scheme.tertiaryContainer)
    val palette = mutableListOf<Color>()
    val alternatives by lazy {
        (0 until 36).flatMap { hue ->
            listOf(0.45f, 0.65f).map { saturation ->
                originalContrastingColor(Color.hsl(hue * 10f, saturation, 0.5f), backgrounds, 3f)
            }
        }
    }
    roles.forEach { role ->
        val preferred = originalContrastingColor(role, backgrounds, 3f)
        val distinct = { color: Color -> palette.all { perceptualColorDistance(it, color) >= 22f } }
        palette += if (distinct(preferred)) preferred else {
            alternatives.filter(distinct).minByOrNull { perceptualColorDistance(it, preferred) }
                ?: alternatives.maxBy { candidate -> palette.minOf { perceptualColorDistance(it, candidate) } }
        }
    }
    return palette
}
