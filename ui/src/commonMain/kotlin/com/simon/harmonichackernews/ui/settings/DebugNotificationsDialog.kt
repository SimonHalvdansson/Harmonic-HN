package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class DebugNotificationAction {
    ENABLE,
    TEST,
    DISABLE,
}

enum class DebugNotificationOutcome {
    ENABLED,
    ENABLE_FAILED,
    TEST_SENT,
    NO_RECENT_REPLY,
    USER_NOT_FOUND,
    TEST_FAILED,
    DISABLED,
    PERMISSION_DENIED,
}

data class DebugNotificationActionResult(
    val outcome: DebugNotificationOutcome,
    val configuredUsername: String = "",
    val notificationsActive: Boolean = false,
)

@Composable
fun DebugNotificationsDialog(
    initialUsername: String,
    initiallyActive: Boolean,
    onDismiss: () -> Unit,
    onActionRequested: (
        action: DebugNotificationAction,
        username: String,
        onResult: (DebugNotificationActionResult) -> Unit,
    ) -> Unit,
) {
    val currentOnActionRequested by rememberUpdatedState(onActionRequested)
    var username by remember { mutableStateOf(initialUsername) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf(
            initialUsername.takeIf(String::isNotBlank)
                ?.let { "Reply notifications are on for $it." }
                .orEmpty(),
        )
    }
    var loading by remember { mutableStateOf(false) }
    var notificationsActive by remember { mutableStateOf(initiallyActive) }

    fun run(action: DebugNotificationAction) {
        val requestedUsername = username.trim()
        if (action != DebugNotificationAction.DISABLE && requestedUsername.isEmpty()) {
            error = "Enter a username"
            return
        }
        error = null
        loading = true
        status = when (action) {
            DebugNotificationAction.ENABLE -> "Setting up reply notifications..."
            DebugNotificationAction.TEST -> "Looking for a recent reply..."
            DebugNotificationAction.DISABLE -> "Turning off reply notifications..."
        }
        currentOnActionRequested(action, requestedUsername) { result ->
            loading = false
            notificationsActive = result.notificationsActive
            status = when (result.outcome) {
                DebugNotificationOutcome.ENABLED -> {
                    username = result.configuredUsername.ifBlank { requestedUsername }
                    "Reply notifications are active for $username."
                }
                DebugNotificationOutcome.ENABLE_FAILED ->
                    "Could not enable reply notifications for $requestedUsername."
                DebugNotificationOutcome.TEST_SENT ->
                    "Sent a notification for the latest recent reply."
                DebugNotificationOutcome.NO_RECENT_REPLY ->
                    "No recent reply found for $requestedUsername."
                DebugNotificationOutcome.USER_NOT_FOUND ->
                    "Could not find HN user $requestedUsername."
                DebugNotificationOutcome.TEST_FAILED ->
                    "Could not send a test notification."
                DebugNotificationOutcome.DISABLED -> "Reply notifications turned off."
                DebugNotificationOutcome.PERMISSION_DENIED ->
                    "Notification permission denied."
            }
        }
    }

    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Debug notifications") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") },
                    isError = error != null,
                    supportingText = error?.let { message -> { Text(message) } },
                    singleLine = true,
                )
                Text(
                    text = status,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                    minLines = 1,
                )
                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }
                DebugNotificationButton(
                    label = "Test notification",
                    icon = Res.drawable.ic_notifications,
                    enabled = username.isNotBlank() && !loading,
                    onClick = { run(DebugNotificationAction.TEST) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                DebugNotificationButton(
                    label = "Activate",
                    icon = Res.drawable.ic_notifications,
                    enabled = username.isNotBlank() && !loading,
                    onClick = { run(DebugNotificationAction.ENABLE) },
                )
                if (notificationsActive) {
                    DebugNotificationButton(
                        label = "Turn off",
                        icon = Res.drawable.ic_close,
                        enabled = !loading,
                        onClick = { run(DebugNotificationAction.DISABLE) },
                    )
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
private fun DebugNotificationButton(
    label: String,
    icon: DrawableResource,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
    ) {
        Row {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                color = HarmonicTheme.colors.storyNormal,
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}
