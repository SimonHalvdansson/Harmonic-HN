package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource
import com.simon.harmonichackernews.resources.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import com.simon.harmonichackernews.ui.common.HarmonicSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

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
internal fun SettingsCheckbox(
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
fun SettingsMainToggle(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = HarmonicTheme.colors.settingsMainToggle.copy(
            alpha = if (enabled) 1f else SettingsMainToggleDisabledAlpha,
        ),
        animationSpec = tween(SettingsMainToggleColorAnimationDurationMillis),
        label = "settings main toggle background",
    )
    val textColor by animateColorAsState(
        targetValue = HarmonicTheme.colors.settingsMainToggleText.copy(
            alpha = if (enabled) 1f else SettingsMainToggleDisabledAlpha,
        ),
        animationSpec = tween(SettingsMainToggleColorAnimationDurationMillis),
        label = "settings main toggle text",
    )
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
            .background(backgroundColor)
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
            color = textColor,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            lineHeight = 21.sp,
        )
        HarmonicSwitch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

private const val SettingsMainToggleDisabledAlpha = 0.6f

private const val SettingsMainToggleColorAnimationDurationMillis = 240

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
            .background(itemBackgroundColor())
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
                tint = iconTint ?: HarmonicTheme.colors.iconTint,
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
                    color = HarmonicTheme.colors.mutedText,
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
            HarmonicSwitch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
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
 * Renders an inline segmented preference. Optional title and summary text stay inside the same
 * preference above the controls, matching custom View preference layouts.
 */
@Composable
fun <T> SegmentedSetting(
    title: String? = null,
    summary: String? = null,
    options: List<Pair<T, String>>,
    optionIcons: Map<T, DrawableResource> = emptyMap(),
    selected: T,
    enabled: Boolean = true,
    buttonHeight: Dp = HarmonicDimens.compose_settings_segmented_button_height,
    optionWeights: Map<T, Float> = emptyMap(),
    disabledOptions: Set<T> = emptySet(),
    containerColor: Color = itemBackgroundColor(),
    optionContent: (@Composable (T, Boolean) -> Unit)? = null,
    onSelected: (T) -> Unit,
) {
    val hasHeader = !title.isNullOrBlank() || !summary.isNullOrBlank()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(
                horizontal = HarmonicDimens.compose_settings_row_horizontal_padding,
                vertical = HarmonicDimens.compose_settings_inline_control_padding,
            ),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = HarmonicTheme.colors.textPrimary,
                fontFamily = ProductSansFontFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            )
        }
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                modifier = if (title.isNullOrBlank()) {
                    Modifier
                } else {
                    Modifier.padding(
                        top = HarmonicDimens.compose_settings_inline_control_summary_top_margin,
                    )
                },
                color = HarmonicTheme.colors.mutedText,
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
                    top = if (hasHeader) {
                        HarmonicDimens.compose_settings_inline_control_top_margin
                    } else {
                        0.dp
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = selected == value
                val optionEnabled = enabled && value !in disabledOptions
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
                val selectedBackground = HarmonicTheme.colors.secondaryContainer
                Row(
                    modifier = Modifier
                        .weight(optionWeights[value] ?: 1f)
                        .alpha(if (value in disabledOptions) 0.38f else 1f)
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
                            enabled = optionEnabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            onClick = { onSelected(value) },
                        ),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (optionContent != null) {
                        optionContent(value, isSelected)
                    } else {
                        optionIcons[value]?.let { icon ->
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) {
                                    HarmonicTheme.colors.onSecondaryContainer
                                } else {
                                    HarmonicTheme.colors.iconTint
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = label,
                            color = if (isSelected) {
                                HarmonicTheme.colors.onSecondaryContainer
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
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBackgroundColor())
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
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
