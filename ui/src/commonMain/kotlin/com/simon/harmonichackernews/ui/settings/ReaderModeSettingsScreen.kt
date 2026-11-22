package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.ReaderLineHeight
import com.simon.harmonichackernews.settings.ReadingPreferences
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import com.simon.harmonichackernews.settings.TextPreferences

@Composable
fun ReaderModeSettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
    preview: @Composable (ReadingPreferences, Boolean) -> Unit,
    miniPlayerSettingVisible: Boolean = true,
    naturalVoiceAvailable: Boolean = false,
) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val reading = settings.reading
    val controlsEnabled = reading.integratedWebView && reading.readerModeEnabled
    var showFontPicker by rememberSaveable { mutableStateOf(false) }
    SettingsPage(
        title = "Reader mode",
        showNavigation = true,
        onBack = onBack,
        contentVersion = reading.hashCode(),
    ) {
        item {
            SettingsMainToggle(
                title = "Use reader mode",
                checked = reading.readerModeEnabled,
                enabled = reading.integratedWebView,
                summary = if (reading.integratedWebView) null else "Enable the in-app browser to use reader mode",
                onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READER_MODE_ENABLED, it) },
            )
        }
        item { preview(reading, !showFontPicker) }
        item {
            SettingsCategory("Reading") {
                SwitchSettingRow(
                    title = "Activate automatically",
                    summary = "On page load, when available",
                    icon = Res.drawable.ic_book_ribbon,
                    checked = reading.readerModeDefault,
                    enabled = controlsEnabled,
                    onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READER_MODE_DEFAULT, it) },
                )
                SettingsDivider()
                SettingRow(title = "Font", summary = reading.readerModeFont.label,
                    icon = Res.drawable.ic_font_download, enabled = controlsEnabled, onClick = { showFontPicker = true })
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = "${reading.readerModeFontSize}px" +
                        if (reading.readerModeFontSize == TextPreferences.DEFAULT_READER_MODE_FONT_SIZE) " (default)" else "",
                    value = reading.readerModeFontSize.toFloat(),
                    valueRange = TextPreferences.MIN_READER_MODE_FONT_SIZE.toFloat()..TextPreferences.MAX_READER_MODE_FONT_SIZE.toFloat(),
                    steps = TextPreferences.MAX_READER_MODE_FONT_SIZE - TextPreferences.MIN_READER_MODE_FONT_SIZE - 1,
                    enabled = controlsEnabled,
                    onValueChange = { repository.setReaderModeFontSize(it.toInt()) },
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Line height",
                    options = ReaderLineHeight.entries.map { it to it.label },
                    selected = reading.readerModeLineHeight,
                    enabled = controlsEnabled,
                    onSelected = repository::setReaderModeLineHeight,
                )
            }
        }
        item {
            SettingsCategory("Text to speech") {
                SwitchSettingRow(
                    title = "Read aloud",
                    summary = "Show the Listen button in reader mode",
                    icon = Res.drawable.ic_volume_up,
                    checked = reading.readAloudEnabled,
                    enabled = controlsEnabled,
                    onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_ENABLED, it) },
                )
                val speechEnabled = controlsEnabled && reading.readAloudEnabled
                if (miniPlayerSettingVisible) {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Show mini-player",
                        summary = "Keep playback controls visible after leaving an article",
                        icon = Res.drawable.ic_web_asset,
                        checked = reading.readAloudMiniPlayer,
                        enabled = speechEnabled,
                        onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READ_ALOUD_MINI_PLAYER, it) },
                    )
                }
                if (naturalVoiceAvailable) {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Use ML Kit voice",
                        summary = "Download a natural voice for playback at 1× speed",
                        icon = Res.drawable.ic_auto_awesome,
                        checked = reading.preferNaturalVoice,
                        enabled = speechEnabled,
                        onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.PREFER_NATURAL_VOICE, it) },
                    )
                }
            }
        }
    }
    if (showFontPicker && controlsEnabled) {
        FontSelectionDialog(readerMode = true, selected = reading.readerModeFont,
            options = AppFont.entries.map { it.label to it },
            onSelected = repository::setReaderModeFont, onDismiss = { showFontPicker = false })
    }
}
