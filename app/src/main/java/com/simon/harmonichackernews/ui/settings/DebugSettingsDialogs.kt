package com.simon.harmonichackernews.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.network.RepliesChecker
import kotlinx.coroutines.launch

private data class PendingDebugNotificationRequest(
    val action: DebugNotificationAction,
    val username: String,
    val onResult: (DebugNotificationActionResult) -> Unit,
)

@Composable
fun DebugNotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val initialUsername = remember { RepliesChecker.getConfiguredUsername(context) }
    val initiallyActive = remember { RepliesChecker.notificationsAreActive(context) }
    var pendingRequest by remember {
        mutableStateOf<PendingDebugNotificationRequest?>(null)
    }

    fun perform(request: PendingDebugNotificationRequest) {
        when (request.action) {
            DebugNotificationAction.DISABLE -> {
                RepliesChecker.disable(context)
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = DebugNotificationOutcome.DISABLED,
                        notificationsActive = false,
                    ),
                )
            }
            DebugNotificationAction.ENABLE -> coroutineScope.launch {
                val success = RepliesChecker.enable(context, request.username)
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = if (success) {
                            DebugNotificationOutcome.ENABLED
                        } else {
                            DebugNotificationOutcome.ENABLE_FAILED
                        },
                        configuredUsername = RepliesChecker.getConfiguredUsername(context),
                        notificationsActive = RepliesChecker.notificationsAreActive(context),
                    ),
                )
            }
            DebugNotificationAction.TEST -> coroutineScope.launch {
                val outcome = when (
                    RepliesChecker.sendLatestDebugNotification(context, request.username)
                ) {
                    RepliesChecker.DebugNotificationResult.SENT ->
                        DebugNotificationOutcome.TEST_SENT
                    RepliesChecker.DebugNotificationResult.NO_RECENT_REPLY ->
                        DebugNotificationOutcome.NO_RECENT_REPLY
                    RepliesChecker.DebugNotificationResult.USER_NOT_FOUND ->
                        DebugNotificationOutcome.USER_NOT_FOUND
                    RepliesChecker.DebugNotificationResult.FAILED ->
                        DebugNotificationOutcome.TEST_FAILED
                }
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = outcome,
                        notificationsActive = RepliesChecker.notificationsAreActive(context),
                    ),
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val request = pendingRequest
        pendingRequest = null
        if (request != null) {
            if (granted) {
                perform(request)
            } else {
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = DebugNotificationOutcome.PERMISSION_DENIED,
                        notificationsActive = RepliesChecker.notificationsAreActive(context),
                    ),
                )
            }
        }
    }

    SharedDebugNotificationsDialog(
        initialUsername = initialUsername,
        initiallyActive = initiallyActive,
        onDismiss = onDismiss,
        onActionRequested = { action, username, onResult ->
            val request = PendingDebugNotificationRequest(action, username, onResult)
            val permissionMissing = action != DebugNotificationAction.DISABLE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            if (permissionMissing) {
                pendingRequest = request
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                perform(request)
            }
        },
    )
}
