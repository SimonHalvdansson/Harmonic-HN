package com.simon.harmonichackernews.ui.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicThemePalette
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette

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
            dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            showExtraSidePadding = dimensionResource(R.dimen.extra_pane_padding) > 0.dp,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onThemeChanged = onThemeChanged,
        dialogContent = { dialog, _, dismiss ->
            when (dialog) {
                AppearanceSettingsDialog.Font -> FontSelectionRoute(
                    readerMode = false,
                    onDismiss = dismiss,
                )
                AppearanceSettingsDialog.Style -> AndroidWelcomeSettingsDialog(
                    styleChooser = true,
                    onDismiss = dismiss,
                )
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
        mutableMapOf<Triple<String, Boolean, ColorSchemeStyle>, HarmonicThemePalette>()
    }
    val app = LocalHarmonicUiDependencies.current
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ThemeSettingsRoute(
        repository = app.settings,
        labels = ThemeRouteLabels(
            nighttimeRange = formatNighttimeRange(
                app.appearance.schedule,
                app.platform.timeFormatting.uses24HourClock(),
            ),
            activeDark = app.appearance.selection().dark,
            dynamicColorAvailable = dynamicColorAvailable,
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onThemeChanged = onThemeChanged,
        resolvePreviewScheme = { scheme, dark, style ->
            previewThemes.getOrPut(Triple(scheme, dark, style)) {
                harmonicThemePalette(context, ThemeSelection.forScheme(scheme, dark, style))
            }
        },
        dialogContent = { dialog, presenter, dismiss ->
            when (dialog) {
                ThemeSettingsDialog.NighttimeRange -> AndroidNighttimeRangeDialog(
                    onDismiss = dismiss,
                    onRangeSelected = onThemeChanged,
                )
            }
        },
    )
}

private fun formatNighttimeRange(
    schedule: NighttimeSchedule,
    use24HourClock: Boolean,
): String = ThemeSelectionPolicy.formatSchedule(
    schedule = schedule,
    use24HourClock = use24HourClock,
)

@Composable
fun AndroidPaletteTintSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val palettes = remember(context, configuration) {
        mutableMapOf<Triple<String, Boolean, ColorSchemeStyle>, HarmonicThemePalette>()
    }
    PaletteTintSettingsRoute(
        repository = LocalHarmonicUiDependencies.current.settings,
        showNavigation = showNavigation,
        onBack = onBack,
        previewThemeEffect = { dark ->
            DisposableEffect(activity, dark) {
                val controller = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }
                val statusLight = controller?.isAppearanceLightStatusBars
                val navigationLight = controller?.isAppearanceLightNavigationBars
                controller?.isAppearanceLightStatusBars = !dark
                controller?.isAppearanceLightNavigationBars = !dark
                onDispose {
                    if (statusLight != null) controller.isAppearanceLightStatusBars = statusLight
                    if (navigationLight != null) controller.isAppearanceLightNavigationBars = navigationLight
                }
            }
        },
        resolvePreviewScheme = { scheme, dark, style ->
            palettes.getOrPut(Triple(scheme, dark, style)) {
                harmonicThemePalette(context, ThemeSelection.forScheme(scheme, dark, style))
            }
        },
    )
}
