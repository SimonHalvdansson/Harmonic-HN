package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.simon.harmonichackernews.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.common.PredictiveBackDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private data class SettingsListEntry(
    val section: SettingsSection,
    val icon: DrawableResource,
)

private val MainSettingsEntries = listOf(
    SettingsListEntry(SettingsSection.Appearance, Res.drawable.ic_style),
    SettingsListEntry(SettingsSection.Stories, Res.drawable.ic_newspaper),
    SettingsListEntry(SettingsSection.Comments, Res.drawable.ic_comment),
    SettingsListEntry(SettingsSection.WebLinks, Res.drawable.ic_web_asset),
    SettingsListEntry(SettingsSection.FiltersTags, Res.drawable.ic_filter_list),
    SettingsListEntry(SettingsSection.AiSummary, Res.drawable.ic_auto_awesome),
    SettingsListEntry(SettingsSection.Data, Res.drawable.ic_library_books),
    SettingsListEntry(SettingsSection.Debug, Res.drawable.ic_api),
    SettingsListEntry(SettingsSection.About, Res.drawable.ic_info),
)

@Composable
fun SettingsAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    neutralButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    edgeToEdgeContent: Boolean = false,
    showButtons: Boolean = true,
    separateDismissButton: Boolean = false,
    properties: DialogProperties = DialogProperties(),
    scrollableContent: Boolean = false,
) {
    PredictiveBackDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val shortEdge = minOf(maxWidth, maxHeight)
            val longEdge = maxOf(maxWidth, maxHeight)
            val usesTabletDialogWidth = shortEdge >= 600.dp && longEdge >= shortEdge * 1.3f
            val dialogMaxWidth = if (usesTabletDialogWidth) {
                HarmonicDimens.compose_settings_dialog_tablet_max_width
            } else {
                HarmonicDimens.compose_settings_dialog_max_width
            }
            if (properties.dismissOnClickOutside) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(onDismissRequest) {
                            detectTapGestures { onDismissRequest() }
                        },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = HarmonicDimens.compose_settings_dialog_horizontal_margin,
                        vertical = HarmonicDimens.compose_settings_dialog_vertical_margin,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(
                            max = dialogMaxWidth,
                        )
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                ) {
                    Column {
                    title?.let { titleContent ->
                        Box(
                            modifier = Modifier.padding(
                                start = HarmonicDimens.compose_settings_dialog_content_padding,
                                top = HarmonicDimens.compose_settings_dialog_content_padding,
                                end = HarmonicDimens.compose_settings_dialog_content_padding,
                                bottom = 0.dp,
                            ),
                        ) {
                            titleContent()
                        }
                    }
                    text?.let { textContent ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (scrollableContent) {
                                        Modifier.weight(1f, fill = false)
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(
                                    start = if (edgeToEdgeContent) {
                                        0.dp
                                    } else {
                                        HarmonicDimens.compose_settings_dialog_content_padding
                                    },
                                    top = if (edgeToEdgeContent) {
                                        0.dp
                                    } else if (title == null) {
                                        HarmonicDimens.compose_settings_dialog_content_padding
                                    } else {
                                        0.dp
                                    },
                                    end = if (edgeToEdgeContent) {
                                        0.dp
                                    } else {
                                        HarmonicDimens.compose_settings_dialog_content_padding
                                    },
                                    bottom = if (edgeToEdgeContent) 0.dp else 8.dp,
                                ),
                        ) {
                            textContent()
                        }
                    }
                    if (showButtons) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = HarmonicDimens.compose_settings_dialog_content_padding,
                                    vertical = HarmonicDimens.compose_settings_dialog_action_vertical_padding,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.End,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            neutralButton?.invoke()
                            if (neutralButton != null) {
                                Spacer(Modifier.weight(1f))
                            }
                            dismissButton?.invoke()
                            if (separateDismissButton && dismissButton != null) {
                                Spacer(Modifier.weight(1f))
                            }
                            confirmButton()
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(
            HarmonicDimens.compose_settings_dialog_option_control_size,
        ),
        contentAlignment = Alignment.Center,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
    }
}

@Composable
private fun SettingsCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(
            HarmonicDimens.compose_settings_dialog_option_control_size,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun SettingsTopAppBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    val platformStyle = LocalSettingsPlatformStyle.current
    HarmonicTopAppBar(
        title = title,
        onBack = onBack,
        toolbarHeight = platformStyle.topBarHeight,
        navigationHeight = platformStyle.topBarNavigationHeight,
        navigationInset = platformStyle.topBarNavigationInset,
        platformTextStyle = platformStyle.textStyle,
    )
}

