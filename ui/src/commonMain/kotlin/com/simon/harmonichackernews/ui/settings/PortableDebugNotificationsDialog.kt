package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.simon.harmonichackernews.network.LatestReplyLookupResult
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.network.ReplySubscriptionResult
import kotlinx.coroutines.launch

/** Shared test controls; native hosts provide authorization through the notification settings host. */
@Composable
internal fun PortableDebugNotificationsDialog(runtime: ReplyNotificationRuntime?, username: String?, onDismiss: () -> Unit, unavailableMessage: String? = null) {
    val scope = rememberCoroutineScope()
    DebugNotificationsDialog(
        initialUsername = runtime?.configuredUsername?.takeIf { it.isNotBlank() } ?: username.orEmpty(),
        initiallyActive = runtime?.isEnabled == true,
        nativeSupported = runtime?.backgroundSupported == true,
        unavailableMessage = unavailableMessage,
        onDismiss = onDismiss,
        onActionRequested = { action, requested, onResult ->
            fun perform() {
                scope.launch {
                    val outcome = when (action) {
                        DebugNotificationAction.DISABLE -> { runtime?.disable(); DebugNotificationOutcome.DISABLED }
                        DebugNotificationAction.ENABLE -> if (runtime?.enable(requested, accountScoped = false) is ReplySubscriptionResult.Enabled)
                            DebugNotificationOutcome.ENABLED else DebugNotificationOutcome.ENABLE_FAILED
                        DebugNotificationAction.TEST -> when (runtime?.publishLatest(requested)) {
                            is LatestReplyLookupResult.Found -> DebugNotificationOutcome.TEST_SENT
                            LatestReplyLookupResult.NoRecentReply -> DebugNotificationOutcome.NO_RECENT_REPLY
                            LatestReplyLookupResult.UserNotFound -> DebugNotificationOutcome.USER_NOT_FOUND
                            else -> DebugNotificationOutcome.TEST_FAILED
                        }
                    }
                    onResult(DebugNotificationActionResult(outcome, runtime?.configuredUsername.orEmpty(), runtime?.isEnabled == true))
                }
            }
            val host = runtime?.settingsHost
            if (action == DebugNotificationAction.DISABLE || host == null) perform()
            else host.requestPermission { granted ->
                if (granted) perform() else onResult(DebugNotificationActionResult(DebugNotificationOutcome.PERMISSION_DENIED))
            }
        },
    )
}
