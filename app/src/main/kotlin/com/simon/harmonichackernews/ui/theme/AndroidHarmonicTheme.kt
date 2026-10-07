@file:JvmName("AndroidHarmonicThemeKt")

package com.simon.harmonichackernews.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.harmonicAppComposition
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
    HarmonicTheme(palette.colorScheme, palette.dark, content)
}

/** System supplies the entire scheme; presets and custom themes are portable. */
fun harmonicThemePalette(context: Context, activeSelection: ThemeSelection): HarmonicThemePalette {
    val dark = activeSelection.dark
    return if (
        activeSelection.colorScheme == com.simon.harmonichackernews.settings.ColorSchemePreferences.DYNAMIC &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ) {
        HarmonicThemePalette(dark, if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context))
    } else {
        HarmonicThemeCatalog.scheme(activeSelection.colorScheme, dark, activeSelection.colorStyle)
    }
}

fun harmonicColorScheme(context: Context): ColorScheme = harmonicThemePalette(
    context,
    context.harmonicAppComposition.appearance.selection(),
).colorScheme

/** Base color used by story preview tint extraction on Android. */
fun previewTintBaseColor(context: Context): Int = harmonicColorScheme(context).surfaceContainerLow.toArgb()
