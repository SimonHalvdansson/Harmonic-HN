@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_keyboard_arrow_down
import com.simon.harmonichackernews.resources.ic_schedule
import com.simon.harmonichackernews.settings.AppearanceRuntime
import com.simon.harmonichackernews.settings.ThemeSelectionPolicy
import com.simon.harmonichackernews.settings.NighttimeSchedule
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.themeBackground
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun NighttimeRangeSetting(
    appearance: AppearanceRuntime,
    is24Hour: Boolean,
    onThemeChanged: () -> Unit,
) {
    val schedule = appearance.schedule
    var expanded by rememberSaveable { mutableStateOf(false) }
    // A new opening starts a fresh draft even if the closing animation is still running.
    var editSession by rememberSaveable { mutableIntStateOf(0) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(240), label = "Timed range caret")
    Column(Modifier.fillMaxWidth().themeBackground { cardBackground }) {
        SettingRow(
            title = "Timed range",
            summary = ThemeSelectionPolicy.formatSchedule(schedule, is24Hour),
            icon = Res.drawable.ic_schedule,
            onClick = {
                if (!expanded) editSession++
                expanded = !expanded
            },
            trailing = {
                Icon(
                    painterResource(Res.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                    tint = HarmonicTheme.colors.onSurfaceVariant,
                )
            },
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(240), expandFrom = Alignment.Top),
            exit = shrinkVertically(tween(240), shrinkTowards = Alignment.Top),
        ) {
            key(editSession, is24Hour) {
                NighttimeRangeEditor(
                    schedule, is24Hour,
                    onCancel = { expanded = false },
                    onSave = {
                        expanded = false
                        appearance.saveSchedule(it)
                        onThemeChanged()
                    },
                )
            }
        }
    }
}

@Composable
private fun NighttimeRangeEditor(
    schedule: NighttimeSchedule,
    is24Hour: Boolean,
    onCancel: () -> Unit,
    onSave: (NighttimeSchedule) -> Unit,
) {
    val start = rememberTimePickerState(schedule.fromHour, schedule.fromMinute, is24Hour)
    val end = rememberTimePickerState(schedule.toHour, schedule.toMinute, is24Hour)
    var editingEnd by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Start time", "End time").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = editingEnd == (index == 1),
                    onClick = { editingEnd = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(12.dp))
        key(editingEnd) {
            TimeScroll(state = if (editingEnd) end else start, shapes = TimePickerDefaults.shapes())
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onSave(NighttimeSchedule(start.hour, start.minute, end.hour, end.minute)) }) {
                Text("Save")
            }
        }
    }
}
