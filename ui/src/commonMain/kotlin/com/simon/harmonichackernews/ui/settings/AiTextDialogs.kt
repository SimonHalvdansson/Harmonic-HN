package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.simon.harmonichackernews.resources.HarmonicDimens
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

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
    onSave: suspend (String) -> String?,
    onDismiss: () -> Unit,
    capitalization: androidx.compose.ui.text.input.KeyboardCapitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
    maxLength: Int? = null,
) {
    var value by rememberSaveable(title, hint, initialValue) { mutableStateOf(initialValue) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    val normalizedValue = if (trimValue) value.trim() else value
    val lengthError = maxLength?.takeIf { normalizedValue.length > it }?.let { "Use $it characters or fewer" }
    val displayedError = lengthError ?: error

    fun save() {
        if (saving || dismissal.isClosing) return
        val savedValue = if (trimValue) value.trim() else value
        if (maxLength != null && savedValue.length > maxLength) return
        if (!allowEmpty && savedValue.isEmpty()) {
            error = "Required"
            return
        }
        saving = true
        scope.launch {
            try {
                error = onSave(savedValue)
                if (error == null) dismissal.request(onDismiss)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Couldn’t save"
            } finally {
                saving = false
            }
        }
    }

    fun dismiss() { if (!saving) dismissal.request(onDismiss) }

    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(
            dismissOnBackPress = !saving && !dismissal.isClosing,
            dismissOnClickOutside = !saving && !dismissal.isClosing,
        ),
        title = { SettingsDialogTitle(title) },
        text = {
            CompleteSettingsDialogDismissal(dismissal)
            RequestSettingsDialogFocus(focusRequester, enabled = !dismissal.isClosing && !saving)
            OutlinedTextField(
                value = value,
                onValueChange = {
                    if (!saving && !dismissal.isClosing) {
                        value = it
                        error = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(top = 4.dp)
                    .then(
                        if (maxLines <= 1 && maxLength == null && displayedError == null) {
                            Modifier.height(
                                HarmonicDimens.compose_settings_dialog_single_line_field_height,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .focusRequester(focusRequester),
                label = { Text(hint) },
                isError = displayedError != null,
                supportingText = if (maxLength != null) {
                    { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(displayedError.orEmpty(), Modifier.weight(1f))
                        Text("${normalizedValue.length} / $maxLength")
                    } }
                } else displayedError?.let { message -> { Text(message) } },
                singleLine = maxLines <= 1,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = AppUiFontFamily,
                    fontSize = textSizeSp.sp,
                    lineHeight = if (maxLines > 1) (textSizeSp + 3).sp else (textSizeSp + 4).sp,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (asciiInput) KeyboardType.Ascii else KeyboardType.Text,
                    capitalization = capitalization,
                    imeAction = if (maxLines <= 1) ImeAction.Done else ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }, enabled = !saving && !dismissal.isClosing && lengthError == null &&
                (maxLength == null || allowEmpty || normalizedValue.isNotEmpty())) { Text("Save") }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = ::dismiss, enabled = !saving && !dismissal.isClosing) { Text("Cancel") }
        },
        neutralButton = if (showReset) {
            {
                SettingsDialogTextButton(
                    onClick = {
                        error = null
                        value = defaultValue
                    },
                    enabled = !saving && !dismissal.isClosing,
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
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }

    fun save() {
        if (dismissal.isClosing) return
        val savedUrl = url.trim()
        if (savedUrl.isEmpty()) {
            error = "Enter a base URL"
            return
        }
        dismissal.request {
            onSave(savedUrl)
            onDismiss()
        }
    }
    fun dismiss() = dismissal.request(onDismiss)

    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        properties = DialogProperties(
            dismissOnBackPress = !dismissal.isClosing,
            dismissOnClickOutside = !dismissal.isClosing,
        ),
        title = { SettingsDialogTitle("Base URL") },
        text = {
            CompleteSettingsDialogDismissal(dismissal)
            RequestSettingsDialogFocus(focusRequester, enabled = !dismissal.isClosing)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Presets",
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AppUiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                LazyRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(presets, key = { it.id }, contentType = { "provider-preset" }) { provider ->
                        val selected = normalizeBaseUrl(provider.baseUrl) == normalizeBaseUrl(url)
                        FilterChip(
                            selected = selected,
                            enabled = !dismissal.isClosing,
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
                        if (!dismissal.isClosing) {
                            url = it
                            error = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
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
            SettingsDialogTextButton(onClick = { save() }, enabled = !dismissal.isClosing) { Text("Save") }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = ::dismiss, enabled = !dismissal.isClosing) { Text("Cancel") }
        },
    )
}

private fun normalizeBaseUrl(url: String): String = url.trim().trimEnd('/')
