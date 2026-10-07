package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AppFont
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.ReadingBooleanPreference
import com.simon.harmonichackernews.settings.TextPreferences
import com.simon.harmonichackernews.ui.content.rememberContentTypography
import com.simon.harmonichackernews.ui.theme.cardBackground

@Composable
fun ReaderModeSettingsRoute(repository: AppSettingsRepository, onBack: () -> Unit) {
    val settings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())
    val reading = settings.reading
    var showFontPicker by rememberSaveable { mutableStateOf(false) }
    val family = rememberContentTypography(reading.readerModeFont.storedValue).family
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
                summary = if (reading.integratedWebView) null else "Enable the integrated browser to use reader mode",
                onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READER_MODE_ENABLED, it) },
            )
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.cardBackground, RoundedCornerShape(20.dp))
                    .padding(24.dp),
            ) {
                Text("Article preview", fontFamily = family, fontWeight = FontWeight.Bold,
                    fontSize = (reading.readerModeFontSize * 1.3f).sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Text("Reader mode brings the words into focus. Adjust the font and text size to find a comfortable way to read your next story.",
                    modifier = Modifier.padding(top = 12.dp), fontFamily = family,
                    fontSize = reading.readerModeFontSize.sp,
                    lineHeight = (reading.readerModeFontSize * 1.68f).sp,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
        item {
            SettingsCategory("Reading") {
                SwitchSettingRow(
                    title = "Reader mode on by default",
                    summary = "Automatically simplify supported articles",
                    icon = Res.drawable.ic_chrome_reader_mode,
                    checked = reading.readerModeDefault,
                    enabled = reading.integratedWebView && reading.readerModeEnabled,
                    onCheckedChange = { repository.setReadingBoolean(ReadingBooleanPreference.READER_MODE_DEFAULT, it) },
                )
                SettingsDivider()
                SettingRow(title = "Font", summary = reading.readerModeFont.label,
                    icon = Res.drawable.ic_font_download, onClick = { showFontPicker = true })
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = "${reading.readerModeFontSize}px" +
                        if (reading.readerModeFontSize == TextPreferences.DEFAULT_READER_MODE_FONT_SIZE) " (default)" else "",
                    value = reading.readerModeFontSize.toFloat(),
                    valueRange = TextPreferences.MIN_READER_MODE_FONT_SIZE.toFloat()..TextPreferences.MAX_READER_MODE_FONT_SIZE.toFloat(),
                    steps = TextPreferences.MAX_READER_MODE_FONT_SIZE - TextPreferences.MIN_READER_MODE_FONT_SIZE - 1,
                    onValueChange = { repository.setReaderModeFontSize(it.toInt()) },
                )
            }
        }
    }
    if (showFontPicker) {
        FontSelectionDialog(readerMode = true, selected = reading.readerModeFont,
            options = AppFont.entries.map { it.label to it },
            onSelected = repository::setReaderModeFont, onDismiss = { showFontPicker = false })
    }
}
