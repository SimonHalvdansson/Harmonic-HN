package com.simon.harmonichackernews.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils

@Composable
fun WebLinksSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    val integratedWebView = prefs.getBoolean("pref_webview", true)
    val readerModeEnabled = prefs.getBoolean(
        SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED,
        true,
    )
    val readerControlsEnabled = integratedWebView && readerModeEnabled
    val readerModeFontSize = SettingsUtils.getReaderModeFontSize(context)
    val archiveDomains = SettingsUtils.getArchiveRedirectDomains(context)

    SettingsPage(
        title = "Web and links",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = refresh,
    ) {
        item {
            SettingsCategory("WebView") {
                SwitchSettingRow(
                    title = "Integrated WebView",
                    summary = "Opens websites in the app which has a hit on performance",
                    icon = R.drawable.ic_web_asset,
                    checked = integratedWebView,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_webview", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Go back to comments",
                    summary = "Back navigation closes integrated WebView",
                    icon = R.drawable.ic_arrow_back,
                    checked = prefs.getBoolean("pref_close_webview_on_back", false),
                    enabled = integratedWebView,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_close_webview_on_back", it).apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Preload websites",
                    summary = preloadSummary(context),
                    icon = R.drawable.ic_cached,
                    enabled = integratedWebView,
                    onClick = { dialog = "preload" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Match WebView dark mode to theme",
                    icon = R.drawable.ic_invert_colors,
                    checked = prefs.getBoolean("pref_webview_match_theme", false),
                    enabled = integratedWebView,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_webview_match_theme", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Block WebView ads",
                    summary = "May cause some sites to stop working and has a small performance penalty",
                    icon = R.drawable.ic_block,
                    checked = prefs.getBoolean("pref_webview_adblock", false),
                    enabled = integratedWebView,
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_webview_adblock", it).apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Reader mode") {
                SwitchSettingRow(
                    title = "Enable reader mode",
                    icon = R.drawable.ic_chrome_reader_mode,
                    checked = readerModeEnabled,
                    enabled = integratedWebView,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Reader mode on by default",
                    icon = R.drawable.ic_chrome_reader_mode,
                    checked = prefs.getBoolean(
                        SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT,
                        false,
                    ),
                    enabled = readerControlsEnabled,
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT, it)
                            .apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Font",
                    summary = SettingsUtils.getPreferredReaderModeFontLabel(context),
                    icon = R.drawable.ic_font_download,
                    enabled = readerControlsEnabled,
                    onClick = { dialog = "reader_font" },
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = buildString {
                        append("${readerModeFontSize}px")
                        if (readerModeFontSize == SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE) {
                            append(" (default)")
                        }
                    },
                    value = readerModeFontSize.toFloat(),
                    valueRange = SettingsUtils.MIN_READER_MODE_FONT_SIZE.toFloat()..
                        SettingsUtils.MAX_READER_MODE_FONT_SIZE.toFloat(),
                    steps = SettingsUtils.MAX_READER_MODE_FONT_SIZE -
                        SettingsUtils.MIN_READER_MODE_FONT_SIZE - 1,
                    enabled = readerControlsEnabled,
                    onValueChange = {
                        prefs.edit()
                            .putInt(
                                SettingsUtils.PREF_WEBVIEW_READER_MODE_FONT_SIZE,
                                it.toInt(),
                            )
                            .apply()
                    },
                )
            }
        }

        item {
            SettingsCategory("Browser and links") {
                SwitchSettingRow(
                    title = "Use external browser",
                    summary = "In place of custom tabs",
                    icon = R.drawable.ic_open_in_browser,
                    checked = prefs.getBoolean("pref_external_browser", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_external_browser", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Redirect Twitter/X to Nitter",
                    icon = R.drawable.ic_shuffle,
                    checked = prefs.getBoolean("pref_redirect_nitter", false),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_redirect_nitter", it).apply()
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Redirect to archive version",
                    summary = when (archiveDomains.size) {
                        0 -> "No domains"
                        1 -> "1 domain"
                        else -> "${archiveDomains.size} domains"
                    },
                    icon = R.drawable.ic_shuffle,
                    onClick = { dialog = "archive_domains" },
                )
            }
        }

        item {
            SettingsCategory("Link previews") {
                LinkPreviewSwitch(
                    title = "ArXiV",
                    icon = R.drawable.ic_link_preview_arxiv,
                    checked = prefs.getBoolean("pref_link_preview_arxiv", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_link_preview_arxiv", it).apply()
                    },
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "GitHub",
                    icon = R.drawable.ic_link_preview_github,
                    checked = prefs.getBoolean("pref_link_preview_github", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_link_preview_github", it).apply()
                    },
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "GitLab",
                    icon = R.drawable.ic_link_preview_gitlab,
                    checked = prefs.getBoolean("pref_link_preview_gitlab", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_link_preview_gitlab", it).apply()
                    },
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "Stack Exchange",
                    icon = R.drawable.ic_link_preview_stack_exchange,
                    checked = prefs.getBoolean("pref_link_preview_stack_exchange", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_link_preview_stack_exchange", it).apply()
                    },
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "Wikipedia",
                    icon = R.drawable.ic_link_preview_wikipedia,
                    checked = prefs.getBoolean("pref_link_preview_wikipedia", true),
                    onCheckedChange = {
                        prefs.edit().putBoolean("pref_link_preview_wikipedia", it).apply()
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Twitter/X (unstable)",
                    summary = "Enables Nitter redirect automatically",
                    icon = R.drawable.ic_link_preview_x,
                    checked = prefs.getBoolean("pref_link_preview_x", false),
                    onCheckedChange = {
                        prefs.edit()
                            .putBoolean("pref_link_preview_x", it)
                            .apply()
                        if (it) {
                            prefs.edit().putBoolean("pref_redirect_nitter", true).apply()
                        }
                    },
                )
            }
        }
    }

    when (dialog) {
        "preload" -> PreloadWebViewDialog(onDismiss = { dialog = null })
        "reader_font" -> FontSelectionDialog(
            readerMode = true,
            onDismiss = { dialog = null },
        )
        "archive_domains" -> ArchiveRedirectDomainsDialog(
            onDismiss = { dialog = null },
        )
    }
}

@Composable
private fun LinkPreviewSwitch(
    title: String,
    icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchSettingRow(
        title = title,
        icon = icon,
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

private fun preloadSummary(context: Context): String {
    val mode = SettingsUtils.shouldPreloadWebView(context)
    val battery = SettingsUtils.getPreloadWebViewMinimumBattery(context)
    if (mode == SettingsUtils.PRELOAD_WEBVIEW_NEVER) {
        return "Never"
    }
    val modeLabel = if (mode == SettingsUtils.PRELOAD_WEBVIEW_ALWAYS) {
        "Always"
    } else {
        "Only on WiFi"
    }
    return if (battery <= 0) {
        "$modeLabel, any battery level"
    } else {
        "$modeLabel, battery at least $battery%"
    }
}
