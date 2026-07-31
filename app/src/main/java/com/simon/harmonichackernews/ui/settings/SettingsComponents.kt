package com.simon.harmonichackernews.ui.settings

import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.preference.PreferenceManager
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
internal fun SettingsAlertDialog(
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
    keepImeVisible: Boolean = false,
) {
    val configuration = LocalConfiguration.current
    val shortEdge = minOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val longEdge = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val usesTabletDialogWidth = configuration.smallestScreenWidthDp >= 600 &&
        longEdge >= shortEdge * 1.3f
    val dialogMaxWidth = dimensionResource(
        if (usesTabletDialogWidth) {
            R.dimen.compose_settings_dialog_tablet_max_width
        } else {
            R.dimen.compose_settings_dialog_max_width
        },
    )
    var dialogBounds by remember { mutableStateOf<Rect?>(null) }
    var dialogRootOffset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            securePolicy = properties.securePolicy,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = properties.decorFitsSystemWindows,
        ),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView, keepImeVisible) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (keepImeVisible) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            }
            onDispose {
                if (keepImeVisible) {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    dialogRootOffset = coordinates.positionInWindow()
                }
                .pointerInput(properties.dismissOnClickOutside, onDismissRequest) {
                    if (!properties.dismissOnClickOutside) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Final,
                        )
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Final)
                        val bounds = dialogBounds
                        if (
                            up != null &&
                            bounds != null &&
                            !bounds.contains(up.position + dialogRootOffset)
                        ) {
                            onDismissRequest()
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = dimensionResource(
                            R.dimen.compose_settings_dialog_horizontal_margin,
                        ),
                        vertical = dimensionResource(
                            R.dimen.compose_settings_dialog_vertical_margin,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = modifier
                        .widthIn(
                            max = dialogMaxWidth,
                        )
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            dialogBounds = coordinates.boundsInWindow()
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                ) {
                    Column {
                    title?.let { titleContent ->
                        Box(
                            modifier = Modifier.padding(
                                start = dimensionResource(
                                    R.dimen.compose_settings_dialog_content_padding,
                                ),
                                top = dimensionResource(
                                    R.dimen.compose_settings_dialog_content_padding,
                                ),
                                end = dimensionResource(
                                    R.dimen.compose_settings_dialog_content_padding,
                                ),
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
                                        dimensionResource(
                                            R.dimen.compose_settings_dialog_content_padding,
                                        )
                                    },
                                    top = if (title == null) {
                                        dimensionResource(
                                            R.dimen.compose_settings_dialog_content_padding,
                                        )
                                    } else {
                                        0.dp
                                    },
                                    end = if (edgeToEdgeContent) {
                                        0.dp
                                    } else {
                                        dimensionResource(
                                            R.dimen.compose_settings_dialog_content_padding,
                                        )
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
                                    horizontal = dimensionResource(
                                        R.dimen.compose_settings_dialog_content_padding,
                                    ),
                                    vertical = dimensionResource(
                                        R.dimen.compose_settings_dialog_action_vertical_padding,
                                    ),
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
internal fun SettingsRadioButton(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(
            dimensionResource(R.dimen.compose_settings_dialog_option_control_size),
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
            dimensionResource(R.dimen.compose_settings_dialog_option_control_size),
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
    @DrawableRes icon: Int?,
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(
                minHeight = dimensionResource(R.dimen.compose_settings_row_min_height),
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
                tint = iconTint ?: HarmonicTheme.colors.drawable,
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDialogTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        content = content,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDialogOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        content = content,
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
                    top = dimensionResource(
                        R.dimen.compose_settings_dialog_item_top_padding,
                    ),
                ),
            ) {
                items(options) { option ->
                    val index = options.indexOf(option)
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
internal fun SettingsDialogTitle(title: String) {
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
