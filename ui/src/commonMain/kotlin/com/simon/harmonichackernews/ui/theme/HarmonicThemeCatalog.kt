package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.ColorSchemePreferences
import com.simon.harmonichackernews.settings.ColorSchemeStyle

data class HarmonicThemePalette(val dark: Boolean, val colorScheme: ColorScheme)

/** Complete light/dark Material palettes for every named color scheme. */
object HarmonicThemeCatalog {
    fun scheme(id: String, dark: Boolean, style: ColorSchemeStyle = ColorSchemeStyle.Balanced): HarmonicThemePalette = HarmonicThemePalette(dark,
        when (ColorSchemePreferences.sanitize(id)) {
            "classic" -> styledCurated(if (dark) "rose" else "teal", dark, style,
                if (dark) classicDark else classicLight)
            "pure" -> styledCurated(if (dark) "rose" else "teal", dark, style,
                if (dark) black else white, preserveSurfaces = true)
            "hacker_news" -> hackerNewsScheme(dark, style)
            "hacker" -> hackerScheme(dark, style)
            "gray" -> styledCurated("rose", dark, style, if (dark) gray else grayLight,
                preserveSurfaces = true)
            in ColorSchemePreferences.generatedValues -> MaterialColorSchemes.generated(id, dark, style)
            // Defined fallback for Dynamic on platforms without Android system colors.
            else -> if (dark) MaterialColorSchemes.violetDark else MaterialColorSchemes.violetLight
        },
    )

    private fun hackerScheme(dark: Boolean, style: ColorSchemeStyle): ColorScheme {
        val identity = if (dark) hacker else hackerLight
        return styledCurated("hackerGreen", dark, style, identity).copy(
            onSurface = identity.onSurface,
            onSurfaceVariant = identity.onSurfaceVariant,
            onBackground = identity.onSurface,
            inverseOnSurface = identity.inverseOnSurface,
        )
    }

    private fun styledCurated(
        base: String,
        dark: Boolean,
        style: ColorSchemeStyle,
        balanced: ColorScheme,
        preserveSurfaces: Boolean = false,
    ): ColorScheme {
        if (style == ColorSchemeStyle.Balanced) return balanced
        if (preserveSurfaces && style == ColorSchemeStyle.NeutralSurfaces) {
            val monochrome = if (dark) MaterialColorSchemes.monochromeDark else MaterialColorSchemes.monochromeLight
            return monochrome.withSurfacesFrom(balanced)
        }
        val generated = MaterialColorSchemes.generated(base, dark, style)
        // Pure keeps its white/black canvas; Gray keeps its neutral canvas. Their accent roles
        // still use each recipe, including the secondary and tertiary color families.
        return if (preserveSurfaces) generated.withSurfacesFrom(balanced) else generated
    }

    private fun hackerNewsScheme(dark: Boolean, style: ColorSchemeStyle): ColorScheme {
        val generated = MaterialColorSchemes.generated("hn", dark, style)
        val surfaces = if (style == ColorSchemeStyle.Balanced) {
            generated.withSurfacesFrom(if (dark) hackerNewsDark else hackerNews)
        } else generated
        // HN's orange header becomes the filled action color. Keep the generated primary
        // foreground readable for links and icons instead of putting bright orange text on cream.
        return surfaces.copy(
            primaryContainer = Color(0xFFFF6600),
            onPrimaryContainer = Color.Black,
        )
    }

    private fun ColorScheme.withSurfacesFrom(source: ColorScheme): ColorScheme = copy(
        surface = source.surface, surfaceDim = source.surfaceDim, surfaceBright = source.surfaceBright,
        surfaceContainerLowest = source.surfaceContainerLowest, surfaceContainerLow = source.surfaceContainerLow,
        surfaceContainer = source.surfaceContainer, surfaceContainerHigh = source.surfaceContainerHigh,
        surfaceContainerHighest = source.surfaceContainerHighest,
        onSurface = source.onSurface, onSurfaceVariant = source.onSurfaceVariant,
        surfaceVariant = source.surfaceVariant, outline = source.outline, outlineVariant = source.outlineVariant,
        inverseSurface = source.inverseSurface, inverseOnSurface = source.inverseOnSurface,
        background = source.surface, onBackground = source.onSurface,
    )

    /** Compatibility resolver for old theme IDs used in saved data and existing fixtures. */
    fun resolve(
        theme: String?,
        systemDark: Boolean,
        materialColorPreset: String = ThemePreferences.COLOR_SYSTEM,
    ): HarmonicThemePalette {
        val normalized = ThemePreferences.canonicalTheme(theme)
        val dark = if (ThemePreferences.isAutomatic(normalized)) systemDark else ThemePreferences.isDark(normalized)
        return scheme(ColorSchemePreferences.fromLegacyTheme(normalized, materialColorPreset), dark)
    }

