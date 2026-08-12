package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import com.simon.harmonichackernews.settings.ThemePreferences
import com.simon.harmonichackernews.utils.Utils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppearanceSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onThemeChanged: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var dialog by rememberSaveable { mutableStateOf<AppearanceSettingsDialog?>(null) }
    val app = remember(context) { AndroidAppComposition.get(context) }
    val repository = app.settings
    val presenter = remember(app) { AppearanceSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val theme = settings.appearance.theme
    val nighttimeTheme = settings.appearance.nighttimeTheme
    val state = presenter.state(
        settings = settings,
        themeLabel = composeThemeLabel(theme),
        nighttimeRangeLabel = formatNighttimeRange(context),
        nighttimeThemeLabel = composeThemeLabel(
            nighttimeTheme,
            ThemePreferences.DEFAULT_NIGHTTIME,
        ),
        fontLabel = AndroidSettingsResources.fontLabel(context, settings.story.fontChoice),
        showTransparentStatusBar = resources.getBoolean(R.bool.before_android_15),
    )
    SharedAppearanceSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onBooleanChanged = { setting, value ->
            presenter.setBoolean(setting, value).forEach { effect ->
                if (effect == SettingsPlatformEffect.ThemeChanged) onThemeChanged()
            }
        },
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )

    when (dialog) {
        AppearanceSettingsDialog.Theme -> ThemeSelectionDialog(
            nighttime = false,
            onDismiss = { dialog = null },
            onThemeChanged = onThemeChanged,
        )
        AppearanceSettingsDialog.NighttimeTheme -> ThemeSelectionDialog(
            nighttime = true,
            onDismiss = { dialog = null },
            onThemeChanged = onThemeChanged,
        )
        AppearanceSettingsDialog.NighttimeRange -> NighttimeRangeDialog(
            onDismiss = { dialog = null },
            onRangeSelected = onThemeChanged,
        )
        AppearanceSettingsDialog.Font -> FontSelectionDialog(
            readerMode = false,
            onDismiss = { dialog = null },
        )
        AppearanceSettingsDialog.Style -> WelcomeSettingsDialog(
            styleChooser = true,
            onDismiss = { dialog = null },
        )
        AppearanceSettingsDialog.PaletteTint -> PaletteTintDialog(onDismiss = { dialog = null })
        null -> Unit
    }
}

private fun formatNighttimeRange(context: Context): String {
    val hours = Utils.getNighttimeHours(context)
    if (DateFormat.is24HourFormat(context)) {
        return String.format(
            Locale.getDefault(),
            "%02d:%02d - %02d:%02d",
            hours[0],
            hours[1],
            hours[2],
            hours[3],
        )
    }
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    @Suppress("DEPRECATION")
    val start = formatter.format(Date(0, 0, 0, hours[0], hours[1]))
    @Suppress("DEPRECATION")
    val end = formatter.format(Date(0, 0, 0, hours[2], hours[3]))
    return "$start - $end"
}
