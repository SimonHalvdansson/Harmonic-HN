package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_dark_mode
import com.simon.harmonichackernews.resources.ic_design_services
import com.simon.harmonichackernews.resources.ic_font_download
import com.simon.harmonichackernews.resources.ic_horizontal_split
import com.simon.harmonichackernews.resources.ic_nights_stay
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.resources.ic_schedule
import com.simon.harmonichackernews.resources.ic_style
import com.simon.harmonichackernews.resources.ic_visibility
import com.simon.harmonichackernews.resources.settings_section_appearance

data class AppearanceSettingsUiState(
    val themeLabel: String,
    val specialNighttime: Boolean,
    val nighttimeRangeLabel: String,
    val nighttimeThemeLabel: String,
    val fontLabel: String,
    val paletteTintSummary: String,
    val paletteTintEnabled: Boolean,
    val showTransparentStatusBar: Boolean,
    val transparentStatusBar: Boolean,
    val compactHeader: Boolean,
)

enum class AppearanceBooleanSetting { SpecialNighttime, TransparentStatusBar, CompactHeader }
enum class AppearanceSettingsDialog { Theme, NighttimeRange, NighttimeTheme, Font, Style, PaletteTint }

@Composable
fun SharedAppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onBooleanChanged: (AppearanceBooleanSetting, Boolean) -> Unit,
    onDialogRequested: (AppearanceSettingsDialog) -> Unit,
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_appearance),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Theme") {
                SettingRow(
                    title = "Theme",
                    summary = state.themeLabel,
                    icon = Res.drawable.ic_style,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.Theme) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Special nighttime theme",
                    icon = Res.drawable.ic_nights_stay,
                    checked = state.specialNighttime,
                    onCheckedChange = {
                        onBooleanChanged(AppearanceBooleanSetting.SpecialNighttime, it)
                    },
                )
                SettingsDivider()
                SettingRow(
                    title = "Timed range",
                    summary = state.nighttimeRangeLabel,
                    icon = Res.drawable.ic_schedule,
                    enabled = state.specialNighttime,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.NighttimeRange) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Nighttime theme",
                    summary = state.nighttimeThemeLabel,
                    icon = Res.drawable.ic_dark_mode,
                    enabled = state.specialNighttime,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.NighttimeTheme) },
                )
            }
        }
        item {
            SettingsCategory("Visual") {
                SettingRow(
                    title = "Title and comment font",
                    summary = state.fontLabel,
                    icon = Res.drawable.ic_font_download,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.Font) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Palette tint",
                    summary = state.paletteTintSummary,
                    icon = Res.drawable.ic_palette,
                    enabled = state.paletteTintEnabled,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.PaletteTint) },
                )
                if (state.showTransparentStatusBar) {
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Transparent status bar",
                        icon = Res.drawable.ic_visibility,
                        checked = state.transparentStatusBar,
                        onCheckedChange = {
                            onBooleanChanged(AppearanceBooleanSetting.TransparentStatusBar, it)
                        },
                    )
                }
                SettingsDivider()
                SwitchSettingRow(
                    title = "Compact header",
                    summary = "Smaller margins for 'Top stories' header",
                    icon = Res.drawable.ic_horizontal_split,
                    checked = state.compactHeader,
                    onCheckedChange = {
                        onBooleanChanged(AppearanceBooleanSetting.CompactHeader, it)
                    },
                )
            }
        }
        item {
            SettingsCategory("Style") {
                SettingRow(
                    title = "General style",
                    icon = Res.drawable.ic_design_services,
                    onClick = { onDialogRequested(AppearanceSettingsDialog.Style) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Stories settings",
                    icon = Res.drawable.ic_open_in_new,
                    onClick = { onNavigate(SettingsSection.Stories) },
                )
                SettingsDivider()
                SettingRow(
                    title = "Comments settings",
                    icon = Res.drawable.ic_open_in_new,
                    onClick = { onNavigate(SettingsSection.Comments) },
                )
            }
        }
    }
}
