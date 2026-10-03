package com.simon.harmonichackernews

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ThemeAccentCatalog
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette
import com.simon.harmonichackernews.utils.AndroidActivityTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29, 31, 35], application = Application::class)
class AndroidThemeMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val themes = listOf(
        "dark", "light", "amoled", "white", "gray", "hacker", "hacker_news",
        "darklight_daynight", "amoledwhite_daynight", "material_dark", "material_light",
        ThemePreferences.DEFAULT, ThemePreferences.MATERIAL_FIXED_AUTO,
        ThemePreferences.MATERIAL_FIXED_DARK, ThemePreferences.MATERIAL_FIXED_LIGHT,
    )

    @Test
    fun everyThemeAndAccentIgnoresStaleHostConfiguration() {
        for (theme in themes) for (dark in listOf(false, true)) {
            val selection = ThemeSelection(theme, dark, ThemePreferences.ACCENT_ORANGE)
            val stale = ContextThemeWrapper(context, AndroidActivityTheme.themeResource("light", false))
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            }
            val expected = harmonicThemePalette(context, selection)
            val actual = harmonicThemePalette(stale.createConfigurationContext(config), selection)
            assertEquals("$theme/$dark", expected.colors, actual.colors)
            assertEquals("$theme/$dark", expected.colorScheme.primary, actual.colorScheme.primary)
        }
    }

    @Test
    fun fixedAndCustomPalettesHaveNoAndroidResourceOverrides() {
        for (theme in themes.filterNot { it in listOf("material_dark", "material_light", ThemePreferences.DEFAULT) }) {
            for (dark in listOf(false, true)) {
                val expected = HarmonicThemeCatalog.resolve(theme, dark)
                val actual = harmonicThemePalette(context, ThemeSelection(theme, dark))
                assertEquals("$theme/$dark", expected.colors, actual.colors)
            }
        }
    }

    @Test
    fun dynamicSelectionUsesWallpaperOnAndroid12AndFixedColorsBeforeIt() {
        for (dark in listOf(false, true)) {
            val actual = harmonicThemePalette(context, ThemeSelection(ThemePreferences.DEFAULT, dark))
            if (Build.VERSION.SDK_INT < 31) {
                assertEquals(HarmonicThemeCatalog.resolve(ThemePreferences.MATERIAL_FIXED_AUTO, dark).colors, actual.colors)
            } else {
                val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                assertEquals(scheme.primary, actual.colorScheme.primary)
                assertEquals(scheme.secondary, actual.colors.accent)
                assertEquals(scheme.background, actual.colors.readerModeBackground)
                assertEquals(if (dark) scheme.background else scheme.surfaceContainerHigh, actual.colors.background)
                assertEquals(context.getColor(android.R.color.system_accent1_600), actual.colors.commentCountIndicator.toArgb())
            }
        }
    }

    @Test
    fun dynamicColorsPreserveComposeRolesThatWereNeverOverriddenByXml() {
        for (dark in listOf(false, true)) {
            val base = HarmonicThemeCatalog.resolve(ThemePreferences.DEFAULT, dark).colorScheme
            val actual = harmonicThemePalette(context, ThemeSelection(ThemePreferences.DEFAULT, dark)).colorScheme
            assertEquals(base.tertiaryContainer, actual.tertiaryContainer)
            assertEquals(base.onTertiaryContainer, actual.onTertiaryContainer)
            assertEquals(base.surfaceContainerLowest, actual.surfaceContainerLowest)
            assertEquals(base.surfaceContainer, actual.surfaceContainer)
        }
    }

    @Test
    fun applyingAnAccentKeepsWallpaperSurfacesAndReaderBackground() {
        for (dark in listOf(false, true)) {
            val selection = ThemeSelection(ThemePreferences.DEFAULT, dark)
            val base = harmonicThemePalette(context, selection)
            val accented = harmonicThemePalette(context, selection.copy(accentPreset = ThemePreferences.ACCENT_ORANGE))
            assertEquals(ThemeAccentCatalog.apply(base, ThemePreferences.ACCENT_ORANGE).colors, accented.colors)
            assertEquals(base.colors.readerModeBackground, accented.colors.readerModeBackground)
        }
    }

    @Test
    fun nativeThemesStillResolveSplashAndWebViewDarkeningFlags() {
        for (theme in themes) for (dark in listOf(false, true)) {
            val themed = ContextThemeWrapper(context, AndroidActivityTheme.themeResource(theme, dark))
            val value = TypedValue()
            assertTrue(themed.theme.resolveAttribute(android.R.attr.windowBackground, value, true))
            if (Build.VERSION.SDK_INT >= 29) {
                assertTrue(themed.theme.resolveAttribute(android.R.attr.isLightTheme, value, true))
                // API 31+ deliberately reports a dark host to allow manual WebView darkening.
                if (Build.VERSION.SDK_INT >= 31) assertEquals("$theme/$dark", 0, value.data)
            }
            if (Build.VERSION.SDK_INT >= 31) {
                assertTrue(themed.theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
                assertEquals(R.drawable.ic_harmonic_splash_gather, value.resourceId)
            }
        }
    }
}
