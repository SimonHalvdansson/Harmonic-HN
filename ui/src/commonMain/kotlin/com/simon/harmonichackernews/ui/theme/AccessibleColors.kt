package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.sqrt

/** Prefer neutral floating-control content, retaining legibility on inverted containers. */
internal val ColorScheme.onFloatingContainer: Color
    get() = if (colorContrast(onSurface, primaryContainer) >= 4.5f) {
        onSurface
    } else {
        onPrimaryContainer
    }

internal fun colorContrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

/** Smallest neutral blend that clears the contrast floor on every supplied surface. */
internal fun contrastingColor(color: Color, backgrounds: List<Color>, minimum: Float): Color {
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

/** CIE76 distance guards against near-identical indicator hues as well as tones. */
internal fun perceptualColorDistance(first: Color, second: Color): Float {
    val a = first.convert(ColorSpaces.CieLab)
    val b = second.convert(ColorSpaces.CieLab)
    val dl = a.red - b.red
    val da = a.green - b.green
    val db = a.blue - b.blue
    return sqrt(dl * dl + da * da + db * db)
}
