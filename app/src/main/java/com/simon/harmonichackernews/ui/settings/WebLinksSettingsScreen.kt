package com.simon.harmonichackernews.ui.settings

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ArchiveRedirectDomainsDialogFragment
import com.simon.harmonichackernews.settings.FontSelectionDialogFragment
import com.simon.harmonichackernews.settings.PreloadWebViewDialogFragment
import com.simon.harmonichackernews.utils.SettingsUtils

@Composable
fun WebLinksSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh

    val integratedWebView = prefs.getBoolean("pref_webview", true)
    val readerModeEnabled = prefs.getBoolean(
        SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED,
        true,
    )
    val readerControlsEnabled = integratedWebView && readerModeEnabled
    val archiveDomains = SettingsUtils.getArchiveRedirectDomains(context)

    SettingsPage(
        title = "Web and links",
        showNavigation = showNavigation,
        onBack = onBack,
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
                    onClick = {
                        activity?.supportFragmentManager?.let(
                            PreloadWebViewDialogFragment::show,
                        )
                    },
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
                    onClick = {
                        activity?.supportFragmentManager?.let(
                            FontSelectionDialogFragment::showReaderMode,
                        )
                    },
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = "${SettingsUtils.getReaderModeFontSize(context)}px",
                    value = SettingsUtils.getReaderModeFontSize(context).toFloat(),
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
                    onClick = {
                        activity?.supportFragmentManager?.let(
                            ArchiveRedirectDomainsDialogFragment::show,
                        )
                    },
                )
            }
        }

        item {
            SettingsCategory("Link previews") {
                LinkPreviewSwitch(
                    title = "ArXiV",
                    icon = R.drawable.ic_link_preview_arxiv,
                    key = "pref_link_preview_arxiv",
                    default = true,
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "GitHub",
                    icon = R.drawable.ic_link_preview_github,
                    key = "pref_link_preview_github",
                    default = true,
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "GitLab",
                    icon = R.drawable.ic_link_preview_gitlab,
                    key = "pref_link_preview_gitlab",
                    default = true,
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "Stack Exchange",
                    icon = R.drawable.ic_link_preview_stack_exchange,
                    key = "pref_link_preview_stack_exchange",
                    default = true,
                )
                SettingsDivider()
                LinkPreviewSwitch(
                    title = "Wikipedia",
                    icon = R.drawable.ic_link_preview_wikipedia,
                    key = "pref_link_preview_wikipedia",
                    default = true,
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
}

@Composable
private fun LinkPreviewSwitch(
    title: String,
    icon: Int,
    key: String,
    default: Boolean,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    SwitchSettingRow(
        title = title,
        icon = icon,
        checked = prefs.getBoolean(key, default),
        onCheckedChange = {
            prefs.edit().putBoolean(key, it).apply()
        },
    )
}

private fun preloadSummary(context: android.content.Context): String {
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
