package com.simon.harmonichackernews.ui.settings

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.utils.SettingsUtils
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
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<AppearanceSettingsDialog?>(null) }
    val theme = prefs.getString(SettingsUtils.PREF_THEME, SettingsUtils.DEFAULT_THEME)
        ?: SettingsUtils.DEFAULT_THEME
    val nighttimeTheme = prefs.getString(
        SettingsUtils.PREF_THEME_NIGHTTIME,
        SettingsUtils.DEFAULT_NIGHTTIME_THEME,
    ) ?: SettingsUtils.DEFAULT_NIGHTTIME_THEME
    val settings = AndroidUserSettings.get(context)
    val tintEnabled = settings.story.tintCardUsingPreview
    val state = AppearanceSettingsUiState(
        themeLabel = composeThemeLabel(theme),
        specialNighttime = settings.general.specialNighttimeTheme,
        nighttimeRangeLabel = formatNighttimeRange(context),
        nighttimeThemeLabel = composeThemeLabel(
            nighttimeTheme,
            SettingsUtils.DEFAULT_NIGHTTIME_THEME,
        ),
        fontLabel = SettingsUtils.getPreferredFontLabel(context).orEmpty(),
        paletteTintSummary = if (tintEnabled) {
            SettingsUtils.getPreferredPaletteTintSummary(context)
        } else {
            "Enable in Stories settings"
        },
        paletteTintEnabled = tintEnabled,
        showTransparentStatusBar = resources.getBoolean(R.bool.before_android_15),
        transparentStatusBar = settings.general.transparentStatusBar,
        compactHeader = settings.story.compactHeader,
    )
    SharedAppearanceSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onNavigate = onNavigate,
        onBooleanChanged = { setting, value ->
            prefs.edit().putBoolean(setting.preferenceKey, value).apply()
            if (setting != AppearanceBooleanSetting.CompactHeader) onThemeChanged()
        },
        onDialogRequested = { dialog = it },
        contentVersion = refresh,
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

private val AppearanceBooleanSetting.preferenceKey: String
    get() = when (this) {
        AppearanceBooleanSetting.SpecialNighttime -> UserPreferenceKeys.SPECIAL_NIGHTTIME
        AppearanceBooleanSetting.TransparentStatusBar -> UserPreferenceKeys.TRANSPARENT_STATUS_BAR
        AppearanceBooleanSetting.CompactHeader -> UserPreferenceKeys.COMPACT_HEADER
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
