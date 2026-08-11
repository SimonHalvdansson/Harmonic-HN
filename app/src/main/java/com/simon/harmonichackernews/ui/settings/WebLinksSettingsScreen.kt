package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import com.simon.harmonichackernews.utils.SettingsUtils

@Composable
fun WebLinksSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val reading = AndroidUserSettings.get(context).reading
    val state = WebLinksSettingsUiState(
        integratedWebView = reading.integratedWebView,
        closeWebViewOnBack = reading.closeWebViewOnBack,
        preloadSummary = preloadSummary(reading.preloadWebViewMode, reading.preloadWebViewMinimumBattery),
        matchWebViewTheme = reading.matchWebViewTheme,
        blockWebViewAds = reading.blockAds,
        readerModeEnabled = reading.readerModeEnabled,
        readerModeDefault = reading.readerModeDefault,
        readerModeFontLabel = SettingsUtils.getPreferredReaderModeFontLabel(context).orEmpty(),
        readerModeFontSize = reading.readerModeFontSize,
        readerModeFontSizeDefault = SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE,
        readerModeFontSizeRange = SettingsUtils.MIN_READER_MODE_FONT_SIZE..
            SettingsUtils.MAX_READER_MODE_FONT_SIZE,
        externalBrowser = reading.externalBrowser,
        redirectNitter = reading.redirectNitter,
        archiveDomainCount = reading.archiveRedirectDomains.size,
        previewArxiv = reading.previewArxiv,
        previewGithub = reading.previewGithub,
        previewGitlab = reading.previewGitlab,
        previewStackExchange = reading.previewStackExchange,
        previewWikipedia = reading.previewWikipedia,
        previewX = reading.previewX,
    )
    SharedWebLinksSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = { setting, value ->
            prefs.edit().putBoolean(setting.preferenceKey, value).apply()
        },
        onReaderFontSizeChanged = {
            prefs.edit().putInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, it).apply()
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
        WebLinksBooleanSetting.IntegratedWebView -> UserPreferenceKeys.WEBVIEW
        WebLinksBooleanSetting.CloseWebViewOnBack -> UserPreferenceKeys.CLOSE_WEBVIEW_ON_BACK
        WebLinksBooleanSetting.MatchWebViewTheme -> UserPreferenceKeys.WEBVIEW_MATCH_THEME
        WebLinksBooleanSetting.BlockWebViewAds -> UserPreferenceKeys.WEBVIEW_ADBLOCK
        WebLinksBooleanSetting.ReaderModeEnabled -> UserPreferenceKeys.READER_MODE_ENABLED
        WebLinksBooleanSetting.ReaderModeDefault -> UserPreferenceKeys.READER_MODE_DEFAULT
        WebLinksBooleanSetting.ExternalBrowser -> UserPreferenceKeys.EXTERNAL_BROWSER
        WebLinksBooleanSetting.RedirectNitter -> UserPreferenceKeys.REDIRECT_NITTER
        WebLinksBooleanSetting.PreviewArxiv -> UserPreferenceKeys.LINK_PREVIEW_ARXIV
        WebLinksBooleanSetting.PreviewGithub -> UserPreferenceKeys.LINK_PREVIEW_GITHUB
        WebLinksBooleanSetting.PreviewGitlab -> UserPreferenceKeys.LINK_PREVIEW_GITLAB
        WebLinksBooleanSetting.PreviewStackExchange -> UserPreferenceKeys.LINK_PREVIEW_STACK_EXCHANGE
        WebLinksBooleanSetting.PreviewWikipedia -> UserPreferenceKeys.LINK_PREVIEW_WIKIPEDIA
        WebLinksBooleanSetting.PreviewX -> UserPreferenceKeys.LINK_PREVIEW_X
    }

private fun preloadSummary(mode: String, battery: Int): String {
    if (mode == SettingsUtils.PRELOAD_WEBVIEW_NEVER) return "Never"
    val modeLabel = if (mode == SettingsUtils.PRELOAD_WEBVIEW_ALWAYS) "Always" else "Only on WiFi"
    return if (battery <= 0) "$modeLabel, any battery level" else "$modeLabel, battery at least $battery%"
}