@Composable
fun SettingsListScreen(
    selectedSection: SettingsSection,
    showSelection: Boolean,
    showDebugSettings: Boolean,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsCardShape = RoundedCornerShape(
        HarmonicDimens.settings_list_segment_corner_radius,
    )
    val visibleEntries = MainSettingsEntries.filter {
        it.section != SettingsSection.Debug || showDebugSettings
    }
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SettingsTopAppBar(
            title = stringResource(Res.string.settings_title),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HarmonicDimens.settings_list_segment_horizontal_margin,
                top = HarmonicDimens.settings_list_first_segment_top_margin,
                end = HarmonicDimens.settings_list_segment_horizontal_margin,
                bottom = HarmonicDimens.settings_list_segment_bottom_margin +
                    navigationBarPadding,
            ),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(settingsCardShape),
                ) {
                    visibleEntries.forEachIndexed { index, entry ->
                        val isSelected = selectedSection == entry.section ||
                            entry.section == SettingsSection.Debug &&
                            selectedSection == SettingsSection.DebugLinkPreviews ||
                            entry.section == SettingsSection.About &&
                            selectedSection == SettingsSection.Licenses
                        SettingsNavigationRow(
                            title = stringResource(entry.section.titleResource),
                            icon = entry.icon,
                            selected = showSelection && isSelected,
                            onClick = { onSectionSelected(entry.section) },
                        )
                        if (index != visibleEntries.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = HarmonicDimens.compose_settings_row_min_height,
            )
            .background(
                if (selected) {
                    HarmonicTheme.colors.settingsHeaderSelected
                } else {
                    HarmonicTheme.colors.settingsSegment
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(
                HarmonicDimens.compose_settings_row_icon_size,
            ),
            tint = HarmonicTheme.colors.drawable,
        )
        Spacer(
            modifier = Modifier.width(
                HarmonicDimens.compose_settings_row_icon_end_space,
            ),
        )
        Text(
            text = title,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
    }
}

@Composable
fun SettingsPage(
    title: String,
    showNavigation: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentVersion: Int = 0,
    pinnedContent: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val navigationBarPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HarmonicTheme.colors.background)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        SettingsTopAppBar(
            title = title,
            onBack = onBack.takeIf { showNavigation },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 0.dp,
                top = 0.dp,
                end = 0.dp,
                bottom = 24.dp + navigationBarPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Preference-backed rows are declared in the lazy content lambda. Capturing the
            // version here makes LazyColumn rebuild those declarations after a preference edit.
            @Suppress("UNUSED_EXPRESSION")
            contentVersion
            pinnedContent?.let { preview ->
                stickyHeader(key = "settings-preview") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HarmonicTheme.colors.background),
                    ) {
                        preview()
                    }
                }
            }
            content()
        }
    }
}

@Composable
fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            modifier = Modifier
                .semantics { heading() }
                .padding(
                    start = HarmonicDimens.compose_settings_category_padding_start,
                    top = HarmonicDimens.compose_settings_category_padding_top,
                    end = HarmonicDimens.settings_list_segment_horizontal_margin,
                    bottom = HarmonicDimens.compose_settings_category_padding_bottom,
                ),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        Spacer(
            modifier = Modifier.height(
                HarmonicDimens.compose_settings_category_segment_gap,
            ),
        )
        SettingsCard(content = content)
    }
}

@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = HarmonicDimens.settings_list_segment_horizontal_margin,
            )
            .clip(
                RoundedCornerShape(
                    HarmonicDimens.settings_list_segment_corner_radius,
                ),
            ),
        content = { content() },
    )
}

