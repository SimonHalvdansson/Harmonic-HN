@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun NighttimeRangeDialog(
    initialHours: IntArray,
    is24Hour: Boolean,
    onRangeSelected: (fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    require(initialHours.size == 4)
    val fromState = rememberTimePickerState(
        initialHour = initialHours[0],
        initialMinute = initialHours[1],
        is24Hour = is24Hour,
    )
    val toState = rememberTimePickerState(
        initialHour = initialHours[2],
        initialMinute = initialHours[3],
        is24Hour = is24Hour,
    )
    var choosingTo by remember { mutableStateOf(false) }
    val timePickerColors = TimePickerDefaults.colors(
        MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.outline,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.surface,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.onSurface,
    )

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle(if (choosingTo) "To:" else "From:") },
        text = {
            AnimatedContent(targetState = choosingTo, label = "nighttime time picker") { to ->
                TimePicker(
                    state = if (to) toState else fromState,
                    colors = timePickerColors,
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(
                onClick = {
                    if (!choosingTo) {
                        choosingTo = true
                    } else {
                        onRangeSelected(
                            fromState.hour,
                            fromState.minute,
                            toState.hour,
                            toState.minute,
                        )
                        onDismiss()
                    }
                },
            ) {
                Text(if (choosingTo) "OK" else "Next")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(
                onClick = {
                    if (choosingTo) choosingTo = false else onDismiss()
                },
            ) {
                Text(if (choosingTo) "Back" else "Cancel")
            }
        },
    )
}
