@file:JvmName("AndroidHarmonicThemeKt")

package com.simon.harmonichackernews.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.utils.ThemeUtils

@Composable
fun HarmonicTheme(
    selection: ThemeSelection? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activeSelection = selection ?: ThemeSelection(
        theme = ThemeUtils.getPreferredTheme(context),
        dark = ThemeUtils.isDarkMode(context),
    )
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

    HarmonicTheme(
        colors = colors,
        colorScheme = colorScheme,
        darkTheme = canonical.dark,
        content = content,
    )
}

fun harmonicColors(context: Context): HarmonicColors {
    val canonical = HarmonicThemeCatalog.resolve(
        theme = ThemeUtils.getPreferredTheme(context),
        systemDark = ThemeUtils.uiModeNight(context),
    )
    return harmonicColors(context, canonical)
}

private fun harmonicColors(
    context: Context,
    canonical: HarmonicThemePalette,
): HarmonicColors {
    val fallback = canonical.colors
    val fallbackScheme = canonical.colorScheme
    val background = context.colorAttribute(
        android.R.attr.colorBackground,
        fallbackScheme.background,
    )
    val settingsSegment = context.colorAttribute(
        R.attr.settingsSegmentColor,
        fallbackScheme.surfaceContainerHigh,
    )
    fun resolveSettingsSurface(fallbackColor: Color): Color = when (fallbackColor) {
        fallback.background -> background
        fallback.settingsSegment -> settingsSegment
        else -> fallbackColor
    }
    val defaultTextView = AppCompatTextView(context)
    return HarmonicColors(
        background = background,
        accent = context.colorAttribute(
            AppCompatR.attr.colorAccent,
            fallback.accent,
        ),
        onSurface = context.colorAttribute(
            MaterialR.attr.colorOnSurface,
            fallbackScheme.onSurface,
        ),
        textPrimary = Color(defaultTextView.currentTextColor).takeUnless {
            it == Color.Unspecified
        } ?: fallback.textPrimary,
        textSecondary = context.colorAttribute(
            R.attr.secondaryTextColor,
            fallbackScheme.onSurfaceVariant,
        ),
        link = Color(defaultTextView.linkTextColors.defaultColor),
        surfaceContainerHigh = context.colorAttribute(
            MaterialR.attr.colorSurfaceContainerHigh,
            fallbackScheme.surfaceContainerHigh,
        ),
        storyCardBackground = context.colorAttribute(
            R.attr.storyCardBackgroundColor,
            fallback.storyCardBackground,
        ),
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
        storyNormal = context.colorAttribute(R.attr.storyColorNormal, fallbackScheme.onSurface),
        storyDisabled = context.colorAttribute(
            R.attr.storyColorDisabled,
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
        drawable = context.colorAttribute(R.attr.drawableColor, fallbackScheme.onSurface).let { color ->
            color.copy(alpha = color.alpha * 0.8f)
        },
        popupMenuBackground = context.colorAttribute(
            R.attr.popupMenuBackgroundColor,
            fallbackScheme.surfaceContainerHigh,
        ),
        settingsSegment = settingsSegment,
        settingsPageBackground = resolveSettingsSurface(fallback.settingsPageBackground),
        settingsItemBackground = resolveSettingsSurface(fallback.settingsItemBackground),
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
