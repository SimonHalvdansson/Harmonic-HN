package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.resources.*

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.simon.harmonichackernews.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.ui.common.PredictiveBackDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

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
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
) {
    PredictiveBackDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = false,
        ),
    ) {
        SettingsAlertDialogLayout(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            neutralButton = neutralButton,
            dismissButton = dismissButton,
            title = title,
            text = text,
            edgeToEdgeContent = edgeToEdgeContent,
            showButtons = showButtons,
            separateDismissButton = separateDismissButton,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            scrollableContent = scrollableContent,
            foreground = foreground,
            containerColor = containerColor,
        )
    }
}

@Composable
private fun SettingsAlertDialogLayout(
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
    dismissOnClickOutside: Boolean = true,
    scrollableContent: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    foreground: (@Composable BoxScope.() -> Unit)? = null,
) {
    val contentWindowInsets = synchronizedSettingsDialogInsets()
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
        if (dismissOnClickOutside) {
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
                .windowInsetsPadding(contentWindowInsets)
                .padding(
                    horizontal = HarmonicDimens.compose_settings_dialog_horizontal_margin,
                    vertical = HarmonicDimens.compose_settings_dialog_vertical_margin,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent()
                        }
                    },
                shape = MaterialTheme.shapes.extraLarge,
                color = containerColor,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            neutralButton?.invoke()
                            if (neutralButton != null) Spacer(Modifier.weight(1f))
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
        foreground?.invoke(this)
    }
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
                contentPadding = PaddingValues(vertical = 8.dp),
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
    description: String? = null,
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
                if (!description.isNullOrBlank()) {
                    item(key = "description") {
                        Text(
                            text = description,
                            modifier = Modifier.padding(
                                start = 24.dp,
                                top = 4.dp,
                                end = 24.dp,
                                bottom = 12.dp,
                            ),
                            color = HarmonicTheme.colors.mutedText,
                            fontFamily = ProductSansFontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
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
                lineHeight = 20.sp,
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
