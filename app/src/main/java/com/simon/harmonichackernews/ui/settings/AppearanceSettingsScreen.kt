package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.AndroidAppearanceState

@Composable
fun AppearanceSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val repository = LocalHarmonicUiDependencies.current.settings
    SharedAppearanceSettingsRoute(
        repository = repository,
        labels = AppearanceRouteLabels(
            nighttimeRange = formatNighttimeRange(context),
            showTransparentStatusBar = resources.getBoolean(R.bool.before_android_15),
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeChanged = onThemeChanged,
        dialogContent = { dialog, _, dismiss ->
            when (dialog) {
                AppearanceSettingsDialog.Theme -> ThemeSelectionDialog(
                    nighttime = false,
                    onDismiss = dismiss,
                    onThemeChanged = onThemeChanged,
                )
                AppearanceSettingsDialog.NighttimeTheme -> ThemeSelectionDialog(
                    nighttime = true,
                    onDismiss = dismiss,
                    onThemeChanged = onThemeChanged,
                )
                AppearanceSettingsDialog.NighttimeRange -> NighttimeRangeDialog(
                    onDismiss = dismiss,
                    onRangeSelected = onThemeChanged,
                )
                AppearanceSettingsDialog.Font -> FontSelectionDialog(
                    readerMode = false,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Style -> WelcomeSettingsDialog(
                    styleChooser = true,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.PaletteTint -> PaletteTintDialog(onDismiss = dismiss)
            }
        },
    )
}

private fun formatNighttimeRange(context: Context): String = ThemeSelectionPolicy.formatSchedule(
    schedule = AndroidAppearanceState.nighttimeScheduleValue(context),
    use24HourClock = DateFormat.is24HourFormat(context),
)
