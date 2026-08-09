@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.RepliesChecker
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private enum class DebugNotificationAction {
    Enable,
    Test,
}

@Composable
fun DebugNotificationsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var username by remember {
        mutableStateOf(RepliesChecker.getConfiguredUsername(context))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf(
            RepliesChecker.getConfiguredUsername(context)
                .takeIf(String::isNotBlank)
                ?.let { "Reply notifications are on for $it." }
                .orEmpty(),
        )
    }
    var loading by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<DebugNotificationAction?>(null) }
    var notificationsActive by remember {
        mutableStateOf(RepliesChecker.notificationsAreActive(context))
    }

    fun enableNotifications(requestedUsername: String) {
        loading = true
        status = "Setting up reply notifications..."
        RepliesChecker.enable(context, requestedUsername) { success ->
            loading = false
            if (success) {
                username = RepliesChecker.getConfiguredUsername(context)
                status = "Reply notifications are active for $username."
            } else {
                status = "Could not enable reply notifications for $requestedUsername."
            }
            notificationsActive = RepliesChecker.notificationsAreActive(context)
        }
    }

    fun testNotification(requestedUsername: String) {
        loading = true
        status = "Looking for a recent reply..."
        RepliesChecker.sendLatestDebugNotification(context, requestedUsername) { result ->
            loading = false
            status = when (result) {
                RepliesChecker.DebugNotificationResult.SENT ->
                    "Sent a notification for the latest recent reply."
                RepliesChecker.DebugNotificationResult.NO_RECENT_REPLY ->
                    "No recent reply found for $requestedUsername."
                RepliesChecker.DebugNotificationResult.USER_NOT_FOUND ->
                    "Could not find HN user $requestedUsername."
                RepliesChecker.DebugNotificationResult.FAILED ->
                    "Could not send a test notification."
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (!granted) {
            status = "Notification permission denied."
        } else {
            when (action) {
                DebugNotificationAction.Enable -> enableNotifications(username.trim())
                DebugNotificationAction.Test -> testNotification(username.trim())
                null -> Unit
            }
        }
    }

    fun runWithNotificationPermission(action: DebugNotificationAction) {
        val requestedUsername = username.trim()
        if (requestedUsername.isEmpty()) {
            error = "Enter a username"
            return
        }
        error = null
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        when (action) {
            DebugNotificationAction.Enable -> enableNotifications(requestedUsername)
            DebugNotificationAction.Test -> testNotification(requestedUsername)
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
                    supportingText = error?.let { message ->
                        {
                            Text(message)
                        }
                    },
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
                    onClick = { runWithNotificationPermission(DebugNotificationAction.Test) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                DebugNotificationButton(
                    label = "Activate",
                    icon = Res.drawable.ic_notifications,
                    enabled = username.isNotBlank() && !loading,
                    onClick = { runWithNotificationPermission(DebugNotificationAction.Enable) },
                )
                if (notificationsActive) {
                    DebugNotificationButton(
                        label = "Turn off",
                        icon = Res.drawable.ic_close,
                        enabled = !loading,
                        onClick = {
                            RepliesChecker.disable(context)
                            status = "Reply notifications turned off."
                            notificationsActive = false
                        },
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
        shapes = ButtonDefaults.shapes(),
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