@Composable
fun SettingsMainToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HarmonicDimens.settings_list_segment_horizontal_margin,
                top = HarmonicDimens.settings_list_first_segment_top_margin,
                end = HarmonicDimens.settings_list_segment_horizontal_margin,
                bottom = 16.dp,
            )
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .alpha(if (enabled) 1f else 0.6f)
            .background(HarmonicTheme.colors.settingsMainToggle)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = HarmonicTheme.colors.settingsMainToggleText,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 21.sp,
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    icon: DrawableResource?,
    iconPainter: Painter? = null,
    summaryFontSizeSp: Float = 14f,
    summaryLineHeightSp: Float = 18f,
    summaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    role: Role = Role.Button,
    checkedState: Boolean? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val resolvedIconPainter = when {
        icon != null -> painterResource(icon)
        else -> iconPainter
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = HarmonicDimens.compose_settings_row_min_height,
            )
            .background(HarmonicTheme.colors.settingsSegment)
            .alpha(if (enabled) 1f else 0.38f)
            .then(
                if (onClick != null) {
                    if (checkedState != null) {
                        Modifier.toggleable(
                            value = checkedState,
                            enabled = enabled,
                            role = role,
                            onValueChange = { onClick() },
                        )
                    } else {
                        Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
                    }
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (resolvedIconPainter != null) {
            Icon(
                painter = resolvedIconPainter,
                contentDescription = null,
                modifier = Modifier.size(
                    HarmonicDimens.compose_settings_row_icon_size,
                ),
                tint = iconTint ?: HarmonicTheme.colors.drawable,
            )
            Spacer(
                modifier = Modifier.width(
                    HarmonicDimens.compose_settings_row_icon_end_space,
                ),
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    vertical = HarmonicDimens.compose_settings_row_text_vertical_padding,
                ),
        ) {
            Text(
                text = title,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = summaryFontSizeSp.sp,
                    lineHeight = summaryLineHeightSp.sp,
                    maxLines = summaryMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Spacer(
                modifier = Modifier.width(
                    HarmonicDimens.compose_settings_row_trailing_start_space,
                ),
            )
            it()
        }
    }
}

@Composable
fun SwitchSettingRow(
    title: String,
    summary: String? = null,
    icon: DrawableResource,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        role = Role.Switch,
        checkedState = checked,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
    )
}

@Composable
fun SettingsDialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content,
    )
}

@Composable
fun SettingsDialogOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(HarmonicDimens.settings_list_segment_internal_gap)
            .background(HarmonicTheme.colors.background),
    )
}

/**
 * Renders an inline segmented preference. An optional summary is kept inside the same
 * preference, between its title and controls, matching custom View preference layouts.
 */
