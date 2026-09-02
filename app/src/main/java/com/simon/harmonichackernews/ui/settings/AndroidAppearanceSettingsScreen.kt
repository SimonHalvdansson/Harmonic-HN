package com.simon.harmonichackernews.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

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
