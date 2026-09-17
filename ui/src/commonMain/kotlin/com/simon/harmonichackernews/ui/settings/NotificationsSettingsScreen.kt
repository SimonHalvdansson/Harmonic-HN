package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.presentation.NotificationSettingsOperation
import com.simon.harmonichackernews.presentation.NotificationsSettingsRuntime
import com.simon.harmonichackernews.presentation.NotificationsSettingsState
import com.simon.harmonichackernews.settings.ReplyNotificationFrequency
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun NotificationsSettingsRoute(
    accounts: ObservableHackerNewsAccountRepository,
    notifications: ReplyNotificationRuntime?,
    showNavigation: Boolean,
    onBack: () -> Unit,
    deliveryAllowed: Boolean = true,
    refreshVersion: Int = 0,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit = { it(true) },
    onOpenSystemSettings: (() -> Unit)? = null,
) {
    val accountState by accounts.accountState.collectAsState()
    val username = accountState.accountOrNull?.username
    if (username == null) {
        LaunchedEffect(accountState) {
            if (accountState == HackerNewsAccountState.LoggedOut) onBack()
        }
        return
    }
    // Leaving the account cancels setup/check requests and ignores stale permission callbacks.
    key(username) {
        val runtime = remember(notifications) { NotificationsSettingsRuntime(username, notifications) }
        val state by runtime.state.collectAsState()
        val scope = rememberCoroutineScope()
        var requestingPermission by remember { mutableStateOf(false) }
        LaunchedEffect(runtime, refreshVersion) { runtime.refresh() }
        NotificationsSettingsScreen(
            username = username,
            state = state,
            requestingPermission = requestingPermission,
            deliveryAllowed = deliveryAllowed,
            showNavigation = showNavigation,
            onBack = onBack,
            onEnabledChanged = { enabled ->
                if (enabled) {
                    requestingPermission = true
                    onRequestPermission { granted ->
                        scope.launch {
                            requestingPermission = false
                            if (accounts.accountState.value.accountOrNull?.username == username) {
                                if (granted) runtime.enable() else runtime.permissionDenied()
                            }
                        }
                    }
                } else {
                    runtime.disable()
                }
            },
            onCheckNow = { scope.launch { runtime.checkNow() } },
            onFrequencyChanged = runtime::setCheckFrequency,
            onOpenSystemSettings = onOpenSystemSettings,
        )
    }
}

@Composable
fun NotificationsSettingsScreen(
    username: String,
    state: NotificationsSettingsState,
    requestingPermission: Boolean,
    deliveryAllowed: Boolean,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onFrequencyChanged: (ReplyNotificationFrequency) -> Unit,
    onOpenSystemSettings: (() -> Unit)?,
) {
    val busy = requestingPermission || state.operation != null
    var frequencyDialogOpen by rememberSaveable { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(Res.string.settings_section_notifications),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = listOf(state, deliveryAllowed, requestingPermission).hashCode(),
    ) {
        item {
            SettingsMainToggle(
                title = "Reply notifications",
                checked = state.enabled || state.operation == NotificationSettingsOperation.Enabling,
                enabled = state.supported && !busy,
                onCheckedChange = onEnabledChanged,
            )
            Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 24.dp)) {
                Text(
                    text = "Get notified when someone replies to your comments or comments on your stories.",
                    color = HarmonicTheme.colors.textSecondary,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                val status = when {
                    !state.supported -> "Reply notifications are not available on this platform yet."
                    requestingPermission -> "Waiting for notification permission…"
                    !deliveryAllowed && state.enabled && !busy ->
                        "Android is blocking reply notifications. Allow them in Notification settings below."
                    state.message.isNotEmpty() -> state.message
                    else -> null
                }
                status?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (state.isError || !deliveryAllowed && state.enabled && !busy) {
                            MaterialTheme.colorScheme.error
                        } else {
                            HarmonicTheme.colors.textSecondary
                        },
                        fontFamily = ProductSansFontFamily,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
        item {
            SettingsCategory("Replies") {
                SettingRow(
                    title = username,
                    summary = "Your Hacker News account",
                    icon = Res.drawable.ic_person,
                    onClick = null,
                )
                SettingsDivider()
                SettingRow(
                    title = "Background check frequency",
                    summary = state.checkFrequency.label +
                        ". Requires an internet connection. Your device may delay delivery to save battery.",
                    icon = Res.drawable.ic_schedule,
                    enabled = state.supported && !busy,
                    onClick = { frequencyDialogOpen = true },
                )
                SettingsDivider()
                SettingRow(
                    title = "Check now",
                    summary = "Look for new replies and notify you if any are found.",
                    icon = Res.drawable.ic_refresh,
                    enabled = state.enabled && state.supported && deliveryAllowed && !busy,
                    onClick = onCheckNow,
                )
            }
        }
        if (onOpenSystemSettings != null) {
            item {
                SettingsCategory("On this device") {
                    SettingRow(
                        title = "Notification settings",
                        summary = if (deliveryAllowed) {
                            "Manage sound, vibration and notification display."
                        } else {
                            "Notifications are blocked by Android. Allow them here to receive replies."
                        },
                        icon = Res.drawable.ic_notifications,
                        onClick = onOpenSystemSettings,
                    )
                }
            }
        }
    }
    if (frequencyDialogOpen) {
        SingleChoiceDialog(
            title = "Background check frequency",
            options = ReplyNotificationFrequency.entries.map { it.minutes.toString() to it.label },
            selected = state.checkFrequency.minutes.toString(),
            onDismiss = { frequencyDialogOpen = false },
            onSelected = {
                onFrequencyChanged(ReplyNotificationFrequency.fromMinutes(it.toInt()))
                frequencyDialogOpen = false
            },
        )
    }
}
