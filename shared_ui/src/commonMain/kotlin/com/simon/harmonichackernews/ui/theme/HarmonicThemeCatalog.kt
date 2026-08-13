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
    fun resolve(theme: String?, systemDark: Boolean): HarmonicThemePalette = when (theme) {
        ThemePreferences.DEFAULT -> if (systemDark) materialDark else materialLight
        "darklight_daynight" -> if (systemDark) dark else light
        "amoledwhite_daynight" -> if (systemDark) amoled else white
        "material_dark" -> materialDark
        "material_light" -> materialLight
        "light" -> light
        "hacker" -> hacker
        "hacker_news" -> hackerNews
        "amoled" -> amoled
        "white" -> white
        "gray" -> gray
        else -> dark
    }

    private val dark = create(
        dark = true,
        background = Color(0xFF222431),
        surface = Color(0xFF14191E),
        accent = Color(0xFFFF959E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        storyDisabled = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF343744),
        settingsToggle = Color(0xFF99595E),
        settingsToggleText = Color(0xFFFFD9DC),
        settingsSegment = Color(0xFF2B2E3A),
        onPrimary = Color(0xFFF6F6EF),
    )
    private val gray = create(
        dark = true,
        background = Color(0xFF292A2E),
        surface = Color(0xFF292A2E),
        accent = Color(0xFFFF959E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        storyDisabled = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF292A2E),
        settingsToggle = Color(0xFF34353A),
        settingsToggleText = Color(0xFFD4D5DA),
        settingsSegment = Color(0xFF202124),
        onPrimary = Color(0xFFF6F6EF),
    )
    private val amoled = create(
        dark = true,
        background = Color.Black,
        surface = Color.Black,
        accent = Color(0xFFFF959E),
        primary = Color(0xFFFF959E),
        primaryContainer = Color(0xFF99595E),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFFBBBBCC),
        storyDisabled = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF0D0F11),
        settingsToggle = Color(0xFF0D0F11),
        settingsToggleText = Color(0xFFDADCE2),
        settingsSegment = Color.Black,
        onPrimary = Color(0xFFF6F6EF),
        overlayButton = Color.Black,
        submissionsOutline = Color(0x33FFFFFF),
    )
    private val hacker = create(
        dark = true,
        background = Color.Black,
        surface = Color.Black,
        accent = Color(0xFF00FF00),
        primary = Color(0xFF00FF00),
        primaryContainer = Color(0xFF003300),
        text = Color(0xFF00FF00),
        secondaryText = Color(0x9900FF00),
        storyDisabled = Color(0x9900FF00),
        divider = Color(0x6600FF00),
        settingsHeader = Color(0xFF001A00),
        settingsToggle = Color(0xFF003300),
        settingsToggleText = Color(0xFF00FF00),
        settingsSegment = Color.Black,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        secondaryContainer = Color(0xFF003300),
        onSecondaryContainer = Color(0xFF00FF00),
        overlayButton = Color.Black,
        submissionsOutline = Color(0x6600FF00),
    )
    private val light = create(
        dark = false,
        background = Color(0xFFF6F6EF),
        surface = Color(0xFFF6F6EF),
        accent = Color(0xFF4C9B7B),
        primary = Color(0xFF4C9B7B),
        primaryContainer = Color(0xFF478A74),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4A4A4A),
        storyDisabled = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFEAE6D2),
        settingsToggle = Color(0xFFC6E9D8),
        settingsToggleText = Color(0xFF245B46),
        settingsSegment = Color(0xFFF0EEDC),
        onSecondary = Color.White,
    )
    private val white = create(
        dark = false,
        background = Color.White,
        surface = Color.White,
        accent = Color(0xFF4C9B7B),
        primary = Color(0xFF4C9B7B),
        primaryContainer = Color(0xFF478A74),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4A4A4A),
        storyDisabled = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFE1E3E5),
        settingsToggle = Color(0xFFC6E9D8),
        settingsToggleText = Color(0xFF245B46),
        settingsSegment = Color(0xFFF2F4F7),
        onSecondary = Color.White,
    )
    private val hackerNews = create(
        dark = false,
        background = Color(0xFFF6F6EF),
        surface = Color(0xFFEEEBD9),
        accent = Color(0xFFFF6600),
        primary = Color(0xFFFF6600),
        primaryContainer = Color(0xFFFFD5B8),
        text = Color(0xFF222222),
        secondaryText = Color(0xFF828282),
        storyDisabled = Color(0xFF828282),
        divider = Color(0xFFD8D3BE),
        settingsHeader = Color(0xFFEEEBD9),
        settingsToggle = Color(0xFFFFD5B8),
        settingsToggleText = Color(0xFF7A3100),
        settingsSegment = Color(0xFFF2EEDF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        overlayButton = Color(0xFFBF5724),
    )
    private val materialLight = create(
        dark = false,
        background = Color(0xFFF0F0F3),
        surface = Color(0xFFEBF1F8),
        accent = Color(0xFF00668B),
        primary = Color(0xFF8094A0),
        primaryContainer = Color(0xFF374955),
        text = Color(0xFF2F2F2F),
        secondaryText = Color(0xFF4E616C),
        storyDisabled = Color(0xFF777777),
        divider = Color(0xFFAAAAAA),
        settingsHeader = Color(0xFFD9DEE2),
        settingsToggle = Color(0xFFB0C6FF),
        settingsToggleText = Color(0xFF294778),
        settingsSegment = Color(0xFFE1E3E5),
        onSecondary = Color.White,
    )
    private val materialDark = create(
        dark = true,
        background = Color(0xFF191C1E),
        surface = Color(0xFF2A3136),
        accent = Color(0xFF8094A0),
        primary = Color(0xFF8094A0),
        secondary = Color(0xFFB0C6FF),
        primaryContainer = Color(0xFF374955),
        text = Color(0xFFDFDFDF),
        secondaryText = Color(0xFF9AAEBB),
        storyDisabled = Color(0xFF9999AA),
        divider = Color(0x22EEEEFF),
        settingsHeader = Color(0xFF2E3133),
        settingsToggle = Color(0xFF484264),
        settingsToggleText = Color(0xFFD6CFF5),
        settingsSegment = Color(0xFF2A3136),
        onPrimary = Color(0xFFE1E3E5),
        popup = Color(0xFF2E3133),
    )

    private fun create(
        dark: Boolean,
        background: Color,
        surface: Color,
        accent: Color,
        primary: Color,
        secondary: Color = accent,
        primaryContainer: Color,
        text: Color,
        secondaryText: Color,
        storyDisabled: Color,
        divider: Color,
        settingsHeader: Color,
        settingsToggle: Color,
        settingsToggleText: Color,
        settingsSegment: Color,
        onPrimary: Color? = null,
        onSecondary: Color? = null,
        secondaryContainer: Color? = null,
        onSecondaryContainer: Color? = null,
        overlayButton: Color? = null,
        popup: Color = surface,
        submissionsOutline: Color? = null,
    ): HarmonicThemePalette {
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val surfaceHighest = lerp(surface, text, if (dark) 0.10f else 0.06f)
        val outline = lerp(background, text, 0.24f)
        val resolvedSecondaryContainer = secondaryContainer ?: base.secondaryContainer
        val resolvedOnSecondaryContainer = onSecondaryContainer ?: base.onSecondaryContainer
        val scheme = base.copy(
            primary = primary,
            onPrimary = onPrimary ?: base.onPrimary,
            primaryContainer = primaryContainer,
            secondary = secondary,
            onSecondary = onSecondary ?: base.onSecondary,
            secondaryContainer = resolvedSecondaryContainer,
            onSecondaryContainer = resolvedOnSecondaryContainer,
            background = background,
            onBackground = text,
            surface = background,
            onSurface = text,
            surfaceContainerLow = background,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceHighest,
            surfaceVariant = surface,
            onSurfaceVariant = secondaryText,
            outline = outline,
            outlineVariant = divider,
        )
        return HarmonicThemePalette(
            dark = dark,
            colors = HarmonicColors(
                background = background,
                accent = accent,
                onSurface = text,
                textPrimary = text,
                textSecondary = secondaryText,
                link = secondary,
                surfaceContainerHigh = surface,
                surfaceContainerHighest = surfaceHighest,
                secondaryContainer = resolvedSecondaryContainer,
                onSecondaryContainer = resolvedOnSecondaryContainer,
                storyNormal = text,
                storyDisabled = storyDisabled,
                outlineVariant = divider,
                commentDivider = divider,
                drawable = text.copy(alpha = text.alpha * 0.8f),
                popupMenuBackground = popup,
                settingsSegment = settingsSegment,
                settingsHeaderSelected = settingsHeader,
                settingsMainToggle = settingsToggle,
                settingsMainToggleText = settingsToggleText,
                overlayButton = overlayButton ?: if (dark) {
                    resolvedSecondaryContainer
                } else {
                    secondary
                },
                submissionsCommentTimeBackground = if (submissionsOutline != null) {
                    background
                } else {
                    surfaceHighest
                },
                submissionsCommentTimeOutline = submissionsOutline ?: Color.Transparent,
            ),
            colorScheme = scheme,
        )
    }
}
