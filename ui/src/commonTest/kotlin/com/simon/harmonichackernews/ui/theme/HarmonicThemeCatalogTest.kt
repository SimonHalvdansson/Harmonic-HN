package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import com.simon.harmonichackernews.settings.ColorSchemePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HarmonicThemeCatalogTest {
    @Test
    fun cardsAreLighterThanThePageAcrossAllSchemesAndModes() {
        for (option in ColorSchemeCatalog.options) for (dark in listOf(false, true)) for (style in ColorSchemeStyle.entries) {
            val colors = HarmonicThemeCatalog.scheme(option.value, dark, style).colorScheme
            assertTrue(
                colors.cardBackground.luminance() > colors.pageBackground.luminance(),
                "${option.value}/$dark/$style: cards must be lighter than the page",
            )
            if (dark) {
                assertEquals(colors.surface, colors.pageBackground)
                assertEquals(colors.surfaceContainer, colors.cardBackground)
            } else {
                assertEquals(colors.surfaceContainer, colors.pageBackground)
                assertEquals(colors.surfaceBright, colors.cardBackground)
                assertTrue(colors.cardBackground.luminance() > colors.surfaceContainerLow.luminance())
            }
        }
    }

    @Test
    fun presetsChangeEveryColorFamilyAndNeutralSurfacesInBothModes() {
        for (dark in listOf(false, true)) {
            val schemes = ColorSchemeCatalog.options.filter { it.value in listOf("orange", "blue", "violet", "teal", "rose", "green", "amber", "slate") }
                .map { HarmonicThemeCatalog.scheme(it.value, dark).colorScheme }
            for (role in listOf<(androidx.compose.material3.ColorScheme) -> Color>(
                { it.primary }, { it.secondary }, { it.tertiary }, { it.surface },
                { it.surfaceContainerLow }, { it.outlineVariant },
            )) {
                assertEquals(schemes.size, schemes.map(role).distinct().size)
            }
            for (scheme in schemes) {
                assertNotEquals(scheme.primary, scheme.secondary)
                assertNotEquals(scheme.secondary, scheme.tertiary)
            }
        }
    }

    @Test
    fun allThemesUseReadableMaterialRolePairs() {
        for (option in ColorSchemeCatalog.options) for (dark in listOf(false, true)) for (style in ColorSchemeStyle.entries) {
            val scheme = HarmonicThemeCatalog.scheme(option.value, dark, style).colorScheme
            val pairs = listOf(
                scheme.primary to scheme.onPrimary, scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondary to scheme.onSecondary, scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.tertiary to scheme.onTertiary, scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.error to scheme.onError, scheme.errorContainer to scheme.onErrorContainer,
                scheme.surface to scheme.onSurface, scheme.surfaceContainerLow to scheme.onSurfaceVariant,
                scheme.pageBackground to scheme.onSurface, scheme.pageBackground to scheme.onSurfaceVariant,
                scheme.cardBackground to scheme.onSurface, scheme.cardBackground to scheme.onSurfaceVariant,
                scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
                scheme.inverseSurface to scheme.inverseOnSurface,
            )
            for ((background, foreground) in pairs) {
                val contrast = (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
                    (minOf(background.luminance(), foreground.luminance()) + 0.05f)
                assertTrue(contrast >= 4.5f, "${option.value}/$dark/$style: $background / $foreground = $contrast")
            }
        }
    }

    @Test
    fun nonMaterialThemesIgnoreMaterialColorSelection() {
        for (theme in listOf("light", "dark", "white", "amoled", "gray", "hacker", "hacker_news")) {
            val base = HarmonicThemeCatalog.resolve(theme, false).colorScheme
            for (option in ColorSchemeCatalog.options) {
                // ColorScheme uses referential equality; curated schemes can return fresh copies.
                assertEquals(
                    base.colors(),
                    HarmonicThemeCatalog.resolve(theme, false, option.value).colorScheme.colors(),
                    "$theme should ignore material color ${option.value}",
                )
            }
        }
    }

    @Test
    fun legacyMaterialIdsResolveToTheConsolidatedTheme() {
        for (dark in listOf(false, true)) {
            assertEquals(dark, HarmonicThemeCatalog.resolve(ThemePreferences.MATERIAL_FIXED_AUTO, dark).dark)
            assertEquals(
                HarmonicThemeCatalog.resolve(ThemePreferences.DEFAULT, dark).colorScheme,
                HarmonicThemeCatalog.resolve(ThemePreferences.MATERIAL_FIXED_AUTO, dark).colorScheme,
            )
        }
    }

    @Test
    fun readerUsesPageBackgroundTextLinkAndDividerRolesFromTheSameScheme() {
        val scheme = HarmonicThemeCatalog.scheme("blue", false).colorScheme.copy(
            surfaceContainer = Color(0xFFABCDEF), onSurface = Color(0xFF234567),
            primary = Color(0xFF345678), outlineVariant = Color(0xFF456789),
        )
        val reader = ReaderModeThemeFactory.create(scheme, true, null, 16)
        assertEquals("#ABCDEF", reader.backgroundColor)
        assertEquals("#234567", reader.textColor)
        assertEquals("#345678", reader.linkColor)
        assertEquals("#456789", reader.dividerColor)
    }
    @Test
    fun rainbowUsesGraySurfacesAndEachRecipeHasItsOwnColorFamilies() {
        for (base in ColorSchemePreferences.generatedValues) for (dark in listOf(false, true)) {
            val neutral = HarmonicThemeCatalog.scheme(base, dark, ColorSchemeStyle.NeutralSurfaces).colorScheme
            for (color in listOf(neutral.surface, neutral.surfaceDim, neutral.surfaceBright,
                neutral.surfaceContainerLowest, neutral.surfaceContainerLow, neutral.surfaceContainer,
                neutral.surfaceContainerHigh, neutral.surfaceContainerHighest, neutral.onSurface,
                neutral.onSurfaceVariant, neutral.outline, neutral.outlineVariant)) {
                assertEquals(color.red, color.green, "$base/$dark: $color")
                assertEquals(color.green, color.blue, "$base/$dark: $color")
            }
            val balanced = HarmonicThemeCatalog.scheme(base, dark, ColorSchemeStyle.Balanced).colorScheme
            val vibrant = HarmonicThemeCatalog.scheme(base, dark, ColorSchemeStyle.Vibrant).colorScheme
            assertNotEquals(balanced.secondary, vibrant.secondary)
            assertNotEquals(balanced.tertiary, vibrant.tertiary)
            assertNotEquals(neutral.surfaceContainer, balanced.surfaceContainer)
            assertNotEquals(balanced.surfaceContainer, vibrant.surfaceContainer)
        }
    }

    @Test
    fun dynamicFallbackIgnoresStyles() {
        for (option in ColorSchemeCatalog.options.filterNot { ColorSchemePreferences.supportsStyle(it.value) }) {
            for (dark in listOf(false, true)) for (style in ColorSchemeStyle.entries) {
                assertEquals(HarmonicThemeCatalog.scheme(option.value, dark).colorScheme,
                    HarmonicThemeCatalog.scheme(option.value, dark, style).colorScheme)
            }
        }
    }

    @Test
    fun curatedSchemesHaveDistinctStylesWithoutLosingPureAndGraySurfaces() {
        for (base in listOf("classic", "pure", "hacker_news", "hacker", "gray")) for (dark in listOf(false, true)) {
            val schemes = ColorSchemeStyle.entries.map { HarmonicThemeCatalog.scheme(base, dark, it).colorScheme }
            assertEquals(3, schemes.map { listOf(it.primary, it.secondary, it.tertiary, it.surfaceContainer) }.distinct().size,
                "$base/$dark must offer three distinct styles")
            if (base == "pure" || base == "gray") {
                assertEquals(1, schemes.map { it.pageBackground }.distinct().size)
            }
        }
    }

    @Test
    fun hackerKeepsItsGreenTextInEveryStyle() {
        for (dark in listOf(false, true)) {
            val balanced = HarmonicThemeCatalog.scheme("hacker", dark).colorScheme
            for (style in ColorSchemeStyle.entries) {
                val colors = HarmonicThemeCatalog.scheme("hacker", dark, style).colorScheme
                assertEquals(balanced.onSurface, colors.onSurface)
                assertEquals(balanced.onSurfaceVariant, colors.onSurfaceVariant)
                assertEquals(balanced.inverseOnSurface, colors.inverseOnSurface)
                for (text in listOf(colors.onSurface, colors.onSurfaceVariant)) {
                    assertTrue(text.green > text.red && text.green > text.blue, "$dark/$style must keep green text")
                }
            }
        }
    }

    @Test
    fun hnBalancedUsesWebsiteOrangeCreamAndBlack() {
        val scheme = HarmonicThemeCatalog.scheme("hacker_news", false).colorScheme
        assertEquals("HN", ColorSchemeCatalog.label("hacker_news"))
        assertEquals(Color(0xFFF6F6EF), scheme.pageBackground)
        assertEquals(Color(0xFFFF6600), scheme.primaryContainer)
        assertEquals(Color.Black, scheme.onPrimaryContainer)
        assertEquals(Color.Black, scheme.onSurface)
        assertEquals(Color.White, scheme.cardBackground)
    }

    @Test
    fun blueRecipesMatchMaterialColorUtilitiesReferenceRoles() {
        // MCU 0.3.0, source #365FB5, standard contrast. Dark primary can converge at the gamut
        // boundary even when recipes differ; secondary/tertiary and neutral roles still differ.
        val expected = listOf(
            listOf(0xFF385BA9, 0xFF585E71, 0xFF725572, 0xFFF9F9F9, 0xFF1B1B1B, 0xFFE2E2E2, 0xFF474747),
            listOf(0xFFB1C6FF, 0xFFC0C6DC, 0xFFE0BBDD, 0xFF131313, 0xFFE2E2E2, 0xFF353535, 0xFFC6C6C6),
            listOf(0xFF475D92, 0xFF585E71, 0xFF725572, 0xFFFAF8FF, 0xFF1A1B21, 0xFFE2E2E9, 0xFF44464F),
            listOf(0xFFB1C6FF, 0xFFC0C6DC, 0xFFE0BBDD, 0xFF121318, 0xFFE2E2E9, 0xFF33343A, 0xFFC5C6D0),
            listOf(0xFF0057CD, 0xFF5B5B7E, 0xFF645788, 0xFFFAF8FF, 0xFF181B25, 0xFFE0E2EF, 0xFF424654),
            listOf(0xFFB1C6FF, 0xFFC3C3EB, 0xFFCFBEF7, 0xFF10131C, 0xFFE0E2EF, 0xFF31343F, 0xFFC2C6D6),
        )
        ColorSchemeStyle.entries.forEachIndexed { index, style ->
            for (dark in listOf(false, true)) {
                val scheme = HarmonicThemeCatalog.scheme("blue", dark, style).colorScheme
                assertEquals(expected[index * 2 + if (dark) 1 else 0].map { Color(it) },
                    listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surface,
                        scheme.onSurface, scheme.surfaceContainerHighest, scheme.onSurfaceVariant))
            }
        }
    }

}
