package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.ExtraSidePadding
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_view_agenda
import com.simon.harmonichackernews.resources.ic_design_services
import com.simon.harmonichackernews.resources.ic_font_download
import com.simon.harmonichackernews.resources.ic_open_in_new
import com.simon.harmonichackernews.resources.ic_palette
import com.simon.harmonichackernews.resources.ic_style
import com.simon.harmonichackernews.resources.ic_visibility
import com.simon.harmonichackernews.resources.settings_section_appearance
import com.simon.harmonichackernews.settings.AppearanceBooleanPreference
import com.simon.harmonichackernews.settings.StoryListSelector
import com.simon.harmonichackernews.settings.SurfaceEffectMode

data class AppearanceSettingsUiState(
    val themeLabel: String,
    val fontLabel: String,
    val paletteTintSummary: String,
    val paletteTintEnabled: Boolean,
    val showTransparentStatusBar: Boolean,
    val transparentStatusBar: Boolean,
    val compactHeader: Boolean,
    val showExtraSidePadding: Boolean = false,
    val extraSidePadding: ExtraSidePadding = ExtraSidePadding.Standard,
    val storyListSelector: StoryListSelector = StoryListSelector.DROPDOWN,
    val surfaceEffectMode: SurfaceEffectMode = SurfaceEffectMode.Frosted,
)

enum class AppearanceBooleanSetting(internal val preference: AppearanceBooleanPreference) {
    SpecialNighttime(AppearanceBooleanPreference.SPECIAL_NIGHTTIME),
    TransparentStatusBar(AppearanceBooleanPreference.TRANSPARENT_STATUS_BAR),
    CompactHeader(AppearanceBooleanPreference.COMPACT_HEADER),
}

@Composable
fun AppearanceSettingsScreen(
    state: AppearanceSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onBooleanChanged: (AppearanceBooleanSetting, Boolean) -> Unit,
    onStyleRequested: () -> Unit,
    contentVersion: Int = 0,
    onExtraSidePaddingChanged: (ExtraSidePadding) -> Unit = {},
    onSurfaceEffectChanged: (SurfaceEffectMode) -> Unit = {},
) {
    if (state.paletteTintEnabled) PreloadPalettePreviewResources()
    SettingsPage(
        title = stringResource(Res.string.settings_section_appearance),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
    ) {
        item {
            SettingsCategory("Colors") {
                SettingRow(
                    title = "Color scheme",
                    summary = state.themeLabel,
                    icon = Res.drawable.ic_style,
                    onClick = { onNavigate(SettingsSection.Theme) },
                )
            }
        }
        item {
            SettingsCategory("Visual") {
                SettingRow(
                    title = "Font",
                    summary = state.fontLabel,
                    icon = Res.drawable.ic_font_download,
                    onClick = { onNavigate(SettingsSection.Font) },
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
                SettingRow(
                    title = "Stories header",
                    summary = "Selector, compactness",
                    icon = Res.drawable.ic_view_agenda,
                    onClick = { onNavigate(SettingsSection.StoriesHeader) },
                )
                SettingsDivider()
                SurfaceEffectSelector(state.surfaceEffectMode, onSurfaceEffectChanged)
            }
        }
        if (state.showExtraSidePadding) {
            item {
                SettingsCategory("Two panes") {
                    SegmentedSetting(
                        title = "Extra side padding",
                        options = listOf(
                            ExtraSidePadding.None to "None",
                            ExtraSidePadding.Small to "Small",
                            ExtraSidePadding.Standard to "Standard",
                        ),
                        selected = state.extraSidePadding,
                        onSelected = onExtraSidePaddingChanged,
                    )
                }
            }
        }
        item {
            SettingsCategory("Other") {
                SettingRow(
                    title = "Preset",
                    icon = Res.drawable.ic_design_services,
                    onClick = onStyleRequested,
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
                SettingsDivider()
                SettingRow(
                    title = "Header display settings",
                    icon = Res.drawable.ic_open_in_new,
                    onClick = { onNavigate(SettingsSection.HeaderDisplay) },
                )
            }
        }
    }
}
