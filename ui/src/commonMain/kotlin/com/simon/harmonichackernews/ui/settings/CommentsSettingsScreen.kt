package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.CommentsProvider
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.content.CommentItemStyle
import com.simon.harmonichackernews.ui.content.SettingsCommentPreviewModel

data class CommentsSettingsUiState(
    val displayStyle: DisplayStyle,
    val showBorder: Boolean,
    val textSize: Float,
    val textSizeOffset: Int,
    val minTextSizeOffset: Int,
    val maxTextSizeOffset: Int,
    val collectLinks: Boolean,
    val emphasizeMetadata: Boolean,
    val depthMode: String,
    val depthModeLabel: String,
    val showDividers: Boolean,
    val preferredFont: String,
    val topLevelIndicators: Boolean,
    val showScrollbar: Boolean,
    val animateChanges: Boolean,
    val storyTintEnabled: Boolean,
    val showUpButton: Boolean,
    val headerTint: Boolean,
    val storyPreviewEnabled: Boolean,
    val headerPreviewImage: Boolean,
    val collapseParent: Boolean,
    val collapseTopLevel: Boolean,
    val preloadCommentsFromStories: Boolean,
    val swapTap: Boolean,
    val sorting: CommentSortingPreference,
    val provider: CommentsProvider,
    val showNavigationButtons: Boolean,
    val volumeNavigation: CommentVolumeNavigationMode,
    val smoothScroll: Boolean,
)

enum class CommentsBooleanSetting {
    Border,
    CollectLinks,
    EmphasizeMetadata,
    Dividers,
    TopLevelIndicators,
    Scrollbar,
    AnimateChanges,
    ShowUpButton,
    HeaderTint,
    HeaderPreviewImage,
    CollapseParent,
    CollapseTopLevel,
    PreloadCommentsFromStories,
    SwapTap,
    NavigationButtons,
    SmoothScroll,
}

enum class CommentsSettingsDialog { Sorting, Provider, VolumeNavigation, ThreadDepth }

