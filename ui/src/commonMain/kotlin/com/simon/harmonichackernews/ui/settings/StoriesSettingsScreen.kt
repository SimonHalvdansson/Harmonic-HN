package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.content.StoryItem
import com.simon.harmonichackernews.ui.content.StoryItemStyle
import com.simon.harmonichackernews.ui.content.StoryItemUiModel

data class StoriesSettingsUiState(
    val previewModel: StoryItemUiModel,
    val previewImageMode: String,
    val previewOffValue: String,
    val previewSmallValue: String,
    val previewMediumValue: String,
    val previewLargeValue: String,
    val borderlessLargeImage: Boolean,
    val compact: Boolean,
    val showSummary: Boolean,
    val showThumbnails: Boolean,
    val showPoints: Boolean,
    val compactPoints: Boolean,
    val includeTopLevelDomain: Boolean,
    val showComments: Boolean,
    val showIndex: Boolean,
    val leftAlignComments: Boolean,
    val tint: Boolean,
    val displayStyle: String,
    val standardStyleValue: String,
    val cardStyleValue: String,
    val textSize: Float,
    val textSizeOffset: Int,
    val minTextSizeOffset: Int,
    val maxTextSizeOffset: Int,
    val hotnessEnabled: Boolean,
    val hotnessLabel: String,
    val preferredFont: String,
    val paletteTintConfigKey: String,
    val startingPage: String,
    val additionalFrontpagesSummary: String,
    val alwaysOpenComments: Boolean,
    val pagination: Boolean,
    val hideClicked: Boolean,
    val grayOutClicked: Boolean,
    val faviconProvider: String,
    val faviconIcon: Painter,
)

enum class StoriesBooleanSetting {
    BorderlessLargeImage,
    Tint,
    Compact,
    ShowSummary,
    ShowThumbnails,
    ShowPoints,
    CompactPoints,
    IncludeTopLevelDomain,
    ShowComments,
    ShowIndex,
    LeftAlignComments,
    AlwaysOpenComments,
    Pagination,
    HideClicked,
    GrayOutClicked,
}

enum class StoriesStringSetting { PreviewImageMode, DisplayStyle }
enum class StoriesSettingsDialog { Hotness, StartingPage, AdditionalFrontpages, FaviconProvider }

