package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

data class AiBaseUrlPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
)

@Composable
fun AiSummaryTextDialog(
    title: String,
    hint: String,
    initialValue: String,
    defaultValue: String,
    minLines: Int,
    maxLines: Int,
    textSizeSp: Int,
    trimValue: Boolean,
    allowEmpty: Boolean,
    showReset: Boolean,
    asciiInput: Boolean,
    onSave: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun save() {
        val savedValue = if (trimValue) value.trim() else value
        if (!allowEmpty && savedValue.isEmpty()) {
            error = "Required"
            return
        }
        error = onSave(savedValue)
        if (error == null) onDismiss()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(
                        if (maxLines <= 1) {
                            Modifier.height(
                                HarmonicDimens.compose_settings_dialog_single_line_field_height,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(focusRequester),
                label = { Text(hint) },
                isError = error != null,
                supportingText = error?.let { message -> { Text(message) } },
                singleLine = maxLines <= 1,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = ProductSansFontFamily,
                    fontSize = textSizeSp.sp,
                    lineHeight = if (maxLines > 1) (textSizeSp + 3).sp else (textSizeSp + 4).sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (asciiInput) KeyboardType.Ascii else KeyboardType.Text,
                    imeAction = if (maxLines <= 1) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }) { Text("Save") }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
        },
        neutralButton = if (showReset) {
            {
                SettingsDialogTextButton(
                    onClick = {
                        error = null
                        value = defaultValue
                    },
                ) {
                    Text("Reset")
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun AiSummaryBaseUrlDialog(
    initialUrl: String,
    presets: List<AiBaseUrlPreset>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    fun save() {
        val savedUrl = url.trim()
        if (savedUrl.isEmpty()) {
            error = "Enter a base URL"
            return
        }
        onSave(savedUrl)
        onDismiss()
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Base URL") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Presets",
                    modifier = Modifier.padding(top = 4.dp),
                    color = HarmonicTheme.colors.storyNormal,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(presets, key = { it.id }) { provider ->
                        val selected = normalizeBaseUrl(provider.baseUrl) == normalizeBaseUrl(url)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                url = provider.baseUrl
                                error = null
                            },
                            label = { Text(provider.label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp)
                        .focusRequester(focusRequester),
                    label = { Text("Base URL") },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }) { Text("Save") }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')