    // Custom schemes use the same complete Material roles. Only palette values differ;
    // adding color to a component never needs a scheme-specific token.
    private val classicLight = MaterialColorSchemes.tealLight.copy(
        surface = Color(0xFFF6F6EF),
        surfaceDim = Color(0xFFDDDCD2),
        surfaceBright = Color(0xFFF6F6EF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF0F0E8),
        surfaceContainer = Color(0xFFEBEBE2),
        surfaceContainerHigh = Color(0xFFE5E5DC),
        surfaceContainerHighest = Color(0xFFDFDFD6),
        onSurface = Color(0xFF2F2F2F),
        onSurfaceVariant = Color(0xFF4A4A4A),
        outline = Color(0xFF77776F),
        outlineVariant = Color(0xFFCACAC0),
    )
    private val classicDark = MaterialColorSchemes.roseDark.copy(
        surface = Color(0xFF1A1C26),
        surfaceDim = Color(0xFF1A1C26),
        surfaceBright = Color(0xFF3C3E4B),
        surfaceContainerLowest = Color(0xFF10121A),
        surfaceContainerLow = Color(0xFF222431),
        surfaceContainer = Color(0xFF272936),
        surfaceContainerHigh = Color(0xFF313340),
        surfaceContainerHighest = Color(0xFF3C3E4B),
        onSurface = Color(0xFFDFDFE8),
        onSurfaceVariant = Color(0xFFBBBBCC),
        outline = Color(0xFF9090A0),
        outlineVariant = Color(0xFF454654),
    )
    private val gray = classicDark.copy(
        surface = Color(0xFF202124),
        surfaceDim = Color(0xFF202124),
        surfaceBright = Color(0xFF414246),
        surfaceContainerLowest = Color(0xFF17181B),
        surfaceContainerLow = Color(0xFF292A2E),
        surfaceContainer = Color(0xFF303135),
        surfaceContainerHigh = Color(0xFF38393D),
        surfaceContainerHighest = Color(0xFF414246),
    )
    private val black = classicDark.copy(
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceBright = Color(0xFF292929),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0C0C0C),
        surfaceContainer = Color(0xFF141414),
        surfaceContainerHigh = Color(0xFF1F1F1F),
        surfaceContainerHighest = Color(0xFF292929),
        onSurface = Color(0xFFE5E5E5),
        onSurfaceVariant = Color(0xFFC6C6C6),
        outline = Color(0xFF909090),
        outlineVariant = Color(0xFF454545),
    )
    private val white = classicLight.copy(
        surface = Color.White,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFDCDCDC),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF6F6F6),
        surfaceContainer = Color(0xFFF0F0F0),
        surfaceContainerHigh = Color(0xFFEAEAEA),
        surfaceContainerHighest = Color(0xFFE4E4E4),
        outlineVariant = Color(0xFFCACACA),
    )
    private val hackerNews = MaterialColorSchemes.orangeLight.copy(
        surface = Color.White,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFDEDCCD),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFFAFAF5),
        surfaceContainer = Color(0xFFF6F6EF),
        surfaceContainerHigh = Color(0xFFEFEFE5),
        surfaceContainerHighest = Color(0xFFE7E7DC),
        onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF5A5A5A),
        surfaceVariant = Color(0xFFE7E7DC),
        outline = Color(0xFF828282),
        outlineVariant = Color(0xFFD0CEBE),
    )
    private val hacker = black.copy(
        primary = Color(0xFF00FF00), onPrimary = Color.Black,
        primaryContainer = Color(0xFF003300), onPrimaryContainer = Color(0xFF88FF88),
        secondary = Color(0xFF88DD88), onSecondary = Color.Black,
        secondaryContainer = Color(0xFF193319), onSecondaryContainer = Color(0xFFBBFFBB),
        tertiary = Color(0xFFBBDD88), onTertiary = Color.Black,
        tertiaryContainer = Color(0xFF293319), onTertiaryContainer = Color(0xFFDDFFBB),
        surfaceTint = Color(0xFF00FF00),
        onSurface = Color(0xFF00FF00), onSurfaceVariant = Color(0xFF88BB88),
        outline = Color(0xFF558855), outlineVariant = Color(0xFF294429),
        inverseSurface = Color(0xFFCCEECC), inverseOnSurface = Color(0xFF102010),
        inversePrimary = Color(0xFF006600),
        primaryFixed = Color(0xFF88FF88), primaryFixedDim = Color(0xFF00DD00),
        onPrimaryFixed = Color(0xFF001100), onPrimaryFixedVariant = Color(0xFF003300),
        secondaryFixed = Color(0xFFBBFFBB), secondaryFixedDim = Color(0xFF88DD88),
        onSecondaryFixed = Color(0xFF001100), onSecondaryFixedVariant = Color(0xFF193319),
        tertiaryFixed = Color(0xFFDDFFBB), tertiaryFixedDim = Color(0xFFBBDD88),
        onTertiaryFixed = Color(0xFF111100), onTertiaryFixedVariant = Color(0xFF293319),
    )
    private val hackerNewsDark = MaterialColorSchemes.orangeDark.copy(
        surface = Color(0xFF19140F), surfaceDim = Color(0xFF19140F),
        surfaceBright = Color(0xFF413B34), surfaceContainerLowest = Color(0xFF130F0A),
        surfaceContainerLow = Color(0xFF211C16), surfaceContainer = Color(0xFF26211B),
        surfaceContainerHigh = Color(0xFF312B25), surfaceContainerHighest = Color(0xFF3C362F),
    )
    private val hackerLight = MaterialColorSchemes.greenLight.copy(
        primary = Color(0xFF006E00), onPrimary = Color.White,
        primaryContainer = Color(0xFFB4F5A5), onPrimaryContainer = Color(0xFF002200),
        surface = Color(0xFFF4FBEE), surfaceBright = Color(0xFFF4FBEE),
        onSurface = Color(0xFF153815), onSurfaceVariant = Color(0xFF3F523C),
        surfaceTint = Color(0xFF006E00),
    )
    private val grayLight = MaterialColorSchemes.roseLight.copy(
        surface = Color(0xFFF7F7F9), surfaceDim = Color(0xFFD9D9DD),
        surfaceBright = Color(0xFFF7F7F9), surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF1F1F4), surfaceContainer = Color(0xFFEBEBEF),
        surfaceContainerHigh = Color(0xFFE5E5E9), surfaceContainerHighest = Color(0xFFDFDFE3),
        onSurface = Color(0xFF202124), onSurfaceVariant = Color(0xFF47474E),
        outline = Color(0xFF77777F), outlineVariant = Color(0xFFC7C7CF),
    )

}