@Composable
fun CommentsSettingsScreen(
    state: CommentsSettingsUiState,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onDisplayStyleChanged: (DisplayStyle) -> Unit,
    onTextSizeOffsetChanged: (Int) -> Unit,
    onBooleanChanged: (CommentsBooleanSetting, Boolean) -> Unit,
    onDialogRequested: (CommentsSettingsDialog) -> Unit,
    contentVersion: Int = 0,
) {
    SettingsPage(
        title = stringResource(Res.string.settings_section_comments),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = contentVersion,
        pinnedContent = {
            CommentItem(
                model = SettingsCommentPreviewModel,
                style = CommentItemStyle(
                    cardStyle = state.displayStyle == DisplayStyle.CARD,
                    showCardBorder = state.showBorder,
                    textSize = state.textSize,
                    collectLinks = state.collectLinks,
                    emphasizeMeta = state.emphasizeMetadata,
                    depthIndicatorMode = state.depthMode,
                    showDivider = state.showDividers,
                    preferredFont = state.preferredFont,
                ),
            )
        },
    ) {
        item {
            SettingsCategory("Comments display") {
                SegmentedSetting(
                    title = "Display style",
                    options = listOf(
                        DisplayStyle.STANDARD.storedValue to "Standard",
                        DisplayStyle.CARD.storedValue to "Card",
                    ),
                    selected = state.displayStyle.storedValue,
                    onSelected = { onDisplayStyleChanged(DisplayStyle.fromStored(it)) },
                )
                SettingsDivider()
                BooleanRow(
                    "Border",
                    Res.drawable.ic_select,
                    state.showBorder,
                    CommentsBooleanSetting.Border,
                    onBooleanChanged,
                    enabled = state.displayStyle == DisplayStyle.CARD,
                )
                SettingsDivider()
                SliderSetting(
                    title = "Text size",
                    valueLabel = if (state.textSizeOffset >= 0) {
                        "+${state.textSizeOffset}"
                    } else {
                        "${state.textSizeOffset}"
                    },
                    value = state.textSizeOffset.toFloat(),
                    valueRange = state.minTextSizeOffset.toFloat()..
                        state.maxTextSizeOffset.toFloat(),
                    steps = state.maxTextSizeOffset - state.minTextSizeOffset - 1,
                    onValueChange = { onTextSizeOffsetChanged(it.toInt()) },
                )
                SettingsDivider()
                BooleanRow("Collect links in comments", Res.drawable.ic_link, state.collectLinks, CommentsBooleanSetting.CollectLinks, onBooleanChanged)
                SettingsDivider()
                BooleanRow("Emphasize meta", Res.drawable.ic_dropdown_menu, state.emphasizeMetadata, CommentsBooleanSetting.EmphasizeMetadata, onBooleanChanged)
                SettingsDivider()
                SettingRow(
                    title = "Thread depth indicators",
                    summary = state.depthModeLabel,
                    icon = Res.drawable.ic_palette,
                    onClick = { onDialogRequested(CommentsSettingsDialog.ThreadDepth) },
                )
                SettingsDivider()
                BooleanRow("Dividers", Res.drawable.ic_horizontal_rule, state.showDividers, CommentsBooleanSetting.Dividers, onBooleanChanged)
                SettingsDivider()
                BooleanRow(
                    "Show top level thread indicators",
                    Res.drawable.ic_format_align_left,
                    state.topLevelIndicators,
                    CommentsBooleanSetting.TopLevelIndicators,
                    onBooleanChanged,
                    summary = "Makes it easier to separate top level comments",
                )
                SettingsDivider()
                BooleanRow("Show scrollbar", Res.drawable.ic_swipe_vertical, state.showScrollbar, CommentsBooleanSetting.Scrollbar, onBooleanChanged)
                SettingsDivider()
                BooleanRow("Animate comment expand/collapse", Res.drawable.ic_animation, state.animateChanges, CommentsBooleanSetting.AnimateChanges, onBooleanChanged)
            }
        }
        item {
            SettingsCategory("Header display") {
                BooleanRow(
                    "Show up button",
                    Res.drawable.ic_arrow_back,
                    state.showUpButton,
                    CommentsBooleanSetting.ShowUpButton,
                    onBooleanChanged,
                )
                SettingsDivider()
                BooleanRow(
                    "Background tint",
                    Res.drawable.ic_palette,
                    state.headerTint,
                    CommentsBooleanSetting.HeaderTint,
                    onBooleanChanged,
                    summary = if (state.storyTintEnabled) null else "Disabled because story tint is off",
                    enabled = state.storyTintEnabled,
                )
                SettingsDivider()
                BooleanRow(
                    "Preview image",
                    Res.drawable.ic_image,
                    state.headerPreviewImage,
                    CommentsBooleanSetting.HeaderPreviewImage,
                    onBooleanChanged,
                    enabled = state.storyPreviewEnabled,
                )
            }
        }
        item {
            SettingsCategory("Interactions") {
                BooleanRow("Hide text of collapsed comments", Res.drawable.ic_comment, state.collapseParent, CommentsBooleanSetting.CollapseParent, onBooleanChanged)
                SettingsDivider()
                BooleanRow("Auto-collapse top level comments", Res.drawable.ic_minimize, state.collapseTopLevel, CommentsBooleanSetting.CollapseTopLevel, onBooleanChanged)
                SettingsDivider()
                BooleanRow(
                    "Preload comments from stories screen",
                    Res.drawable.ic_database,
                    state.preloadCommentsFromStories,
                    CommentsBooleanSetting.PreloadCommentsFromStories,
                    onBooleanChanged,
                    summary = "Loads and prepares visible discussions in the background. Uses more battery and may affect performance.",
                )
                SettingsDivider()
                SegmentedSetting(
                    title = "Comment tap action",
                    summary = if (state.swapTap) "Long press: Toggle visibility" else "Long press: Details",
                    options = listOf("visibility" to "Toggle visibility", "details" to "Details"),
                    selected = if (state.swapTap) "details" else "visibility",
                    onSelected = {
                        onBooleanChanged(CommentsBooleanSetting.SwapTap, it == "details")
                    },
                )
                SettingsDivider()
                DialogRow("Comment sorting", state.sorting.label, Res.drawable.ic_filter_list, CommentsSettingsDialog.Sorting, onDialogRequested)
                SettingsDivider()
                DialogRow("Comments provider", state.provider.label, Res.drawable.ic_api, CommentsSettingsDialog.Provider, onDialogRequested)
            }
        }
        item {
            SettingsCategory("Navigation") {
                BooleanRow(
                    "Show navigation buttons",
                    Res.drawable.ic_explore,
                    state.showNavigationButtons,
                    CommentsBooleanSetting.NavigationButtons,
                    onBooleanChanged,
                    summary = "Navigate between top level comments",
                )
                SettingsDivider()
                DialogRow(
                    "Volume buttons for navigation",
                    state.volumeNavigation.label,
                    Res.drawable.ic_swipe_vertical,
                    CommentsSettingsDialog.VolumeNavigation,
                    onDialogRequested,
                )
                SettingsDivider()
                BooleanRow(
                    "Smooth scroll comments",
                    Res.drawable.ic_comments_animation_navigation,
                    state.smoothScroll,
                    CommentsBooleanSetting.SmoothScroll,
                    onBooleanChanged,
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
    setting: CommentsBooleanSetting,
    onChanged: (CommentsBooleanSetting, Boolean) -> Unit,
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
    dialog: CommentsSettingsDialog,
    onDialogRequested: (CommentsSettingsDialog) -> Unit,
) = SettingRow(
    title = title,
    summary = summary,
    icon = icon,
    onClick = { onDialogRequested(dialog) },
)