@Composable
fun StoriesSettingsScreen(
    state: StoriesSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onBooleanChanged: (StoriesBooleanSetting, Boolean) -> Unit,
    onStringChanged: (StoriesStringSetting, String) -> Unit,
    onTextSizeOffsetChanged: (Int) -> Unit,
    onDialogRequested: (StoriesSettingsDialog) -> Unit,
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_stories),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
        pinnedContent = {
            StoryItem(
                model = state.previewModel,
                style = StoryItemStyle(
                    previewImageMode = state.previewImageMode,
                    borderlessLargeImage = state.borderlessLargeImage,
                    compact = state.compact,
                    showSummary = state.showSummary,
                    showFavicon = state.showThumbnails,
                    showPoints = state.showPoints,
                    compactPoints = state.compactPoints,
                    includeTopLevelDomain = state.includeTopLevelDomain,
                    showCommentCount = state.showComments,
                    showIndex = state.showIndex,
                    commentsOnLeft = state.leftAlignComments,
                    tintCard = state.tint,
                    cardStyle = state.displayStyle == state.cardStyleValue,
                    useHotnessIcon = state.hotnessEnabled,
                    preferredFont = state.preferredFont,
                    textSize = state.textSize,
                    paletteTintConfigKey = state.paletteTintConfigKey,
                ),
            )
        },
    ) {
        item {
            SettingsCategory("Layout") {
                SegmentedSetting(
                    title = "Preview image",
                    options = listOf(
                        state.previewOffValue to "Off",
                        state.previewSmallValue to "Small",
                        state.previewMediumValue to "Medium",
                        state.previewLargeValue to "Large",
                    ),
                    selected = state.previewImageMode,
                    onSelected = { onStringChanged(StoriesStringSetting.PreviewImageMode, it) },
                )
                SettingsDivider()
                BooleanRow("Borderless large image", Res.drawable.ic_fullscreen, state.borderlessLargeImage, StoriesBooleanSetting.BorderlessLargeImage, onBooleanChanged, enabled = state.previewImageMode == state.previewLargeValue)
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = formatOffset(state.textSizeOffset),
                    value = state.textSizeOffset.toFloat(),
                    valueRange = state.minTextSizeOffset.toFloat()..
                        state.maxTextSizeOffset.toFloat(),
                    steps = state.maxTextSizeOffset - state.minTextSizeOffset - 1,
                    onValueChange = { onTextSizeOffsetChanged(it.toInt()) },
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Display style",
                    options = listOf(
                        state.standardStyleValue to "Standard",
                        state.cardStyleValue to "Card",
                    ),
                    selected = state.displayStyle,
                    onSelected = { onStringChanged(StoriesStringSetting.DisplayStyle, it) },
                )
                SettingsDivider()
                BooleanRow("Tint", Res.drawable.ic_palette, state.tint, StoriesBooleanSetting.Tint, onBooleanChanged, summary = "Uses preview or favicon")
                SettingsDivider()
                BooleanRow("Compact stories", Res.drawable.ic_view_agenda, state.compact, StoriesBooleanSetting.Compact, onBooleanChanged, summary = "Hides points, domain and time")
                SettingsDivider()
                BooleanRow("Show summary", Res.drawable.ic_subject, state.showSummary, StoriesBooleanSetting.ShowSummary, onBooleanChanged)
                SettingsDivider()
                BooleanRow("Show story thumbnails", Res.drawable.ic_public, state.showThumbnails, StoriesBooleanSetting.ShowThumbnails, onBooleanChanged, enabled = !state.compact)
                SettingsDivider()
                BooleanRow("Show story points", Res.drawable.ic_thumbs_up_down, state.showPoints, StoriesBooleanSetting.ShowPoints, onBooleanChanged, enabled = !state.compact)
                SettingsDivider()
                BooleanRow(
                    "Compact points",
                    Res.drawable.ic_thumb_up,
                    state.compactPoints,
                    StoriesBooleanSetting.CompactPoints,
                    onBooleanChanged,
                    enabled = !state.compact &&
                        state.showPoints &&
                        state.previewImageMode != state.previewMediumValue,
                )
                SettingsDivider()
                BooleanRow("Include top level domain", Res.drawable.ic_public, state.includeTopLevelDomain, StoriesBooleanSetting.IncludeTopLevelDomain, onBooleanChanged, enabled = !state.compact)
                SettingsDivider()
                BooleanRow("Show comment count", Res.drawable.ic_comment, state.showComments, StoriesBooleanSetting.ShowComments, onBooleanChanged, enabled = !state.compact)
                SettingsDivider()
                BooleanRow("Show story indices", Res.drawable.ic_format_list_numbered, state.showIndex, StoriesBooleanSetting.ShowIndex, onBooleanChanged)
                SettingsDivider()
                BooleanRow(
                    "Left align comments button",
                    Res.drawable.ic_pan_tool,
                    state.leftAlignComments,
                    StoriesBooleanSetting.LeftAlignComments,
                    onBooleanChanged,
                    enabled = state.previewImageMode != state.previewMediumValue,
                )
                SettingsDivider()
                SettingRow(
                    title = "Highlight hot stories",
                    summary = state.hotnessLabel,
                    icon = Res.drawable.ic_whatshot,
                    onClick = { onDialogRequested(StoriesSettingsDialog.Hotness) },
                )
            }
        }
        item {
            SettingsCategory("Behavior") {
                DialogRow("Starting page", state.startingPage, Res.drawable.ic_bookmark, StoriesSettingsDialog.StartingPage, onDialogRequested)
                SettingsDivider()
                DialogRow("Additional frontpages", state.additionalFrontpagesSummary, Res.drawable.ic_library_books, StoriesSettingsDialog.AdditionalFrontpages, onDialogRequested)
                SettingsDivider()
                BooleanRow("Always open comments", Res.drawable.ic_keyboard_double_arrow_right, state.alwaysOpenComments, StoriesBooleanSetting.AlwaysOpenComments, onBooleanChanged, summary = "Clicking a story takes you directly to the comments view")
                SettingsDivider()
                BooleanRow("Use pagination", Res.drawable.ic_swipe_vertical, state.pagination, StoriesBooleanSetting.Pagination, onBooleanChanged, summary = "Load 30 stories at a time")
                SettingsDivider()
                BooleanRow("Hide clicked posts", Res.drawable.ic_visibility_off, state.hideClicked, StoriesBooleanSetting.HideClicked, onBooleanChanged)
                SettingsDivider()
                BooleanRow("Gray out clicked posts", Res.drawable.ic_visibility, state.grayOutClicked, StoriesBooleanSetting.GrayOutClicked, onBooleanChanged, enabled = !state.hideClicked)
                SettingsDivider()
                SettingRow(
                    title = "Favicon provider",
                    summary = state.faviconProvider,
                    icon = null,
                    iconPainter = state.faviconIcon,
                    iconTint = Color.Unspecified,
                    enabled = !state.compact && state.showThumbnails,
                    onClick = { onDialogRequested(StoriesSettingsDialog.FaviconProvider) },
                )
            }
        }
    }
}

@Composable
private fun BooleanRow(
    title: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    checked: Boolean,
    setting: StoriesBooleanSetting,
    onChanged: (StoriesBooleanSetting, Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
) = SwitchSettingRow(
    title = title,
    summary = summary,
    icon = icon,
    checked = checked,
    enabled = enabled,
    onCheckedChange = { onChanged(setting, it) },
)

@Composable
private fun DialogRow(
    title: String,
    summary: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    dialog: StoriesSettingsDialog,
    onDialogRequested: (StoriesSettingsDialog) -> Unit,
) = SettingRow(
    title = title,
    summary = summary,
    icon = icon,
    onClick = { onDialogRequested(dialog) },
)

private fun formatOffset(offset: Int): String = if (offset >= 0) "+$offset" else "$offset"