@Composable
fun SegmentedSetting(
    title: String,
    summary: String? = null,
    options: List<Pair<String, String>>,
    optionIcons: Map<String, DrawableResource> = emptyMap(),
    selected: String,
    enabled: Boolean = true,
    buttonHeight: Dp = HarmonicDimens.compose_settings_segmented_button_height,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
                vertical = HarmonicDimens.compose_settings_inline_control_padding,
            ),
    ) {
        Text(
            text = title,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                modifier = Modifier.padding(
                    top = HarmonicDimens.compose_settings_inline_control_summary_top_margin,
                ),
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 18.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(
                    top = HarmonicDimens.compose_settings_inline_control_top_margin,
                ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = selected == value
                val outerCorner = HarmonicDimens.compose_settings_segmented_button_corner_radius
                val defaultInnerCorner = HarmonicDimens.compose_settings_segmented_button_inner_corner_radius
                val pressedInnerCorner = HarmonicDimens.compose_settings_segmented_button_pressed_inner_corner_radius
                val interactionSource = remember(value) { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val innerCorner by animateDpAsState(
                    targetValue = when {
                        isPressed -> pressedInnerCorner
                        isSelected -> outerCorner
                        else -> defaultInnerCorner
                    },
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = 800f,
                    ),
                    label = "settings segmented button corners",
                )
                val shape = RoundedCornerShape(
                    topStart = if (index == 0) outerCorner else innerCorner,
                    topEnd = if (index == options.lastIndex) outerCorner else innerCorner,
                    bottomEnd = if (index == options.lastIndex) outerCorner else innerCorner,
                    bottomStart = if (index == 0) outerCorner else innerCorner,
                )
                val selectedBackground =
                    HarmonicTheme.colors.onSurface.copy(alpha = 0.9f)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(
                            buttonHeight,
                        )
                        .background(
                            if (isSelected) {
                                selectedBackground
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            shape,
                        )
                        .border(
                            1.dp,
                            if (isSelected) {
                                selectedBackground
                            } else {
                                HarmonicTheme.colors.outlineVariant
                            },
                            shape,
                        )
                        .clip(shape)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            onClick = { onSelected(value) },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    optionIcons[value]?.let { icon ->
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isSelected) {
                                HarmonicTheme.colors.background
                            } else {
                                HarmonicTheme.colors.drawable
                            },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = label,
                        color = if (isSelected) {
                            HarmonicTheme.colors.background
                        } else {
                            HarmonicTheme.colors.textPrimary
                        },
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
                vertical = HarmonicDimens.compose_settings_inline_control_padding,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = valueLabel,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
fun SingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(title) },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .selectableGroup(),
            ) {
                items(options, key = { it.first }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .selectable(
                                selected = option.first == selected,
                                role = Role.RadioButton,
                                onClick = { onSelected(option.first) },
                            )
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsRadioButton(selected = option.first == selected)
                        Text(
                            text = option.second,
                            modifier = Modifier.padding(start = 4.dp),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
fun MultiChoiceDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChanged: (Set<String>) -> Unit,
) {
    var workingSelection by remember(options, selected) {
        mutableStateOf(selected)
    }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(title) },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                items(options, key = { it }) { option ->
                    val checked = option in workingSelection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable(
                                role = Role.Checkbox,
                                onClick = {
                                    workingSelection = if (checked) {
                                        workingSelection - option
                                    } else {
                                        workingSelection + option
                                    }
                                },
                            )
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsCheckbox(checked = checked)
                        Text(
                            text = option,
                            modifier = Modifier.padding(start = 4.dp),
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { onSelectionChanged(workingSelection) }) {
                Text("OK")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ItemsDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(title) },
        edgeToEdgeContent = true,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(
                    top = HarmonicDimens.compose_settings_dialog_item_top_padding,
                ),
            ) {
                itemsIndexed(options) { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { onSelected(index) }
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option,
                            color = HarmonicTheme.colors.textPrimary,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun MessageActionDialog(
    title: String? = null,
    message: CharSequence,
    positiveLabel: String? = null,
    negativeLabel: String? = null,
    neutralLabel: String? = null,
    onPositive: () -> Unit = {},
    onNegative: () -> Unit = {},
    onNeutral: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let {
            {
                SettingsDialogTitle(it)
            }
        },
        text = {
            Text(
                text = message.toString(),
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
        },
        confirmButton = {
            positiveLabel?.let { label ->
                SettingsDialogTextButton(onClick = onPositive) {
                    Text(label)
                }
            }
        },
        dismissButton = {
            negativeLabel?.let { label ->
                SettingsDialogTextButton(onClick = onNegative) {
                    Text(label)
                }
            }
        },
        neutralButton = {
            neutralLabel?.let { label ->
                SettingsDialogTextButton(onClick = onNeutral) {
                    Text(label)
                }
            }
        },
    )
}

@Composable
fun SettingsDialogTitle(title: String) {
    Text(
        text = title,
        color = HarmonicTheme.colors.textPrimary,
        fontFamily = ProductSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    )
}

@Composable
fun EditableStringListDialog(
    title: String,
    subtitle: String,
    inputLabel: String,
    initialItems: List<String>,
    emptyMessage: String,
    suggestions: List<String> = emptyList(),
    normalize: (String) -> String = String::trim,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var items by remember(initialItems) { mutableStateOf(initialItems) }
    var input by remember { mutableStateOf("") }

    fun add(rawValue: String) {
        val value = normalize(rawValue)
        if (value.isNotBlank() && items.none { it.equals(value, ignoreCase = true) }) {
            items = items + value
            input = ""
        }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(inputLabel) },
                        singleLine = true,
                    )
                    SettingsDialogTextButton(
                        onClick = { add(input) },
                        enabled = input.isNotBlank(),
                    ) {
                        Text("Add")
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        suggestions.take(3).forEach { suggestion ->
                            SettingsDialogTextButton(
                                onClick = { add(suggestion) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                if (items.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        modifier = Modifier.padding(top = 18.dp),
                        color = HarmonicTheme.colors.storyDisabled,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        items(items) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { items = items - item },
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_close),
                                        contentDescription = "Remove $item",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { onSave(items) }) {
                Text("Done")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    initialValue: String,
    allowEmpty: Boolean,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 5,
                maxLines = if (singleLine) 1 else 10,
            )
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = { onSave(value.trim()) },
                enabled = allowEmpty || value.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        neutralButton = onReset?.let { reset ->
            {
                SettingsDialogTextButton(onClick = reset) {
                    Text("Reset")
                }
            }
        },
    )
}

object SimpleMessageDialogController {
    private var state by mutableStateOf<Pair<String, String>?>(null)

    fun show(title: String, message: String) {
        state = title to message
    }

    @Composable
    fun Content() {
        val current = state ?: return
        MessageActionDialog(
            title = current.first,
            message = current.second,
            negativeLabel = "Done",
            onNegative = { state = null },
            onDismiss = { state = null },
        )
    }
}
