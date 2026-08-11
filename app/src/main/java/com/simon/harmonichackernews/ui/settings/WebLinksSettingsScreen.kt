package com.simon.harmonichackernews.ui.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.utils.SettingsUtils

@Composable
fun WebLinksSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val state = WebLinksSettingsUiState(
        integratedWebView = prefs.getBoolean("pref_webview", true),
        closeWebViewOnBack = prefs.getBoolean("pref_close_webview_on_back", false),
        preloadSummary = preloadSummary(context),
        matchWebViewTheme = prefs.getBoolean("pref_webview_match_theme", false),
        blockWebViewAds = prefs.getBoolean("pref_webview_adblock", false),
        readerModeEnabled = prefs.getBoolean(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED, true),
        readerModeDefault = prefs.getBoolean(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT, false),
        readerModeFontLabel = SettingsUtils.getPreferredReaderModeFontLabel(context).orEmpty(),
        readerModeFontSize = SettingsUtils.getReaderModeFontSize(context),
        readerModeFontSizeDefault = SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE,
        readerModeFontSizeRange = SettingsUtils.MIN_READER_MODE_FONT_SIZE..
            SettingsUtils.MAX_READER_MODE_FONT_SIZE,
        externalBrowser = prefs.getBoolean("pref_external_browser", false),
        redirectNitter = prefs.getBoolean("pref_redirect_nitter", false),
        archiveDomainCount = SettingsUtils.getArchiveRedirectDomains(context).size,
        previewArxiv = prefs.getBoolean("pref_link_preview_arxiv", true),
        previewGithub = prefs.getBoolean("pref_link_preview_github", true),
        previewGitlab = prefs.getBoolean("pref_link_preview_gitlab", true),
        previewStackExchange = prefs.getBoolean("pref_link_preview_stack_exchange", true),
        previewWikipedia = prefs.getBoolean("pref_link_preview_wikipedia", true),
        previewX = prefs.getBoolean("pref_link_preview_x", false),
    )
    SharedWebLinksSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            prefs.edit().putBoolean(setting.preferenceKey, value).apply()
        },
        onReaderFontSizeChanged = {
            prefs.edit().putInt(SettingsUtils.PREF_WEBVIEW_READER_MODE_FONT_SIZE, it).apply()
        },
        onDialogRequested = { dialog = it },
        contentVersion = refresh,
    )

    when (dialog) {
        WebLinksSettingsDialog.Preload -> PreloadWebViewDialog(onDismiss = { dialog = null })
        WebLinksSettingsDialog.ReaderFont -> FontSelectionDialog(
            readerMode = true,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ArchiveDomains -> ArchiveRedirectDomainsDialog(
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

private val WebLinksBooleanSetting.preferenceKey: String
    get() = when (this) {
        WebLinksBooleanSetting.IntegratedWebView -> "pref_webview"
        WebLinksBooleanSetting.CloseWebViewOnBack -> "pref_close_webview_on_back"
        WebLinksBooleanSetting.MatchWebViewTheme -> "pref_webview_match_theme"
        WebLinksBooleanSetting.BlockWebViewAds -> "pref_webview_adblock"
        WebLinksBooleanSetting.ReaderModeEnabled -> SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED
        WebLinksBooleanSetting.ReaderModeDefault -> SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT
        WebLinksBooleanSetting.ExternalBrowser -> "pref_external_browser"
        WebLinksBooleanSetting.RedirectNitter -> "pref_redirect_nitter"
        WebLinksBooleanSetting.PreviewArxiv -> "pref_link_preview_arxiv"
        WebLinksBooleanSetting.PreviewGithub -> "pref_link_preview_github"
        WebLinksBooleanSetting.PreviewGitlab -> "pref_link_preview_gitlab"
        WebLinksBooleanSetting.PreviewStackExchange -> "pref_link_preview_stack_exchange"
        WebLinksBooleanSetting.PreviewWikipedia -> "pref_link_preview_wikipedia"
        WebLinksBooleanSetting.PreviewX -> "pref_link_preview_x"
    }

private fun preloadSummary(context: Context): String {
    val mode = SettingsUtils.shouldPreloadWebView(context)
    val battery = SettingsUtils.getPreloadWebViewMinimumBattery(context)
    if (mode == SettingsUtils.PRELOAD_WEBVIEW_NEVER) return "Never"
    val modeLabel = if (mode == SettingsUtils.PRELOAD_WEBVIEW_ALWAYS) "Always" else "Only on WiFi"
    return if (battery <= 0) "$modeLabel, any battery level" else "$modeLabel, battery at least $battery%"
}
