package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import com.simon.harmonichackernews.settings.SplitOrientation
import com.simon.harmonichackernews.settings.SplitRatioPreferences
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_design_services
import com.simon.harmonichackernews.resources.ic_font_download
import com.simon.harmonichackernews.resources.ic_horizontal_split
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.resources.ic_style
import com.simon.harmonichackernews.resources.ic_visibility
import com.simon.harmonichackernews.resources.settings_section_appearance
import com.simon.harmonichackernews.settings.AppearanceBooleanPreference

data class AppearanceSettingsUiState(
    val themeLabel: String,
    val fontLabel: String,
    val paletteTintSummary: String,
    val paletteTintEnabled: Boolean,
    val showTransparentStatusBar: Boolean,
    val transparentStatusBar: Boolean,
    val compactHeader: Boolean,
    val showSplitRatio: Boolean = false,
    val splitRatio: Float = 0.5f,
    val splitOrientation: SplitOrientation = SplitOrientation.Portrait,
    val allowSplitAdjustment: Boolean = false,
)

enum class AppearanceBooleanSetting(internal val preference: AppearanceBooleanPreference) {
    AllowSplitAdjustment(AppearanceBooleanPreference.ALLOW_SPLIT_ADJUSTMENT),
    SpecialNighttime(AppearanceBooleanPreference.SPECIAL_NIGHTTIME),
    TransparentStatusBar(AppearanceBooleanPreference.TRANSPARENT_STATUS_BAR),
    CompactHeader(AppearanceBooleanPreference.COMPACT_HEADER),
}
enum class AppearanceSettingsDialog { Theme, NighttimeRange, NighttimeTheme, Font, Style }

@Composable
fun AppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onBooleanChanged: (AppearanceBooleanSetting, Boolean) -> Unit,
    onDialogRequested: (AppearanceSettingsDialog) -> Unit,
    contentVersion: Int = 0,
    onSplitRatioChanged: (Float) -> Unit = {},
) {
    var sliderRatio by remember(state.splitRatio, state.splitOrientation) { mutableFloatStateOf(state.splitRatio) }
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
                    onClick = { onNavigate(SettingsSection.Theme) },
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
                    onClick = { onNavigate(SettingsSection.PaletteTint) },
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
        if (state.showSplitRatio) {
            item {
                SettingsCategory("Split ratio") {
                    key(state.splitOrientation) {
                        SliderSetting(
                            title = when (state.splitOrientation) {
                                SplitOrientation.Portrait -> "Portrait split ratio"
                                SplitOrientation.Landscape -> "Landscape split ratio"
                            },
                            valueLabel = "${(sliderRatio * 100).roundToInt()} / ${(100 - sliderRatio * 100).roundToInt()}",
                            value = sliderRatio,
                            valueRange = SplitRatioPreferences.Range,
                            steps = 0,
                            onValueChange = { sliderRatio = it },
                            onValueChangeFinished = { onSplitRatioChanged(sliderRatio) },
                        )
                    }
                    SettingsDivider()
                    SwitchSettingRow(
                        title = "Allow adjustments",
                        icon = Res.drawable.ic_horizontal_split,
                        summary = "Drag the handle between panes to resize them",
                        checked = state.allowSplitAdjustment,
                        onCheckedChange = {
                            onBooleanChanged(AppearanceBooleanSetting.AllowSplitAdjustment, it)
                        },
                    )
                }
            }
        }
        item {
            SettingsCategory("Preset") {
                SettingRow(
                    title = "Preset",
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
