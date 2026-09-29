@file:JvmName("AndroidHarmonicThemeKt")

package com.simon.harmonichackernews.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.utils.AndroidActivityTheme

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

/** Resolve Android attributes and color roles from the same immutable selection. */
fun harmonicThemePalette(context: Context, activeSelection: ThemeSelection): HarmonicThemePalette {
    // The Activity theme is mutable and may lag the appearance flow during configuration changes.
    // Use an isolated context so a new selection cannot be combined with the previous theme's colors.
    val configuration = Configuration(context.resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (activeSelection.dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val themedContext = ContextThemeWrapper(
        context.createConfigurationContext(configuration),
        AndroidActivityTheme.themeResource(activeSelection.theme, activeSelection.dark),
    )
    return resolveAndroidThemePalette(themedContext, activeSelection)
}

private fun resolveAndroidThemePalette(
    context: Context,
    activeSelection: ThemeSelection,
): HarmonicThemePalette {
    val canonical = HarmonicThemeCatalog.resolve(
        theme = activeSelection.theme,
        systemDark = activeSelection.dark,
    )
    val colors = harmonicColors(context, canonical)
    val baseScheme = canonical.colorScheme
    val colorScheme = baseScheme.copy(
        primary = context.colorAttribute(AppCompatR.attr.colorPrimary, baseScheme.primary),
        onPrimary = context.colorAttribute(MaterialR.attr.colorOnPrimary, baseScheme.onPrimary),
        primaryContainer = context.colorAttribute(
            MaterialR.attr.colorPrimaryContainer,
            baseScheme.primaryContainer,
        ),
        onPrimaryContainer = context.colorAttribute(
            MaterialR.attr.colorOnPrimaryContainer,
            baseScheme.onPrimaryContainer,
        ),
        secondary = context.colorAttribute(MaterialR.attr.colorSecondary, baseScheme.secondary),
        onSecondary = context.colorAttribute(
            MaterialR.attr.colorOnSecondary,
            baseScheme.onSecondary,
        ),
        secondaryContainer = colors.secondaryContainer,
        onSecondaryContainer = colors.onSecondaryContainer,
        tertiary = context.colorAttribute(MaterialR.attr.colorTertiary, baseScheme.tertiary),
        onTertiary = context.colorAttribute(
            MaterialR.attr.colorOnTertiary,
            baseScheme.onTertiary,
        ),
        background = colors.background,
        onBackground = colors.onSurface,
        surface = context.colorAttribute(MaterialR.attr.colorSurface, colors.background),
        onSurface = colors.onSurface,
        surfaceContainerLow = context.colorAttribute(
            MaterialR.attr.colorSurfaceContainerLow,
            baseScheme.surfaceContainerLow,
        ),
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHighest,
        surfaceVariant = context.colorAttribute(
            MaterialR.attr.colorSurfaceVariant,
            baseScheme.surfaceVariant,
        ),
        onSurfaceVariant = context.colorAttribute(
            MaterialR.attr.colorOnSurfaceVariant,
            baseScheme.onSurfaceVariant,
        ),
        outline = context.colorAttribute(MaterialR.attr.colorOutline, baseScheme.outline),
        outlineVariant = colors.outlineVariant,
    )

    return ThemeAccentCatalog.apply(
        canonical.copy(colors = colors, colorScheme = colorScheme),
        activeSelection.accentPreset,
    )
}

fun harmonicColors(context: Context): HarmonicColors = harmonicThemePalette(
    context,
    context.harmonicAppComposition.appearance.selection(),
).colors

private fun harmonicColors(
    context: Context,
    canonical: HarmonicThemePalette,
): HarmonicColors {
    val fallback = canonical.colors
    val fallbackScheme = canonical.colorScheme
    val readerModeBackground = context.colorAttribute(
        android.R.attr.colorBackground,
        fallback.readerModeBackground,
    )
    val mutedSurface = context.colorAttribute(
        R.attr.mutedSurfaceColor,
        fallbackScheme.surfaceContainerHigh,
    )
    fun resolveThemeSurface(fallbackColor: Color): Color = when (fallbackColor) {
        fallback.readerModeBackground -> readerModeBackground
        fallback.mutedSurface -> mutedSurface
        else -> fallbackColor
    }
    val onSurface = context.colorAttribute(
        MaterialR.attr.colorOnSurface,
        fallbackScheme.onSurface,
    )
    return HarmonicColors(
        background = resolveThemeSurface(fallback.background),
        readerModeBackground = readerModeBackground,
        accent = context.colorAttribute(
            AppCompatR.attr.colorAccent,
            fallback.accent,
        ),
        onSurface = onSurface,
        textPrimary = onSurface,
        textSecondary = context.colorAttribute(
            R.attr.secondaryTextColor,
            fallbackScheme.onSurfaceVariant,
        ),
        link = context.colorAttribute(android.R.attr.textColorLink, fallback.link),
        surfaceContainerHigh = context.colorAttribute(
            MaterialR.attr.colorSurfaceContainerHigh,
            fallbackScheme.surfaceContainerHigh,
        ),
        contentCardBackground = if (canonical.dark) {
            context.colorAttribute(R.attr.contentCardBackgroundColor, fallback.contentCardBackground)
        } else {
            resolveThemeSurface(fallback.itemBackground)
        },
        surfaceContainerHighest = context.colorAttribute(
            MaterialR.attr.colorSurfaceContainerHighest,
            fallbackScheme.surfaceContainerHighest,
        ),
        secondaryContainer = context.colorAttribute(
            MaterialR.attr.colorSecondaryContainer,
            fallbackScheme.secondaryContainer,
        ),
        onSecondaryContainer = context.colorAttribute(
            MaterialR.attr.colorOnSecondaryContainer,
            fallbackScheme.onSecondaryContainer,
        ),
        contentPrimary = context.colorAttribute(
            R.attr.contentPrimaryColor,
            fallbackScheme.onSurface,
        ),
        mutedText = context.colorAttribute(
            R.attr.mutedTextColor,
            fallbackScheme.onSurfaceVariant,
        ),
        outlineVariant = context.colorAttribute(
            MaterialR.attr.colorOutlineVariant,
            fallbackScheme.outlineVariant,
        ),
        commentDivider = context.colorAttribute(
            R.attr.commentDividerColor,
            fallbackScheme.outlineVariant,
        ),
        commentCountIndicator = context.colorAttribute(
            R.attr.commentCountIndicatorColor,
            fallback.commentCountIndicator,
        ),
        iconTint = context.colorAttribute(R.attr.iconTintColor, fallbackScheme.onSurface).let { color ->
            color.copy(alpha = color.alpha * 0.8f)
        },
        popupMenuBackground = if (canonical.dark) {
            context.colorAttribute(R.attr.popupMenuBackgroundColor, fallback.popupMenuBackground)
        } else {
            resolveThemeSurface(fallback.itemBackground)
        },
        mutedSurface = mutedSurface,
        itemBackground = resolveThemeSurface(fallback.itemBackground),
        settingsHeaderSelected = context.colorAttribute(
            R.attr.settingsHeaderSelectedColor,
            fallbackScheme.surfaceContainerHigh,
        ),
        settingsMainToggle = context.colorAttribute(
            R.attr.settingsMainToggleColor,
            fallbackScheme.secondaryContainer,
        ),
        settingsMainToggleText = context.colorAttribute(
            R.attr.settingsMainToggleTextColor,
            fallbackScheme.onSecondaryContainer,
        ),
        overlayButton = context.colorAttribute(
            R.attr.overlayButtonColor,
            fallbackScheme.secondaryContainer,
        ),
        overlayButtonContent = context.colorAttribute(
            R.attr.overlayButtonContentColor,
            fallbackScheme.onSecondaryContainer,
        ),
        submissionsCommentTimeBackground = context.colorAttribute(
            R.attr.submissionsCommentTimeBackgroundColor,
            fallbackScheme.surfaceContainerHighest,
        ),
        submissionsCommentTimeOutline = context.colorAttribute(
            R.attr.submissionsCommentTimeOutlineColor,
            Color.Transparent,
        ),
    )
}

private fun Context.colorAttribute(
    @AttrRes attribute: Int,
    fallback: Color,
): Color {
    val value = TypedValue()
    if (!theme.resolveAttribute(attribute, value, true)) {
        return fallback
    }

    val resolved = when {
        value.resourceId != 0 -> ContextCompat.getColor(this, value.resourceId)
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
        else -> return fallback
    }
    return Color(resolved)
}

/** Base color used by story preview tint extraction on Android. */
fun previewTintBaseColor(context: Context): Int = harmonicColors(context).contentCardBackground.toArgb()
