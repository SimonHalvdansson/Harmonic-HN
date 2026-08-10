package com.simon.harmonichackernews.settings

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

data class PreviewTintSwatch(
    val hue: Float,
    val saturation: Float,
)

data class PreviewTintPalette(
    val vibrant: PreviewTintSwatch? = null,
    val lightVibrant: PreviewTintSwatch? = null,
    val darkVibrant: PreviewTintSwatch? = null,
    val dominant: PreviewTintSwatch? = null,
    val muted: PreviewTintSwatch? = null,
    val lightMuted: PreviewTintSwatch? = null,
    val darkMuted: PreviewTintSwatch? = null,
)

/** Platform-neutral color policy applied after a platform has extracted palette swatches. */
object PreviewTintPolicy {
    private const val RESULT_VERSION = "worker-v4-kmpalette"
    private const val MIN_CHROMATIC_SOURCE_SATURATION = 0.05f
    private const val CARD_TINT_ALPHA_LIGHT = 0.24f
    private const val CARD_TINT_ALPHA_DARK = 0.34f

    fun calculateCardTint(
        baseColor: Int,
        palette: PreviewTintPalette?,
        modeOrConfigKey: String?,
    ): Int {
        val swatch = chooseSwatch(palette, modeOrConfigKey) ?: return baseColor
        val configKey = PaletteTintPreferences.normalizeConfigKey(modeOrConfigKey)
        val darkBase = luminance(baseColor) < 0.5
        val tintAlpha = clamp01(
            (if (darkBase) CARD_TINT_ALPHA_DARK else CARD_TINT_ALPHA_LIGHT) *
                PaletteTintPreferences.strengthMultiplier(configKey),
        )
        var targetSaturation = clamp01(
            swatch.saturation * PaletteTintPreferences.colorfulnessMultiplier(configKey),
        )
        if (
            swatch.saturation >= MIN_CHROMATIC_SOURCE_SATURATION &&
            PaletteTintPreferences.colorfulness(configKey) >=
            PaletteTintPreferences.DEFAULT_COLORFULNESS
        ) {
            targetSaturation = max(0.25f, targetSaturation)
        }
        val targetLightness = (
            (if (darkBase) 0.42f else 0.66f) + PaletteTintPreferences.toneOffset(configKey)
        ).coerceIn(0.05f, 0.95f)
        val tintColor = hslToColor(swatch.hue, targetSaturation, targetLightness)
        return blendArgb(baseColor, tintColor, tintAlpha)
    }

    fun storedMode(modeOrConfigKey: String?): String =
        "${PaletteTintPreferences.normalizeConfigKey(modeOrConfigKey)}:$RESULT_VERSION"

    private fun chooseSwatch(
        palette: PreviewTintPalette?,
        modeOrConfigKey: String?,
    ): PreviewTintSwatch? {
        if (palette == null) return null
        return when (PaletteTintPreferences.sanitizeMode(modeOrConfigKey)) {
            PaletteTintPreferences.VIBRANT -> firstOf(
                palette.vibrant,
                palette.lightVibrant,
                palette.darkVibrant,
                palette.dominant,
                palette.muted,
                palette.lightMuted,
                palette.darkMuted,
            )

            PaletteTintPreferences.DOMINANT -> firstOf(
                palette.dominant,
                palette.muted,
                palette.vibrant,
                palette.lightMuted,
                palette.lightVibrant,
                palette.darkMuted,
                palette.darkVibrant,
            )

            else -> firstOf(
                palette.muted,
                palette.lightMuted,
                palette.darkMuted,
                palette.vibrant,
                palette.lightVibrant,
                palette.darkVibrant,
                palette.dominant,
            )
        }
    }

    private fun firstOf(vararg swatches: PreviewTintSwatch?): PreviewTintSwatch? =
        swatches.firstOrNull { it != null }

    private fun luminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value < 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val red = linear(color ushr 16 and 0xff)
        val green = linear(color ushr 8 and 0xff)
        val blue = linear(color and 0xff)
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val hueSegment = normalizedHue / 60f
        val secondary = chroma * (1f - kotlin.math.abs(hueSegment % 2f - 1f))
        val (red, green, blue) = when (hueSegment.toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        val match = lightness - chroma / 2f
        return argb(
            alpha = 255,
            red = ((red + match) * 255f).roundToInt(),
            green = ((green + match) * 255f).roundToInt(),
            blue = ((blue + match) * 255f).roundToInt(),
        )
    }

    private fun blendArgb(base: Int, tint: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return argb(
            alpha = ((base ushr 24 and 0xff) * inverse + (tint ushr 24 and 0xff) * ratio).roundToInt(),
            red = ((base ushr 16 and 0xff) * inverse + (tint ushr 16 and 0xff) * ratio).roundToInt(),
            green = ((base ushr 8 and 0xff) * inverse + (tint ushr 8 and 0xff) * ratio).roundToInt(),
            blue = ((base and 0xff) * inverse + (tint and 0xff) * ratio).roundToInt(),
        )
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)

    private fun clamp01(value: Float): Float = min(1f, max(0f, value))
}
