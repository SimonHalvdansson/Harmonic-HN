package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.CommentIndicatorThickness
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentDepthPreferences
import com.simon.harmonichackernews.ui.content.CommentItem
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlin.time.Clock
import org.jetbrains.compose.resources.stringResource

@Composable
fun ThreadDepthIndicatorsSettingsRoute(
    repository: AppSettingsRepository,
    onBack: () -> Unit,
) {
    val presenter = remember(repository) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    ThreadDepthIndicatorsSettingsScreen(
        state = presenter.state(settings),
        onEnabledChanged = repository::setCommentDepthIndicatorsEnabled,
        onModeSelected = presenter::setDepthIndicatorMode,
        onThicknessSelected = presenter::setIndicatorThickness,
        onBooleanChanged = presenter::setBoolean,
        onBack = onBack,
    )
}

@Composable
private fun ThreadDepthIndicatorsSettingsScreen(
    state: CommentsSettingsUiState,
    onEnabledChanged: (Boolean) -> Unit,
    onModeSelected: (String) -> Unit,
    onThicknessSelected: (CommentIndicatorThickness) -> Unit,
    onBooleanChanged: (CommentsBooleanSetting, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val comments = remember {
        val createdAt = Clock.System.now().epochSeconds.toInt() - 3600
        listOf(
            "willow" to "Less hidden state helps.",
            "lantern" to "Much easier to reason about.",
            "pebble" to "And simpler to test.",
            "pixel" to "Small steps add up.",
        ).mapIndexed { depth, (author, body) ->
            PortableCommentItem(
                comment = CommentSnapshot(
                    id = -100 - depth,
                    author = author,
                    text = body,
                    expandedAnchorText = body,
                    createdAtEpochSeconds = createdAt + depth * 60,
                ),
                presentation = CommentPresentationSnapshot(expanded = true, depth = depth),
            )
        }
    }
    val modes = listOf(
        CommentDepthPreferences.THEME_DEFAULT,
        CommentDepthPreferences.MATERIAL_YOU,
        CommentDepthPreferences.COLORS,
        CommentDepthPreferences.AUTHOR,
        CommentDepthPreferences.MONOCHROME,
    )
    val indicatorsEnabled = CommentDepthPreferences.shouldShowIndicators(state.depthMode)
    SettingsPage(
        title = stringResource(Res.string.settings_section_thread_depth),
        showNavigation = true,
        onBack = onBack,
        contentVersion = state.hashCode(),
        headerContent = {
            SettingsMainToggle(
                title = "Use thread depth indicators",
                checked = indicatorsEnabled,
                enabled = true,
                onCheckedChange = onEnabledChanged,
            )
        },
        pinnedContent = {
            // Use runtime rows so indentation, surfaces, type, metadata and top-level indicators
            // follow the same preferences as the actual thread.
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                comments.forEachIndexed { index, comment ->
                    CommentItem(
                        comment = comment,
                        style = state.toPreviewCommentItemStyle(),
                        storyAuthor = null,
                        accountUser = null,
                        userTag = null,
                        subtreeReplyCount = 0,
                        collapseParent = state.collapseParent,
                        showTopLevelIndicator = state.topLevelIndicators,
                        nextCommentDepth = comments.getOrNull(index + 1)?.depth,
                        enableLongClick = false,
                        onToggleExpanded = {},
                        onShowActions = {},
                        onLinkLongClick = { _, _, _ -> },
                        onReferenceLongClick = { _, _, _ -> },
                    )
                }
            }
        },
    ) {
        item {
            SettingsCategory("Indicator shape") {
                SegmentedSetting(
                    title = "Thickness",
                    options = CommentIndicatorThickness.entries.map { it.storedValue to it.label },
                    selected = state.indicatorThickness.storedValue,
                    enabled = indicatorsEnabled,
                    onSelected = { onThicknessSelected(CommentIndicatorThickness.fromStored(it)) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Rounded corners",
                    summary = "Rounded line ends; sits beside the card in Filled and Raised",
                    icon = Res.drawable.ic_select,
                    checked = state.roundedDepthIndicators,
                    enabled = indicatorsEnabled,
                    onCheckedChange = { onBooleanChanged(CommentsBooleanSetting.RoundedDepthIndicators, it) },
                )
            }
        }
        item {
            SettingsCategory("Thread lines") {
                SwitchSettingRow(
                    title = "Continuous thread lines",
                    summary = if (state.depthMode == CommentDepthPreferences.AUTHOR) {
                        "Unavailable with Author colors"
                    } else "Continue ancestor lines along the left of their replies",
                    icon = Res.drawable.ic_account_tree,
                    checked = state.continuousDepthIndicators && state.depthMode != CommentDepthPreferences.AUTHOR,
                    enabled = indicatorsEnabled && state.depthMode != CommentDepthPreferences.AUTHOR,
                    onCheckedChange = { onBooleanChanged(CommentsBooleanSetting.ContinuousDepthIndicators, it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Show top level thread indicators",
                    summary = "Makes it easier to separate top level comments",
                    icon = Res.drawable.ic_border_left,
                    checked = state.topLevelIndicators,
                    enabled = indicatorsEnabled,
                    onCheckedChange = { onBooleanChanged(CommentsBooleanSetting.TopLevelIndicators, it) },
                )
            }
        }
        item {
            SettingsCategory("Indicator colors") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(itemBackgroundColor())
                        .alpha(if (indicatorsEnabled) 1f else 0.38f)
                        .selectableGroup(),
                ) {
                    modes.forEachIndexed { index, option ->
                        val selected = CommentDepthPreferences.sanitizeMode(state.depthMode) == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .selectable(
                                    selected = selected,
                                    enabled = indicatorsEnabled,
                                    role = Role.RadioButton,
                                    onClick = { onModeSelected(option) },
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsRadioButton(selected = selected)
                            Text(
                                text = CommentDepthPreferences.modeLabel(option),
                                modifier = Modifier.padding(start = 4.dp),
                                color = HarmonicTheme.colors.textPrimary,
                                fontFamily = ProductSansFontFamily,
                                fontSize = 16.sp,
                            )
                        }
                        if (index < modes.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}
