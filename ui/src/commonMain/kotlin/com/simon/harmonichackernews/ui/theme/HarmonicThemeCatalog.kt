package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.simon.harmonichackernews.settings.ThemePreferences

data class HarmonicThemePalette(
    val dark: Boolean,
    val colors: HarmonicColors,
    val colorScheme: ColorScheme,
)

/** Canonical, platform-neutral fallback palettes for every stored Harmonic theme. */
object HarmonicThemeCatalog {
    fun resolve(
        theme: String?,
        systemDark: Boolean,
        accentPreset: String = ThemePreferences.ACCENT_DEFAULT,
    ): HarmonicThemePalette = (when (theme) {
        ThemePreferences.DEFAULT -> if (systemDark) materialDark else materialLight
        ThemePreferences.MATERIAL_FIXED_AUTO -> if (systemDark) materialFixedDark else materialFixedLight
        "darklight_daynight" -> if (systemDark) dark else light
        "amoledwhite_daynight" -> if (systemDark) amoled else white
        ThemePreferences.MATERIAL_FIXED_DARK -> materialFixedDark
        ThemePreferences.MATERIAL_FIXED_LIGHT -> materialFixedLight
        "material_dark" -> materialDark
        "material_light" -> materialLight
        "light" -> light
        "hacker" -> hacker
        "hacker_news" -> hackerNews
        "amoled" -> amoled
        "white" -> white
        "gray" -> gray
        else -> dark
    }).let { ThemeAccentCatalog.apply(it, accentPreset) }

