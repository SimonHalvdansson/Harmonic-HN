package com.simon.harmonichackernews

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.ColorSchemeCatalog
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
    private val themes = ColorSchemeCatalog.options.map { it.value }

    @Test
    fun everySchemeIgnoresStaleHostConfiguration() {
        for (theme in ColorSchemeCatalog.options.map { it.value }) for (dark in listOf(false, true)) {
            val selection = ThemeSelection.forScheme(theme, dark)
            val stale = ContextThemeWrapper(context, AndroidActivityTheme.themeResource(ThemeSelection.forScheme("classic", false)))
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_NO else Configuration.UI_MODE_NIGHT_YES
            }
            val expected = harmonicThemePalette(context, selection)
            val actual = harmonicThemePalette(stale.createConfigurationContext(config), selection)
            assertEquals("$theme/$dark", expected.colorScheme.toString(), actual.colorScheme.toString())
            assertEquals("$theme/$dark", expected.colorScheme.primary, actual.colorScheme.primary)
        }
    }

    @Test
    fun presetsAndCustomThemesHaveNoAndroidResourceOverrides() {
        for (theme in ColorSchemeCatalog.options.map { it.value }.filter { it != "dynamic" }) for (dark in listOf(false, true)) for (style in ColorSchemeStyle.entries) {
            val expected = HarmonicThemeCatalog.scheme(theme, dark, style)
            val actual = harmonicThemePalette(context, ThemeSelection.forScheme(theme, dark, style))
            assertEquals(expected.colorScheme.toString(), actual.colorScheme.toString())
        }
    }

    @Test
    fun dynamicSelectionUsesTheCompleteWallpaperSchemeOrPortableFallback() {
        for (dark in listOf(false, true)) for (style in ColorSchemeStyle.entries) {
            val actual = harmonicThemePalette(context, ThemeSelection.forScheme("dynamic", dark, style)).colorScheme
            val expected = if (Build.VERSION.SDK_INT < 31) {
                HarmonicThemeCatalog.scheme("dynamic", dark).colorScheme
            } else if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            assertEquals(expected.toString(), actual.toString())
        }
    }

    @Test
    fun nativeThemesStillResolveSplashAndWebViewDarkeningFlags() {
        for (theme in themes) for (dark in listOf(false, true)) {
            val themed = ContextThemeWrapper(context, AndroidActivityTheme.themeResource(ThemeSelection.forScheme(theme, dark)))
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
