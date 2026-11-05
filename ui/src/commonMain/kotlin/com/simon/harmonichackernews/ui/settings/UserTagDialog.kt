package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import org.jetbrains.compose.resources.painterResource

@Composable
fun UserTagDialog(
    currentTag: String,
    onSave: (String, UserTagColor) -> Unit,
    onDismiss: () -> Unit,
    currentColor: UserTagColor = UserTagColor.Theme,
    userName: String = "",
) {
    var tagValue by rememberSaveable(currentTag, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(currentTag, selection = TextRange(currentTag.length)))
    }
    val tag = tagValue.text
    var colorValue by rememberSaveable(currentColor) { mutableStateOf(currentColor.storageValue) }
    val selectedColor = UserTagColor.fromStorage(colorValue)
    val focusRequester = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    fun saveTag() {
        val savedTag = tag.trim()
        val savedColor = selectedColor
        dismissal.request { onSave(savedTag, savedColor) }
    }
    fun dismissTag() = dismissal.request(onDismiss)

    SettingsAlertDialog(
        onDismissRequest = ::dismissTag,
        modifier = Modifier.testTag("user-tag-dialog"),
        title = { SettingsDialogTitle("Set tag") },
        scrollableContent = true,
        text = {
            // A native enter/exit window animation competes with the animated IME insets.
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            if (!dismissal.isClosing) RequestSettingsDialogFocus(focusRequester)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = tagValue,
                    onValueChange = { if (!dismissal.isClosing) tagValue = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(focusRequester),
                    label = { Text("Tag") },
                    trailingIcon = {
                        if (tag.isNotEmpty()) {
                            IconButton(
                                enabled = !dismissal.isClosing,
                                onClick = { tagValue = TextFieldValue(""); focusRequester.requestFocus() },
                            ) {
                                Icon(painterResource(Res.drawable.ic_close), contentDescription = "Clear tag")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveTag() }),
                )
                Column {
                    Text("Color", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    UserTagColorChoices(
                        selectedColor = selectedColor,
                        enabled = !dismissal.isClosing,
                        onSelected = { colorValue = it.storageValue },
                    )
                }
                UserTagPreview(userName, tag, selectedColor)
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = ::dismissTag, enabled = !dismissal.isClosing) { Text("Cancel") }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = ::saveTag, enabled = !dismissal.isClosing) { Text("Set") }
        },
    )
}
