package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.NitterInstance

@Composable
fun NitterInstanceDialog(
    initialValue: String,
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun save() {
        invalid = !onSave(value)
        if (!invalid) onDismiss()
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Nitter instance URL") },
        text = {
            Column {
                Text(
                    text = "Used for Twitter/X redirects and link previews. Enter an HTTP or HTTPS " +
                        "server URL without a path, query, or fragment.",
                    style = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        lineHeight = TextUnit.Unspecified,
                    ),
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; invalid = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Instance URL") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) {
                        { Text("Enter a valid server URL, such as https://nitter.example.org") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
            }
        },
        neutralButton = {
            SettingsDialogTextButton(onClick = {
                value = NitterInstance.DEFAULT_URL
                invalid = false
            }) { Text("Default") }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = { save() }) { Text("Save") }
        },
    )
}
