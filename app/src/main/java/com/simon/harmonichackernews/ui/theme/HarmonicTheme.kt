package com.simon.harmonichackernews.ui.theme

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.ThemeUtils

val ProductSansFontFamily = FontFamily(
    Font(R.font.product_sans_regular, FontWeight.Normal),
    Font(R.font.product_sans_bold, FontWeight.SemiBold),
    Font(R.font.product_sans_italic, FontWeight.Normal, FontStyle.Italic),
)

val GoogleSansFlexRoundedFontFamily = FontFamily(
    Font(R.font.google_sans_flex_rounded_regular, FontWeight.Normal),
    Font(R.font.google_sans_flex_rounded_bold, FontWeight.Bold),
)

@Immutable
data class HarmonicColors(
    val background: Color,
    val onSurface: Color,
    val textPrimary: Color,
    val link: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val storyNormal: Color,
    val storyDisabled: Color,
    val outlineVariant: Color,
    val commentDivider: Color,
    val drawable: Color,
    val settingsSegment: Color,
    val settingsHeaderSelected: Color,
    val settingsMainToggle: Color,
    val settingsMainToggleText: Color,
)

private val LocalHarmonicColors = staticCompositionLocalOf<HarmonicColors> {
    error("HarmonicTheme is not present")
}

object HarmonicTheme {
    val colors: HarmonicColors
        @Composable
        get() = LocalHarmonicColors.current
}

@Composable
fun HarmonicTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = ThemeUtils.isDarkMode(context)
    val colors = harmonicColors(context)
    val baseScheme = if (isDark) darkColorScheme() else lightColorScheme()
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
        surfaceContainerHigh = colors.surfaceContainerHigh,
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

    androidx.compose.runtime.CompositionLocalProvider(
        LocalHarmonicColors provides colors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

fun harmonicColors(context: Context): HarmonicColors {
    val fallbackScheme = if (ThemeUtils.isDarkMode(context)) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }
    val background = context.colorAttribute(
        android.R.attr.colorBackground,
        fallbackScheme.background,
    )
    val defaultTextView = AppCompatTextView(context)
    return HarmonicColors(
        background = background,
        onSurface = context.colorAttribute(
            MaterialR.attr.colorOnSurface,
            fallbackScheme.onSurface,
        ),
        textPrimary = Color(defaultTextView.currentTextColor),
        link = Color(defaultTextView.linkTextColors.defaultColor),
        surfaceContainerHigh = context.colorAttribute(
            MaterialR.attr.colorSurfaceContainerHigh,
            fallbackScheme.surfaceContainerHigh,
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
        drawable = context.colorAttribute(R.attr.drawableColor, fallbackScheme.onSurface),
        settingsSegment = context.colorAttribute(
            R.attr.settingsSegmentColor,
            fallbackScheme.surfaceContainerHigh,
        ),
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
