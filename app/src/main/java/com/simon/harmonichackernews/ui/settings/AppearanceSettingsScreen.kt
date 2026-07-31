package com.simon.harmonichackernews.ui.settings

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
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
    var refreshToken by remember { mutableIntStateOf(0) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(prefs) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                refreshToken++
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refreshToken
    val theme = prefs.getString(SettingsUtils.PREF_THEME, SettingsUtils.DEFAULT_THEME)
        ?: SettingsUtils.DEFAULT_THEME
    val nighttimeTheme = prefs.getString(
        SettingsUtils.PREF_THEME_NIGHTTIME,
        SettingsUtils.DEFAULT_NIGHTTIME_THEME,
    ) ?: SettingsUtils.DEFAULT_NIGHTTIME_THEME
    val specialNighttime = prefs.getBoolean("pref_special_nighttime", false)
    val compactHeader = prefs.getBoolean("pref_compact_header", false)
    val transparentStatusBar = prefs.getBoolean("pref_transparent_status_bar", false)

    SettingsPage(
        title = "Appearance",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refreshToken,
    ) {
        item {
            SettingsCategory("Theme") {
                SettingRow(
                    title = "Theme",
                    summary = composeThemeLabel(theme),
                    icon = R.drawable.ic_style,
                    onClick = { dialog = "theme" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Special nighttime theme",
                    icon = R.drawable.ic_nights_stay,
                    checked = specialNighttime,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_special_nighttime", it).apply()
                        onThemeChanged()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Timed range",
                    summary = formatNighttimeRange(context),
                    icon = R.drawable.ic_schedule,
                    enabled = specialNighttime,
                    onClick = { dialog = "nighttime_range" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Nighttime theme",
                    summary = composeThemeLabel(
                        nighttimeTheme,
                        SettingsUtils.DEFAULT_NIGHTTIME_THEME,
                    ),
                    icon = R.drawable.ic_dark_mode,
                    enabled = specialNighttime,
                    onClick = { dialog = "nighttime_theme" },
                )
            }
        }

        item {
            SettingsCategory("Visual") {
                SettingRow(
                    title = "Title and comment font",
                    summary = SettingsUtils.getPreferredFontLabel(context),
                    icon = R.drawable.ic_font_download,
                    onClick = { dialog = "font" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Palette tint",
                    summary = if (SettingsUtils.shouldTintCardUsingPreview(context)) {
                        SettingsUtils.getPreferredPaletteTintSummary(context)
                    } else {
                        "Enable in Stories settings"
                    },
                    icon = R.drawable.ic_palette,
                    enabled = SettingsUtils.shouldTintCardUsingPreview(context),
                    onClick = { dialog = "palette_tint" },
                )
                if (resources.getBoolean(R.bool.before_android_15)) {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Transparent status bar",
                        icon = R.drawable.ic_visibility,
                        checked = transparentStatusBar,
                        onCheckedChange = {
                            prefs.edit()
                                .putBoolean("pref_transparent_status_bar", it)
                                .apply()
                            onThemeChanged()
                        },
                    )
                }
                SettingsDivider()
                SwitchSettingRow(
                    title = "Compact header",
                    summary = "Smaller margins for 'Top stories' header",
                    icon = R.drawable.ic_horizontal_split,
                    checked = compactHeader,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_compact_header", it).apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Style") {
                SettingRow(
                    title = "General style",
                    icon = R.drawable.ic_design_services,
                    onClick = { dialog = "style" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Stories settings",
                    icon = R.drawable.ic_open_in_new,
                    onClick = { onNavigate(SettingsSection.Stories) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Comments settings",
                    icon = R.drawable.ic_open_in_new,
                    onClick = { onNavigate(SettingsSection.Comments) },
                )
            }
        }
    }

    when (dialog) {
        "theme" -> ThemeSelectionDialog(
            nighttime = false,
            onDismiss = { dialog = null },
            onThemeChanged = onThemeChanged,
        )
        "nighttime_theme" -> ThemeSelectionDialog(
            nighttime = true,
            onDismiss = { dialog = null },
            onThemeChanged = onThemeChanged,
        )
        "nighttime_range" -> NighttimeRangeDialog(
            onDismiss = { dialog = null },
            onRangeSelected = onThemeChanged,
        )
        "font" -> FontSelectionDialog(
            readerMode = false,
            onDismiss = { dialog = null },
        )
        "style" -> WelcomeSettingsDialog(
            styleChooser = true,
            onDismiss = { dialog = null },
        )
        "palette_tint" -> PaletteTintDialog(
            onDismiss = { dialog = null },
        )
    }
}

private fun formatNighttimeRange(context: android.content.Context): String {
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
    return formatter.format(Date(0, 0, 0, hours[0], hours[1])) +
        " - " +
        formatter.format(Date(0, 0, 0, hours[2], hours[3]))
}
