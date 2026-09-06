package com.simon.harmonichackernews.ui.settings

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicThemePalette
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette
import com.simon.harmonichackernews.utils.ThemeUtils

@Composable
fun AndroidAppearanceSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
) {
    val resources = LocalResources.current
    val app = LocalHarmonicUiDependencies.current
    val repository = app.settings
    AppearanceSettingsRoute(
        repository = repository,
        labels = AppearanceRouteLabels(
            showTransparentStatusBar = resources.getBoolean(R.bool.before_android_15),
            materialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeChanged = onThemeChanged,
        dialogContent = { dialog, _, dismiss ->
            when (dialog) {
                AppearanceSettingsDialog.Theme -> AndroidThemeSelectionDialog(
                    nighttime = false,
                    onDismiss = dismiss,
                    onThemeChanged = onThemeChanged,
                )
                AppearanceSettingsDialog.NighttimeTheme -> AndroidThemeSelectionDialog(
                    nighttime = true,
                    onDismiss = dismiss,
                    onThemeChanged = onThemeChanged,
                )
                AppearanceSettingsDialog.NighttimeRange -> AndroidNighttimeRangeDialog(
                    onDismiss = dismiss,
                    onRangeSelected = onThemeChanged,
                )
                AppearanceSettingsDialog.Font -> FontSelectionRoute(
                    readerMode = false,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Style -> AndroidWelcomeSettingsDialog(
                    styleChooser = true,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.PaletteTint -> AndroidPaletteTintDialog(onDismiss = dismiss)
            }
        },
    )
}

@Composable
fun AndroidThemeSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onThemeChanged: () -> Unit,
) {
    val context = LocalContext.current
    val currentConfiguration = LocalConfiguration.current
    val previewThemes = remember(context, currentConfiguration) {
        mutableMapOf<Triple<String, Boolean, String>, HarmonicThemePalette>()
    }
    val app = LocalHarmonicUiDependencies.current
    val materialYouAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ThemeSettingsRoute(
        repository = app.settings,
        labels = ThemeRouteLabels(
            nighttimeRange = formatNighttimeRange(
                app.appearance.schedule,
                app.platform.timeFormatting.uses24HourClock(),
            ),
            activeTheme = app.appearance.selection().theme,
            materialYouAvailable = materialYouAvailable,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onThemeChanged = onThemeChanged,
        resolvePreviewTheme = { theme, dark, accent ->
            previewThemes.getOrPut(Triple(theme, dark, accent)) {
                val configuration = Configuration(currentConfiguration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }
                val themedContext = ContextThemeWrapper(
                    context.createConfigurationContext(configuration),
                    ThemeUtils.themeResource(theme, dark),
                )
                harmonicThemePalette(themedContext, ThemeSelection(theme, dark, accent))
            }
        },
        dialogContent = { dialog, presenter, dismiss ->
            when (dialog) {
                ThemeSettingsDialog.LightTheme -> ThemeSelectionDialog(
                    nighttime = false,
                    selected = presenter.snapshot.appearance.lightTheme,
                    materialYouAvailable = materialYouAvailable,
                    selectionKind = ThemeSelectionKind.Light,
                    title = "Light theme",
                    onThemeSelected = { theme ->
                        presenter.setLightTheme(theme)
                        onThemeChanged()
                        dismiss()
                    },
                    onDismiss = dismiss,
                    previewPalettes = {
                        ThemePreviewCatalog.palettes(
                            it,
                            presenter.snapshot.appearance.accentPreset,
                        )
                    },
                )
                ThemeSettingsDialog.DarkTheme -> ThemeSelectionDialog(
                    nighttime = false,
                    selected = presenter.snapshot.appearance.darkTheme,
                    materialYouAvailable = materialYouAvailable,
                    selectionKind = ThemeSelectionKind.Dark,
                    title = "Dark theme",
                    onThemeSelected = { theme ->
                        presenter.setDarkTheme(theme)
                        onThemeChanged()
                        dismiss()
                    },
                    onDismiss = dismiss,
                    previewPalettes = {
                        ThemePreviewCatalog.palettes(
                            it,
                            presenter.snapshot.appearance.accentPreset,
                        )
                    },
                )
                ThemeSettingsDialog.NighttimeTheme -> ThemeSelectionDialog(
                    nighttime = true,
                    selected = presenter.snapshot.appearance.nighttimeTheme,
                    materialYouAvailable = materialYouAvailable,
                    selectionKind = ThemeSelectionKind.Dark,
                    onThemeSelected = { theme ->
                        presenter.setTheme(theme, nighttime = true)
                        onThemeChanged()
                        dismiss()
                    },
                    onDismiss = dismiss,
                    previewPalettes = {
                        ThemePreviewCatalog.palettes(
                            it,
                            presenter.snapshot.appearance.accentPreset,
                        )
                    },
                )
                ThemeSettingsDialog.NighttimeRange -> AndroidNighttimeRangeDialog(
                    onDismiss = dismiss,
                    onRangeSelected = onThemeChanged,
                )
            }
        },
    )
}

private fun formatNighttimeRange(
    schedule: com.simon.harmonichackernews.settings.NighttimeSchedule,
    use24HourClock: Boolean,
): String = ThemeSelectionPolicy.formatSchedule(
    schedule = schedule,
    use24HourClock = use24HourClock,
)
