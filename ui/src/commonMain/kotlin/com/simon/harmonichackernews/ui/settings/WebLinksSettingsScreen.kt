package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.resources.ic_block
import com.simon.harmonichackernews.resources.ic_cached
import com.simon.harmonichackernews.resources.ic_chrome_reader_mode
import com.simon.harmonichackernews.resources.ic_font_download
import com.simon.harmonichackernews.resources.ic_invert_colors
import com.simon.harmonichackernews.resources.ic_preview
import com.simon.harmonichackernews.resources.ic_open_in_browser
import com.simon.harmonichackernews.resources.ic_shuffle
import com.simon.harmonichackernews.resources.ic_web_asset
import com.simon.harmonichackernews.resources.settings_section_web_links
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource

data class WebLinksSettingsUiState(
    val integratedWebView: Boolean,
    val closeWebViewOnBack: Boolean,
    val preloadSummary: String,
    val matchWebViewTheme: Boolean,
    val blockWebViewAds: Boolean,
    val readerModeEnabled: Boolean,
    val readerModeDefault: Boolean,
    val readerModeFontLabel: String,
    val readerModeFontSize: Int,
    val readerModeFontSizeDefault: Int,
    val readerModeFontSizeRange: IntRange,
    val externalBrowser: Boolean,
    val redirectNitter: Boolean,
    val archiveDomainCount: Int,
    val enabledLinkPreviews: Set<LinkPreviewType>,
)

enum class WebLinksBooleanSetting {
    IntegratedWebView,
    CloseWebViewOnBack,
    MatchWebViewTheme,
    BlockWebViewAds,
    ReaderModeEnabled,
    ReaderModeDefault,
    ExternalBrowser,
    RedirectNitter,
}

enum class WebLinksSettingsDialog { Preload, ReaderFont, ArchiveDomains, LinkPreviews }

@Composable
fun WebLinksSettingsScreen(
    state: WebLinksSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onBooleanChanged: (WebLinksBooleanSetting, Boolean) -> Unit,
    onReaderFontSizeChanged: (Int) -> Unit,
    onDialogRequested: (WebLinksSettingsDialog) -> Unit,
    contentVersion: Int = 0,
) {
    val readerControlsEnabled = state.integratedWebView && state.readerModeEnabled
    SettingsPage(
        title = stringResource(Res.string.settings_section_web_links),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("WebView") {
                SwitchSettingRow(
                    title = "Integrated WebView",
                    summary = "Opens websites in the app which has a hit on performance",
                    icon = Res.drawable.ic_web_asset,
                    checked = state.integratedWebView,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.IntegratedWebView, it)
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Go back to comments",
                    summary = "Back navigation closes integrated WebView",
                    icon = Res.drawable.ic_arrow_back,
                    checked = state.closeWebViewOnBack,
                    enabled = state.integratedWebView,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.CloseWebViewOnBack, it)
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Preload websites",
                    summary = state.preloadSummary,
                    icon = Res.drawable.ic_cached,
                    enabled = state.integratedWebView,
                    onClick = { onDialogRequested(WebLinksSettingsDialog.Preload) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Match WebView dark mode to theme",
                    icon = Res.drawable.ic_invert_colors,
                    checked = state.matchWebViewTheme,
                    enabled = state.integratedWebView,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.MatchWebViewTheme, it)
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Block WebView ads",
                    summary = "May cause some sites to stop working and has a small performance penalty",
                    icon = Res.drawable.ic_block,
                    checked = state.blockWebViewAds,
                    enabled = state.integratedWebView,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.BlockWebViewAds, it)
                    },
                )
            }
        }
        item {
            SettingsCategory("Reader mode") {
                SwitchSettingRow(
                    title = "Enable reader mode",
                    icon = Res.drawable.ic_chrome_reader_mode,
                    checked = state.readerModeEnabled,
                    enabled = state.integratedWebView,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.ReaderModeEnabled, it)
                    },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Reader mode on by default",
                    icon = Res.drawable.ic_chrome_reader_mode,
                    checked = state.readerModeDefault,
                    enabled = readerControlsEnabled,
                    onCheckedChange = {
                        onBooleanChanged(WebLinksBooleanSetting.ReaderModeDefault, it)
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Font",
                    summary = state.readerModeFontLabel,
                    icon = Res.drawable.ic_font_download,
                    enabled = readerControlsEnabled,
                    onClick = { onDialogRequested(WebLinksSettingsDialog.ReaderFont) },
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = buildString {
                        append("${state.readerModeFontSize}px")
                        if (state.readerModeFontSize == state.readerModeFontSizeDefault) {
                            append(" (default)")
                        }
                    },
                    value = state.readerModeFontSize.toFloat(),
                    valueRange = state.readerModeFontSizeRange.first.toFloat()..
                        state.readerModeFontSizeRange.last.toFloat(),
                    steps = state.readerModeFontSizeRange.last -
                        state.readerModeFontSizeRange.first - 1,
                    enabled = readerControlsEnabled,
                    onValueChange = { onReaderFontSizeChanged(it.toInt()) },
                )
            }
        }
        item {
            SettingsCategory("Browser and links") {
                BooleanSettingRow(
                    "Use external browser",
                    Res.drawable.ic_open_in_browser,
                    state.externalBrowser,
                    WebLinksBooleanSetting.ExternalBrowser,
                    onBooleanChanged,
                    summary = "In place of custom tabs",
                )
                SettingsDivider()
                BooleanSettingRow(
                    "Redirect Twitter/X to Nitter",
                    Res.drawable.ic_shuffle,
                    state.redirectNitter,
                    WebLinksBooleanSetting.RedirectNitter,
                    onBooleanChanged,
                )
                SettingsDivider()
                SettingRow(
                    title = "Redirect to archive version",
                    summary = when (state.archiveDomainCount) {
                        0 -> "No domains"
                        1 -> "1 domain"
                        else -> "${state.archiveDomainCount} domains"
                    },
                    icon = Res.drawable.ic_shuffle,
                    onClick = { onDialogRequested(WebLinksSettingsDialog.ArchiveDomains) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Link previews",
                    summary = "${state.enabledLinkPreviews.size} of ${LinkPreviewType.entries.size} enabled",
                    icon = Res.drawable.ic_preview,
                    onClick = { onDialogRequested(WebLinksSettingsDialog.LinkPreviews) },
                )
            }
        }
    }
}

@Composable
private fun BooleanSettingRow(
    title: String,
    icon: DrawableResource,
    checked: Boolean,
    setting: WebLinksBooleanSetting,
    onBooleanChanged: (WebLinksBooleanSetting, Boolean) -> Unit,
    summary: String? = null,
) {
    SwitchSettingRow(
        title = title,
        summary = summary,
        icon = icon,
        checked = checked,
        onCheckedChange = { onBooleanChanged(setting, it) },
    )
}
