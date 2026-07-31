package com.simon.harmonichackernews.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.common.HarmonicTopAppBar
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private data class SettingsListEntry(
    val section: SettingsSection,
    @DrawableRes val icon: Int,
)

private val MainSettingsEntries = listOf(
    SettingsListEntry(SettingsSection.Appearance, R.drawable.ic_style),
    SettingsListEntry(SettingsSection.Stories, R.drawable.ic_newspaper),
    SettingsListEntry(SettingsSection.Comments, R.drawable.ic_comment),
    SettingsListEntry(SettingsSection.WebLinks, R.drawable.ic_web_asset),
    SettingsListEntry(SettingsSection.FiltersTags, R.drawable.ic_filter_list),
    SettingsListEntry(SettingsSection.AiSummary, R.drawable.ic_auto_awesome),
    SettingsListEntry(SettingsSection.Data, R.drawable.ic_library_books),
    SettingsListEntry(SettingsSection.Debug, R.drawable.ic_api),
    SettingsListEntry(SettingsSection.About, R.drawable.ic_info),
)

@Composable
fun SettingsListScreen(
    selectedSection: SettingsSection,
    showSelection: Boolean,
    onBack: () -> Unit,
    onSectionSelected: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsCardShape = RoundedCornerShape(
        dimensionResource(R.dimen.settings_list_segment_corner_radius),
    )
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
        HarmonicTopAppBar(
            title = "Settings",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = dimensionResource(R.dimen.settings_list_segment_horizontal_margin),
                top = dimensionResource(R.dimen.settings_list_first_segment_top_margin),
                end = dimensionResource(R.dimen.settings_list_segment_horizontal_margin),
                bottom = dimensionResource(R.dimen.settings_list_segment_bottom_margin) +
                    navigationBarPadding,
            ),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(settingsCardShape),
                ) {
                    MainSettingsEntries
                        .filter { it.section != SettingsSection.Debug || BuildConfig.DEBUG }
                        .forEachIndexed { index, entry ->
                            SettingsNavigationRow(
                                title = entry.section.title,
                                icon = entry.icon,
                                selected = showSelection && (
                                    selectedSection == entry.section ||
                                    (
                                        entry.section == SettingsSection.About &&
                                            selectedSection == SettingsSection.Licenses
                                        )
                                    ),
                                onClick = { onSectionSelected(entry.section) },
                            )
                            if (
                                index != MainSettingsEntries
                                    .filter {
                                        it.section != SettingsSection.Debug || BuildConfig.DEBUG
                                    }
                                    .lastIndex
                            ) {
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
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = dimensionResource(R.dimen.compose_settings_row_min_height),
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
                horizontal = dimensionResource(
                    R.dimen.compose_settings_row_horizontal_padding,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(
                dimensionResource(R.dimen.compose_settings_row_icon_size),
            ),
            tint = HarmonicTheme.colors.drawable,
        )
        Spacer(
            modifier = Modifier.width(
                dimensionResource(R.dimen.compose_settings_row_icon_end_space),
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
        HarmonicTopAppBar(
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
                    start = dimensionResource(
                        R.dimen.compose_settings_category_padding_start,
                    ),
                    top = dimensionResource(R.dimen.compose_settings_category_padding_top),
                    end = dimensionResource(
                        R.dimen.settings_list_segment_horizontal_margin,
                    ),
                    bottom = dimensionResource(
                        R.dimen.compose_settings_category_padding_bottom,
                    ),
                ),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        Spacer(
            modifier = Modifier.height(
                dimensionResource(R.dimen.compose_settings_category_segment_gap),
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
                horizontal = dimensionResource(
                    R.dimen.settings_list_segment_horizontal_margin,
                ),
            )
            .clip(
                RoundedCornerShape(
                    dimensionResource(R.dimen.settings_list_segment_corner_radius),
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
                start = dimensionResource(R.dimen.settings_list_segment_horizontal_margin),
                top = dimensionResource(R.dimen.settings_list_first_segment_top_margin),
                end = dimensionResource(R.dimen.settings_list_segment_horizontal_margin),
                bottom = 16.dp,
            )
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .alpha(if (enabled) 1f else 0.6f)
            .background(HarmonicTheme.colors.settingsMainToggle)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
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
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    @DrawableRes icon: Int?,
    summaryFontSizeSp: Float = 14f,
    summaryLineHeightSp: Float = 18f,
    summaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = dimensionResource(R.dimen.compose_settings_row_min_height),
            )
            .background(HarmonicTheme.colors.settingsSegment)
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                horizontal = dimensionResource(
                    R.dimen.compose_settings_row_horizontal_padding,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(
                    dimensionResource(R.dimen.compose_settings_row_icon_size),
                ),
                tint = HarmonicTheme.colors.drawable,
            )
            Spacer(
                modifier = Modifier.width(
                    dimensionResource(R.dimen.compose_settings_row_icon_end_space),
                ),
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    vertical = dimensionResource(
                        R.dimen.compose_settings_row_text_vertical_padding,
                    ),
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
                    dimensionResource(R.dimen.compose_settings_row_trailing_start_space),
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
    @DrawableRes icon: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        summary = summary,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
            .height(dimensionResource(R.dimen.settings_list_segment_internal_gap))
            .background(HarmonicTheme.colors.background),
    )
}

@Composable
fun SegmentedSetting(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    enabled: Boolean = true,
    onSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HarmonicTheme.colors.settingsSegment)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(
                horizontal = dimensionResource(
                    R.dimen.compose_settings_row_horizontal_padding,
                ),
                vertical = dimensionResource(
                    R.dimen.compose_settings_inline_control_padding,
                ),
            ),
    ) {
        Text(
            text = title,
            color = HarmonicTheme.colors.textPrimary,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(
                    top = dimensionResource(
                        R.dimen.compose_settings_inline_control_top_margin,
                    ),
                ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = selected == value
                val outerCorner = dimensionResource(
                    R.dimen.compose_settings_segmented_button_corner_radius,
                )
                val defaultInnerCorner = dimensionResource(
                    R.dimen.compose_settings_segmented_button_inner_corner_radius,
                )
                val pressedInnerCorner = dimensionResource(
                    R.dimen.compose_settings_segmented_button_pressed_inner_corner_radius,
                )
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(
                            dimensionResource(
                                R.dimen.compose_settings_segmented_button_height,
                            ),
                        )
                        .background(
                            if (isSelected) {
                                selectedBackground
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            shape,
                        )
                        .then(
                            Modifier.border(
                                1.dp,
                                if (isSelected) {
                                    selectedBackground
                                } else {
                                    HarmonicTheme.colors.outlineVariant
                                },
                                shape,
                            ),
                        )
                        .clip(shape)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            onClick = { onSelected(value) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
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
                horizontal = dimensionResource(
                    R.dimen.compose_settings_row_horizontal_padding,
                ),
                vertical = dimensionResource(
                    R.dimen.compose_settings_inline_control_padding,
                ),
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
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSelected by rememberUpdatedState(onSelected)
    DisposableEffect(context, title, options, selected) {
        val labels = options.map { it.second }.toTypedArray()
        val checkedItem = options.indexOfFirst { it.first == selected }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(labels, checkedItem) { dialogInterface, which ->
                currentOnSelected(options[which].first)
                dialogInterface.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                currentOnDismiss()
            }
            .setOnCancelListener {
                currentOnDismiss()
            }
            .create()
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}

@Composable
fun MultiChoiceDialog(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSelectionChanged: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)
    DisposableEffect(context, title, options, selected) {
        val workingSelection = selected.toMutableSet()
        val labels = options.toTypedArray()
        val checkedItems = BooleanArray(options.size) { options[it] in selected }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMultiChoiceItems(labels, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    workingSelection += options[which]
                } else {
                    workingSelection -= options[which]
                }
            }
            .setPositiveButton("OK") { _, _ ->
                currentOnSelectionChanged(workingSelection)
            }
            .setNegativeButton("Cancel") { _, _ ->
                currentOnDismiss()
            }
            .setOnCancelListener {
                currentOnDismiss()
            }
            .create()
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
}

@Composable
fun ItemsDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSelected by rememberUpdatedState(onSelected)
    DisposableEffect(context, title, options) {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                currentOnSelected(which)
            }
            .setNegativeButton("Cancel") { _, _ ->
                currentOnDismiss()
            }
            .setOnCancelListener {
                currentOnDismiss()
            }
            .create()
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
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
    val context = LocalContext.current
    val currentOnPositive by rememberUpdatedState(onPositive)
    val currentOnNegative by rememberUpdatedState(onNegative)
    val currentOnNeutral by rememberUpdatedState(onNeutral)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(
        context,
        title,
        message,
        positiveLabel,
        negativeLabel,
        neutralLabel,
    ) {
        val builder = MaterialAlertDialogBuilder(context)
        title?.let { builder.setTitle(it) }
        builder.setMessage(message)
        positiveLabel?.let {
            builder.setPositiveButton(it) { _, _ ->
                currentOnPositive()
            }
        }
        negativeLabel?.let {
            builder.setNegativeButton(it) { _, _ ->
                currentOnNegative()
            }
        }
        neutralLabel?.let {
            builder.setNeutralButton(it) { _, _ ->
                currentOnNeutral()
            }
        }
        val dialog = builder
            .setOnCancelListener {
                currentOnDismiss()
            }
            .create()
        dialog.show()
        onDispose {
            dialog.setOnCancelListener(null)
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }
    }
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

    AlertDialog(
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
                    TextButton(
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
                            TextButton(
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
                                        painter = painterResource(R.drawable.ic_close),
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
            TextButton(onClick = { onSave(items) }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
    AlertDialog(
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
            TextButton(
                onClick = { onSave(value.trim()) },
                enabled = allowEmpty || value.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                onReset?.let { reset ->
                    TextButton(onClick = reset) {
                        Text("Reset")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
fun rememberPreferenceRefresh(): Int {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(preferences) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                refresh++
            }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return refresh
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
