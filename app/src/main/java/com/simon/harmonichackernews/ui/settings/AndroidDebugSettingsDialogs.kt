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
import com.simon.harmonichackernews.network.LatestReplyLookupResult
import com.simon.harmonichackernews.network.ReplySubscriptionResult
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.launch

private data class PendingDebugNotificationRequest(
    val action: DebugNotificationAction,
    val username: String,
    val onResult: (DebugNotificationActionResult) -> Unit,
)

@Composable
fun AndroidDebugNotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val runtime = checkNotNull(LocalHarmonicUiDependencies.current.replyNotifications)
    val initialUsername = remember { runtime.configuredUsername }
    val initiallyActive = remember { runtime.isEnabled }
    var pendingRequest by remember {
        mutableStateOf<PendingDebugNotificationRequest?>(null)
    }

    fun perform(request: PendingDebugNotificationRequest) {
        when (request.action) {
            DebugNotificationAction.DISABLE -> {
                runtime.disable()
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = DebugNotificationOutcome.DISABLED,
                        notificationsActive = false,
                    ),
                )
            }
            DebugNotificationAction.ENABLE -> coroutineScope.launch {
                val success = runtime.enable(request.username) is ReplySubscriptionResult.Enabled
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = if (success) {
                            DebugNotificationOutcome.ENABLED
                        } else {
                            DebugNotificationOutcome.ENABLE_FAILED
                        },
                        configuredUsername = runtime.configuredUsername,
                        notificationsActive = runtime.isEnabled,
                    ),
                )
            }
            DebugNotificationAction.TEST -> coroutineScope.launch {
                val outcome = when (runtime.publishLatest(request.username)) {
                    is LatestReplyLookupResult.Found ->
                        DebugNotificationOutcome.TEST_SENT
                    LatestReplyLookupResult.NoRecentReply ->
                        DebugNotificationOutcome.NO_RECENT_REPLY
                    LatestReplyLookupResult.UserNotFound ->
                        DebugNotificationOutcome.USER_NOT_FOUND
                    is LatestReplyLookupResult.Failed ->
                        DebugNotificationOutcome.TEST_FAILED
                }
                request.onResult(
                    DebugNotificationActionResult(
                        outcome = outcome,
                        notificationsActive = runtime.isEnabled,
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
                        notificationsActive = runtime.isEnabled,
                    ),
                )
            }
        }
    }

    DebugNotificationsDialog(
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
