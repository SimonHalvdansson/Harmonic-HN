package com.simon.harmonichackernews.ui.settings

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
            nighttimeRange = formatNighttimeRange(
                app.appearance.schedule,
                app.platform.timeFormatting.uses24HourClock(),
            ),
            showTransparentStatusBar = resources.getBoolean(R.bool.before_android_15),
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

private fun formatNighttimeRange(
    schedule: com.simon.harmonichackernews.settings.NighttimeSchedule,
    use24HourClock: Boolean,
): String = ThemeSelectionPolicy.formatSchedule(
    schedule = schedule,
    use24HourClock = use24HourClock,
)
