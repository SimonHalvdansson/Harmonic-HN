@file:JvmName("AndroidHarmonicThemeKt")

package com.simon.harmonichackernews.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.ThemeSelection

@Composable
fun HarmonicTheme(
    selection: ThemeSelection? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activeSelection = selection ?: context.harmonicAppComposition.appearance.selection()
    val configuration = LocalConfiguration.current
    val palette = remember(context, configuration, activeSelection) {
        harmonicThemePalette(context, activeSelection)
    }
    HarmonicTheme(palette.colors, palette.colorScheme, palette.dark, content)
}

/** All consumers resolve the same immutable selection, independent of the Activity's theme. */
fun harmonicThemePalette(context: Context, activeSelection: ThemeSelection): HarmonicThemePalette {
    val dynamic = activeSelection.theme in setOf(
        ThemePreferences.DEFAULT, "material_light", "material_dark",
    )
    val palette = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dark = when (activeSelection.theme) {
            "material_dark" -> true
            "material_light" -> false
            else -> activeSelection.dark
        }
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // Preserve the expressive XML theme's container foregrounds and outline. These were
        // fixed roles even in its dynamic variant; replacing them silently changes contrast.
        val compatible = scheme.copy(
            onPrimaryContainer = if (dark) scheme.onPrimaryContainer else Color(0xFF4F378B),
            onSecondaryContainer = if (dark) scheme.onSecondaryContainer else Color(0xFF4A4458),
            outlineVariant = if (dark) Color(0xFF49454F) else Color(0xFFCAC4D0),
        )
        HarmonicThemeCatalog.materialPalette(
            dark = dark,
            scheme = compatible,
            commentCountIndicator = Color(context.getColor(android.R.color.system_accent1_600)),
        )
    } else {
        val theme = if (dynamic) when (activeSelection.theme) {
            "material_dark" -> ThemePreferences.MATERIAL_FIXED_DARK
            "material_light" -> ThemePreferences.MATERIAL_FIXED_LIGHT
            else -> ThemePreferences.MATERIAL_FIXED_AUTO
        } else activeSelection.theme
        HarmonicThemeCatalog.resolve(theme, activeSelection.dark)
    }
    return ThemeAccentCatalog.apply(palette, activeSelection.accentPreset)
}

fun harmonicColors(context: Context): HarmonicColors = harmonicThemePalette(
    context,
    context.harmonicAppComposition.appearance.selection(),
).colors

/** Base color used by story preview tint extraction on Android. */
fun previewTintBaseColor(context: Context): Int = harmonicColors(context).contentCardBackground.toArgb()
