package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.common.HarmonicSwitch
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
fun HiddenCommentsDialog(
    hideFlaggedComments: Boolean,
    hideDeadComments: Boolean,
    hideDelayedComments: Boolean,
    onBooleanChanged: (CommentsBooleanSetting, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Hide comments") },
        edgeToEdgeContent = true,
        scrollableContent = true,
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "Applies when the entire comment is [flagged], [dead] or [delayed].",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
                HiddenCommentSwitch("Flagged", hideFlaggedComments) {
                    onBooleanChanged(CommentsBooleanSetting.HideFlaggedComments, it)
                }
                HiddenCommentSwitch("Dead", hideDeadComments) {
                    onBooleanChanged(CommentsBooleanSetting.HideDeadComments, it)
                }
                HiddenCommentSwitch("Delayed", hideDelayedComments) {
                    onBooleanChanged(CommentsBooleanSetting.HideDelayedComments, it)
                }
            }
        },
        neutralButton = {
            SettingsDialogTextButton(
                onClick = {
                    onBooleanChanged(CommentsBooleanSetting.HideFlaggedComments, false)
                    onBooleanChanged(CommentsBooleanSetting.HideDeadComments, true)
                    onBooleanChanged(CommentsBooleanSetting.HideDelayedComments, false)
                },
            ) { Text("Reset") }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun HiddenCommentSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = ProductSansFontFamily,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        HarmonicSwitch(checked = checked, onCheckedChange = null)
    }
}