    private val dark = create(
        dark = true,
        readerModeBackground = Color(0xFF222431),
        surface = Color(0xFF14191E),
        accent = Color(0xFFFF959E),
        commentCountIndicator = Color(0xFF99595E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        mutedText = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF343744),
        settingsToggle = Color(0xFF99595E),
        settingsToggleText = Color(0xFFFFD9DC),
        mutedSurface = Color(0xFF2B2E3A),
        contentCardBackground = Color(0xFF2B2E3A),
        onPrimary = Color(0xFFF6F6EF),
        overlayButton = Color(0xFF99595E),
        overlayButtonContent = Color.White,
        onPrimaryContainer = Color(0xFFFFD9DC),
        onSecondary = Color(0xFFF6F6EF),
        secondaryContainer = Color(0xFF99595E),
        onSecondaryContainer = Color(0xFFFFD9DC),
        tertiary = Color(0xFF99595E),
        onTertiary = Color(0xFFF6F6EF),
        surfaceHighest = Color(0xFF2B2E3A),
        surfaceVariant = Color(0xFF2B2E3A),
        outlineColor = Color(0xFF888899),
        icon = Color.White,
    )
    private val gray = create(
        dark = true,
        readerModeBackground = Color(0xFF292A2E),
        surface = Color(0xFF292A2E),
        accent = Color(0xFFFF959E),
        commentCountIndicator = Color(0xFF99595E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        mutedText = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF292A2E),
        settingsToggle = Color(0xFF34353A),
        settingsToggleText = Color(0xFFD4D5DA),
        mutedSurface = Color(0xFF202124),
        contentCardBackground = Color(0xFF34353A),
        onPrimary = Color(0xFFF6F6EF),
        onPrimaryContainer = Color(0xFFFFD9DC),
        onSecondary = Color(0xFFF6F6EF),
        secondaryContainer = Color(0xFF99595E),
        onSecondaryContainer = Color(0xFFFFD9DC),
        tertiary = Color(0xFF99595E),
        onTertiary = Color(0xFFF6F6EF),
        surfaceHighest = Color(0xFF34353A),
        surfaceVariant = Color(0xFF34353A),
        outlineColor = Color(0xFF888899),
        icon = Color.White,
        popup = Color(0xFF14191E),
        overlayButtonContent = Color.White,
    )
    private val amoled = create(
        dark = true,
        readerModeBackground = Color.Black,
        surface = Color.Black,
        accent = Color(0xFFFF959E),
        commentCountIndicator = Color(0xFF99595E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        mutedText = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF0D0F11),
        settingsToggle = Color(0xFF0D0F11),
        settingsToggleText = Color(0xFFDADCE2),
        mutedSurface = Color.Black,
        onPrimary = Color(0xFFF6F6EF),
        overlayButton = Color.Black,
        overlayButtonContent = Color(0xFFDFDFDF),
        submissionsOutline = Color(0x33FFFFFF),
        onPrimaryContainer = Color(0xFFFFD9DC),
        onSecondary = Color(0xFFF6F6EF),
        secondaryContainer = Color(0xFF99595E),
        onSecondaryContainer = Color(0xFFFFD9DC),
        tertiary = Color(0xFF99595E),
        onTertiary = Color(0xFFF6F6EF),
        surfaceHighest = Color(0xFF000000),
        surfaceVariant = Color(0xFF000000),
        outlineColor = Color(0xFF888899),
        icon = Color.White,
        popup = Color(0xFF14191E),
    )
    private val hacker = create(
        dark = true,
        readerModeBackground = Color.Black,
        surface = Color.Black,
        accent = Color(0xFF00FF00),
        primary = Color(0xFF00FF00),
        primaryContainer = Color(0xFF003300),
        text = Color(0xFF00FF00),
        secondaryText = Color(0x9900FF00),
        mutedText = Color(0x9900FF00),
        divider = Color(0x6600FF00),
        settingsHeader = Color(0xFF001A00),
        settingsToggle = Color(0xFF003300),
        settingsToggleText = Color(0xFF00FF00),
        mutedSurface = Color.Black,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF003300),
        onSecondaryContainer = Color(0xFF00FF00),
        overlayButton = Color.Black,
        overlayButtonContent = Color(0xFF00FF00),
        submissionsOutline = Color(0x6600FF00),
        onPrimaryContainer = Color(0xFF00FF00),
        tertiary = Color(0xFF00FF00),
        onTertiary = Color(0xFF000000),
        surfaceHighest = Color(0xFF000000),
        surfaceVariant = Color(0xFF000000),
        outlineColor = Color(0x6600FF00),
    )
    private val light = create(
        dark = false,
        readerModeBackground = Color(0xFFF6F6EF),
        surface = Color(0xFFF6F6EF),
        accent = Color(0xFF4C9B7B),
        primary = Color(0xFF4C9B7B),
        primaryContainer = Color(0xFF478A74),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4A4A4A),
        mutedText = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFEAE6D2),
        settingsToggle = Color(0xFFC6E9D8),
        settingsToggleText = Color(0xFF245B46),
        mutedSurface = Color(0xFFF0EEDC),
        background = Color(0xFFF6F5EC),
        itemBackground = Color(0xFFFCFCFA),
        onSecondary = Color.White,
        onPrimary = Color(0xFFFFFFFF),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC6E9D8),
        onSecondaryContainer = Color(0xFF245B46),
        tertiary = Color(0xFF478A74),
        onTertiary = Color(0xFFFFFFFF),
        surfaceHighest = Color(0xFFF0EEDC),
        surfaceVariant = Color(0xFFF0EEDC),
        outlineColor = Color(0xFF888888),
        icon = Color(0xBB000000),
        commentCountIndicator = Color(0xFF99595E),
    )
    private val white = create(
        dark = false,
        readerModeBackground = Color.White,
        surface = Color.White,
        accent = Color(0xFF4C9B7B),
        primary = Color(0xFF4C9B7B),
        primaryContainer = Color(0xFF478A74),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4A4A4A),
        mutedText = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFE1E3E5),
        settingsToggle = Color(0xFFC6E9D8),
        settingsToggleText = Color(0xFF245B46),
        mutedSurface = Color(0xFFF2F4F7),
        onSecondary = Color.White,
        onPrimary = Color(0xFFFFFFFF),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFC6E9D8),
        onSecondaryContainer = Color(0xFF245B46),
        tertiary = Color(0xFF478A74),
        onTertiary = Color(0xFFFFFFFF),
        surfaceHighest = Color(0xFFF2F4F7),
        surfaceVariant = Color(0xFFF2F4F7),
        outlineColor = Color(0xFF888888),
        icon = Color(0xBB000000),
        commentCountIndicator = Color(0xFF99595E),
    )
    private val hackerNews = create(
        dark = false,
        readerModeBackground = Color(0xFFF6F6EF),
        surface = Color(0xFFEEEBD9),
        accent = Color(0xFFFF6600),
        commentCountIndicator = Color(0xFFB34700),
        primary = Color(0xFFFF6600),
        primaryContainer = Color(0xFFFFD5B8),
        text = Color(0xFF222222),
        secondaryText = Color(0xFF828282),
        mutedText = Color(0xFF828282),
        divider = Color(0xFFD8D3BE),
        settingsHeader = Color(0xFFEEEBD9),
        settingsToggle = Color(0xFFFFD5B8),
        settingsToggleText = Color(0xFF7A3100),
        mutedSurface = Color(0xFFF2EEDF),
        background = Color(0xFFF7F5ED),
        itemBackground = Color(0xFFFCFCFA),
        onPrimary = Color.White,
        onSecondary = Color.White,
        overlayButton = Color(0xFFBF5724),
        overlayButtonContent = Color.White,
        onPrimaryContainer = Color(0xFF7A3100),
        secondaryContainer = Color(0xFFFFD5B8),
        onSecondaryContainer = Color(0xFF7A3100),
        tertiary = Color(0xFFB34700),
        onTertiary = Color(0xFFFFFFFF),
        surfaceHighest = Color(0xFFF2EEDF),
        surfaceVariant = Color(0xFFEEEBD9),
        outlineColor = Color(0xFFD8D3BE),
        icon = Color(0xCC000000),
    )
    private val materialFixedLight = materialPalette(
        dark = false,
        scheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF4F378B),
            secondary = Color(0xFF625B71),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF4A4458),
            tertiary = Color(0xFF7D5260),
            onTertiary = Color(0xFFFFFFFF),
            background = Color(0xFFFEF7FF),
            onBackground = Color(0xFF1D1B20),
            surface = Color(0xFFFEF7FF),
            onSurface = Color(0xFF1D1B20),
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainerHigh = Color(0xFFECE6F0),
            surfaceContainerHighest = Color(0xFFE6E0E9),
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            surfaceContainer = Color(0xFFF3EDF7),
        ),
        commentCountIndicator = Color(0xFF6750A4),
    )
    private val materialFixedDark = materialPalette(
        dark = true,
        scheme = darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondary = Color(0xFFCCC2DC),
            onSecondary = Color(0xFF332D41),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8),
            tertiary = Color(0xFFEFB8C8),
            onTertiary = Color(0xFF492532),
            background = Color(0xFF141218),
            onBackground = Color(0xFFE6E0E9),
            surface = Color(0xFF141218),
            onSurface = Color(0xFFE6E0E9),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B),
            surfaceVariant = Color(0xFF49454F),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            surfaceContainer = Color(0xFF211F26),
        ),
        commentCountIndicator = Color(0xFF6750A4),
    )
    private val materialLight = create(
        dark = false,
        readerModeBackground = Color(0xFFF0F0F3),
        surface = Color(0xFFEBF1F8),
        accent = Color(0xFF00668B),
        primary = Color(0xFF8094A0),
        primaryContainer = Color(0xFF374955),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4E616C),
        mutedText = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFD9DEE2),
        settingsToggle = Color(0xFFB0C6FF),
        settingsToggleText = Color(0xFF294778),
        mutedSurface = Color(0xFFE1E3E5),
        contentCardBackground = Color(0xFFE1E3E5),
        onSecondary = Color.White,
        overlayButton = Color(0xFF374955),
        overlayButtonContent = Color.White,
    )
    private val materialDark = create(
        dark = true,
        readerModeBackground = Color(0xFF191C1E),
        surface = Color(0xFF2A3136),
        accent = Color(0xFF8094A0),
        commentCountIndicator = Color(0xFF00668B),
        primary = Color(0xFF8094A0),
        secondary = Color(0xFFB0C6FF),
        primaryContainer = Color(0xFF374955),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFF9AAEBB),
        mutedText = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF2E3133),
        settingsToggle = Color(0xFF484264),
        settingsToggleText = Color(0xFFD6CFF5),
        mutedSurface = Color(0xFF2A3136),
        onPrimary = Color(0xFFE1E3E5),
        overlayButton = Color(0xFF374955),
        overlayButtonContent = Color(0xFFE1E3E5),
        popup = Color(0xFF2E3133),
    )

    /** Map Material roles into the app's surfaces without consulting a mutable host theme. */
    fun materialPalette(
        dark: Boolean,
        scheme: ColorScheme,
        commentCountIndicator: Color,
    ): HarmonicThemePalette {
        val background = if (dark) scheme.background else scheme.surfaceContainerHigh
        val colors = HarmonicColors(
            background = background,
            readerModeBackground = scheme.background,
            accent = scheme.secondary,
            onSurface = scheme.onSurface,
            textPrimary = scheme.onSurface,
            textSecondary = scheme.onSurfaceVariant,
            link = scheme.primary,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            contentCardBackground = if (dark) scheme.surfaceContainerHigh else scheme.background,
            surfaceContainerHighest = scheme.surfaceContainerHighest,
            secondaryContainer = scheme.secondaryContainer,
            onSecondaryContainer = scheme.onSecondaryContainer,
            contentPrimary = scheme.onSurface,
            mutedText = scheme.onSurfaceVariant,
            outlineVariant = scheme.outlineVariant,
            commentDivider = scheme.outlineVariant,
            commentCountIndicator = commentCountIndicator,
            iconTint = scheme.onSurface.copy(alpha = scheme.onSurface.alpha * 0.8f),
            popupMenuBackground = if (dark) scheme.surfaceContainerHigh else scheme.background,
            mutedSurface = scheme.surfaceContainerHigh,
            itemBackground = if (dark) scheme.surfaceContainerHigh else scheme.background,
            settingsHeaderSelected = scheme.surfaceContainer,
            settingsMainToggle = scheme.secondaryContainer,
            settingsMainToggleText = scheme.onSecondaryContainer,
            overlayButton = if (dark) scheme.primaryContainer else scheme.primary,
            overlayButtonContent = if (dark) scheme.onSurface else scheme.onPrimary,
            submissionsCommentTimeBackground = scheme.surfaceContainerHighest,
            submissionsCommentTimeOutline = Color.Transparent,
        )
        // Match the roles previously read by AndroidHarmonicTheme. Other Compose roles keep
        // their established defaults (for example the AI badges' tertiary container).
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val composeScheme = base.copy(
            primary = scheme.primary,
            onPrimary = scheme.onPrimary,
            primaryContainer = scheme.primaryContainer,
            onPrimaryContainer = scheme.onPrimaryContainer,
            secondary = scheme.secondary,
            onSecondary = scheme.onSecondary,
            secondaryContainer = scheme.secondaryContainer,
            onSecondaryContainer = scheme.onSecondaryContainer,
            tertiary = scheme.tertiary,
            onTertiary = scheme.onTertiary,
            background = background,
            onBackground = scheme.onSurface,
            surface = scheme.surface,
            onSurface = scheme.onSurface,
            surfaceContainerLow = scheme.surfaceContainerLow,
            surfaceContainerHigh = scheme.surfaceContainerHigh,
            surfaceContainerHighest = scheme.surfaceContainerHighest,
            surfaceVariant = scheme.surfaceVariant,
            onSurfaceVariant = scheme.onSurfaceVariant,
            outline = scheme.outline,
            outlineVariant = scheme.outlineVariant,
        )
        return HarmonicThemePalette(dark, colors, composeScheme)
    }

    private fun create(
        dark: Boolean,
        readerModeBackground: Color,
        surface: Color,
        accent: Color,
        commentCountIndicator: Color = accent,
        primary: Color,
        secondary: Color = accent,
        primaryContainer: Color,
        text: Color,
        secondaryText: Color,
        mutedText: Color,
        divider: Color,
        settingsHeader: Color,
        settingsToggle: Color,
        settingsToggleText: Color,
        mutedSurface: Color,
        background: Color? = null,
        itemBackground: Color? = null,
        contentCardBackground: Color = surface,
        onPrimary: Color? = null,
        onSecondary: Color? = null,
        secondaryContainer: Color? = null,
        onSecondaryContainer: Color? = null,
        overlayButton: Color? = null,
        overlayButtonContent: Color? = null,
        popup: Color = surface,
        submissionsOutline: Color? = null,
        onPrimaryContainer: Color? = null,
        tertiary: Color? = null,
        onTertiary: Color? = null,
        surfaceHighest: Color? = null,
        surfaceVariant: Color? = null,
        outlineColor: Color? = null,
        icon: Color? = null,
    ): HarmonicThemePalette {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val resolvedSurfaceHighest = surfaceHighest ?: lerp(surface, text, if (dark) 0.10f else 0.06f)
        val resolvedBackground = background ?: if (dark) readerModeBackground else mutedSurface
        val outline = outlineColor ?: lerp(readerModeBackground, text, 0.24f)
        val resolvedSecondaryContainer = secondaryContainer ?: base.secondaryContainer
        val resolvedOnSecondaryContainer = onSecondaryContainer ?: base.onSecondaryContainer
        val resolvedOverlayButton = overlayButton ?: if (dark) {
            resolvedSecondaryContainer
        } else {
            secondary
        }
        val resolvedOverlayButtonContent = overlayButtonContent ?: if (dark) {
            resolvedOnSecondaryContainer
        } else {
            onSecondary ?: base.onSecondary
        }
        val scheme = base.copy(
            primary = primary,
            onPrimary = onPrimary ?: base.onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer ?: base.onPrimaryContainer,
            tertiary = tertiary ?: base.tertiary,
            onTertiary = onTertiary ?: base.onTertiary,
            secondary = secondary,
            onSecondary = onSecondary ?: base.onSecondary,
            secondaryContainer = resolvedSecondaryContainer,
            onSecondaryContainer = resolvedOnSecondaryContainer,
            background = resolvedBackground,
            onBackground = text,
            surface = readerModeBackground,
            onSurface = text,
            surfaceContainerLow = readerModeBackground,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = resolvedSurfaceHighest,
            surfaceVariant = surfaceVariant ?: surface,
            onSurfaceVariant = secondaryText,
            outline = outline,
            outlineVariant = divider,
        )
        return HarmonicThemePalette(
            dark = dark,
            colors = HarmonicColors(
                background = resolvedBackground,
                readerModeBackground = readerModeBackground,
                accent = accent,
                onSurface = text,
                textPrimary = text,
                textSecondary = secondaryText,
                link = secondary,
                surfaceContainerHigh = surface,
                contentCardBackground = if (dark) {
                    contentCardBackground
                } else {
                    itemBackground ?: readerModeBackground
                },
                surfaceContainerHighest = resolvedSurfaceHighest,
                secondaryContainer = resolvedSecondaryContainer,
                onSecondaryContainer = resolvedOnSecondaryContainer,
                contentPrimary = text,
                mutedText = mutedText,
                outlineVariant = divider,
                commentDivider = divider,
                commentCountIndicator = commentCountIndicator,
                iconTint = (icon ?: text).let { it.copy(alpha = it.alpha * 0.8f) },
                popupMenuBackground = if (dark) popup else itemBackground ?: readerModeBackground,
                mutedSurface = mutedSurface,
                itemBackground = itemBackground ?: if (dark) {
                    mutedSurface
                } else {
                    readerModeBackground
                },
                settingsHeaderSelected = settingsHeader,
                settingsMainToggle = settingsToggle,
                settingsMainToggleText = settingsToggleText,
                overlayButton = resolvedOverlayButton,
                overlayButtonContent = resolvedOverlayButtonContent,
                submissionsCommentTimeBackground = if (submissionsOutline != null) {
                    readerModeBackground
                } else {
                    resolvedSurfaceHighest
                },
                submissionsCommentTimeOutline = submissionsOutline ?: Color.Transparent,
            ),
            colorScheme = scheme,
        )
    }
}
