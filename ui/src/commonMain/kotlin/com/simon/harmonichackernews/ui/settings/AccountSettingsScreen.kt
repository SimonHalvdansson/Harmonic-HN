package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.presentation.NotificationSettingsOperation
import com.simon.harmonichackernews.presentation.NotificationsSettingsRuntime
import com.simon.harmonichackernews.presentation.NotificationsSettingsState
import com.simon.harmonichackernews.presentation.DebugAccountIdentity
import com.simon.harmonichackernews.presentation.DebugAccountIdentityState
import com.simon.harmonichackernews.presentation.accountLastCheckedLabel
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.ReplyNotificationFrequency
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.OutlinedButton
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.common.HarmonicFilterButton
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.content.HarmonicDropdownMenu
import com.simon.harmonichackernews.ui.content.HarmonicMenuText
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.themeBackground
import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun AccountSettingsRoute(
    accounts: ObservableHackerNewsAccountRepository,
    notifications: ReplyNotificationRuntime?,
    showNavigation: Boolean,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenReply: (HackerNewsReply) -> Unit,
    deliveryAllowed: Boolean = true,
    refreshVersion: Int = 0,
    onRequestPermission: (onResult: (Boolean) -> Unit) -> Unit = { it(true) },
    onOpenSystemSettings: (() -> Unit)? = null,
    debugIdentity: DebugAccountIdentity? = null,
    settings: AppSettingsRepository? = null,
) {
    val notificationHost = notifications?.settingsHost
    val lifecycleOwner = LocalLifecycleOwner.current
    var hostDeliveryAllowed by remember(notificationHost) { mutableStateOf(false) }
    var hostRefreshVersion by remember(notificationHost) { mutableIntStateOf(0) }
    DisposableEffect(notificationHost, lifecycleOwner) {
        var active = true
        fun refresh() {
            notificationHost?.refresh { allowed ->
                if (active) { hostDeliveryAllowed = allowed; hostRefreshVersion++ }
            }
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose { active = false; lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val allowed = if (notificationHost != null) hostDeliveryAllowed else deliveryAllowed
    val currentAccountState by accounts.accountState.collectAsStateWithLifecycle()
    val currentDebugAccount by debugIdentity?.state?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(DebugAccountIdentityState()) }
    val preferences by settings?.updates?.collectAsStateWithLifecycle(initialValue = settings.snapshot())
        ?: remember { mutableStateOf(null) }
    // Retain the outgoing account surface until the fade finishes. Each identity keeps its
    // own runtime and list state, so its history cannot jump to the incoming account mid-exit.
    AnimatedContent(
        targetState = currentAccountState to currentDebugAccount,
        contentKey = { (account, debug) -> (debug.username ?: account.accountOrNull?.username) to (debug.username != null) },
        modifier = Modifier.fillMaxSize().testTag("account-identity-transition"),
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 80)) +
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 60 }) togetherWith
                fadeOut(tween(100))
        },
        contentAlignment = Alignment.TopStart,
        label = "account identity",
    ) { (accountState, debugAccount) ->
        val account = accountState.accountOrNull
        val username = debugAccount.username ?: account?.username
        key(accountState, debugAccount) {
            val runtime = remember(username, notifications) { NotificationsSettingsRuntime(username.orEmpty(), notifications) }
            val state by runtime.state.collectAsStateWithLifecycle()
            val changes by notifications?.activity?.changes?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
            val scope = rememberCoroutineScope()
            var requestingPermission by remember { mutableStateOf(false) }
            var loggingOut by remember { mutableStateOf(false) }
            var accountError by remember { mutableStateOf<String?>(null) }
            var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
            LaunchedEffect(runtime, refreshVersion, hostRefreshVersion, changes) {
                runtime.refresh()
                now = Clock.System.now().toEpochMilliseconds()
            }
            LaunchedEffect(runtime) {
                if (username != null && !state.activity.historyLoaded) runtime.refreshActivity()
            }
            LaunchedEffect(Unit) {
                while (isActive) { now = Clock.System.now().toEpochMilliseconds(); delay(60_000) }
            }
            AccountSettingsScreen(
                username = username,
                state = state,
                busy = requestingPermission || loggingOut || state.operation != null,
                deliveryAllowed = allowed,
                showNavigation = showNavigation,
                nowMillis = now,
                accountMessage = accountError,
                accountActionBusy = loggingOut,
                debugAccount = debugAccount.username != null,
                avatarOptions = preferences?.comments?.userAvatarOptions ?: UserAvatarOptions(),
                onBack = onBack,
                onLogin = onLogin,
                onLogout = {
                    if (debugAccount.username != null) debugIdentity?.setUsername(null)
                    else if (account != null && !loggingOut) scope.launch {
                        loggingOut = true
                        try {
                            if (!accounts.clearAccountIfMatches(account)) accountError = "Could not log out. Please try again."
                        } finally { loggingOut = false }
                    }
                },
                onEnabledChanged = { enabled ->
                    if (enabled) scope.launch {
                        runtime.enable()
                        if (runtime.state.value.enabled && !allowed) {
                            requestingPermission = true
                            val onResult: (Boolean) -> Unit = { granted ->
                                requestingPermission = false
                                if (notificationHost != null) hostDeliveryAllowed = granted
                                if (accounts.accountState.value === accountState && debugIdentity?.state?.value?.username == debugAccount.username && !granted) runtime.permissionDenied()
                            }
                            if (notificationHost != null) notificationHost.requestPermission(onResult) else onRequestPermission(onResult)
                        }
                    } else runtime.disable()
                },
                onCheckNow = { scope.launch { runtime.refreshActivity() } },
                onFrequencyChanged = runtime::setCheckFrequency,
                onPreferencesChanged = runtime::setAlertPreferences,
                onOpenSystemSettings = notificationHost?.let { { it.openSystemSettings() } } ?: onOpenSystemSettings,
                onOpenReply = { entry ->
                    username?.let { notifications?.activity?.markRead(it, entry.reply.id) }
                    onOpenReply(entry.reply)
                },
                onMarkAllRead = { username?.let { notifications?.activity?.markRead(it) } },
                onMute = { id, title, muted -> username?.let { notifications?.activity?.setMuted(it, id, title, muted) } },
            )
        }
    }
}

@Composable
fun AccountSettingsScreen(
    username: String?,
    state: NotificationsSettingsState,
    busy: Boolean,
    deliveryAllowed: Boolean,
    showNavigation: Boolean,
    nowMillis: Long,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onCheckNow: () -> Unit,
    onFrequencyChanged: (ReplyNotificationFrequency) -> Unit,
    onPreferencesChanged: (AccountAlertPreferences) -> Unit,
    onOpenSystemSettings: (() -> Unit)?,
    onOpenReply: (AccountActivityEntry) -> Unit,
    onMarkAllRead: () -> Unit,
    onMute: (Int, String, Boolean) -> Unit,
    accountMessage: String? = null,
    accountActionBusy: Boolean = false,
    debugAccount: Boolean = false,
    avatarOptions: UserAvatarOptions = UserAvatarOptions(),
) {
    var dialog by rememberSaveable(username) { mutableStateOf<String?>(null) }
    var unreadOnly by rememberSaveable(username) { mutableStateOf(false) }
    var showAll by rememberSaveable(username) { mutableStateOf(false) }
    val activity = state.activity
    val preferences = activity.alerts
    val filtered = remember(activity.entries, unreadOnly) { activity.entries.filter { !unreadOnly || !it.read } }
    val visible = if (showAll) filtered else filtered.take(5)
    SettingsPage(
        title = stringResource(Res.string.settings_section_account),
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = listOf(state, deliveryAllowed, busy, unreadOnly, showAll).hashCode(),
    ) {
        item(key = "account") {
            SettingsCard(Modifier.padding(top = 8.dp)) {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (username != null) UserAvatar(username, Modifier.size(40.dp), avatarOptions) else Icon(
                            painterResource(Res.drawable.ic_account_circle), null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Column(Modifier.weight(1f)) {
                            AccountText(username ?: "Your Hacker News account", 21, FontWeight.Bold, primary = true)
                            AccountText(when {
                                debugAccount -> "Debug account · Public activity"
                                username != null -> "Logged in to Hacker News"
                                else -> "Keep up with your conversations"
                            }, 13, primary = true)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (username == null) {
                        AccountText("Log in to see replies to your comments and activity on your submissions.", 14, primary = true)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onLogin, enabled = !busy, modifier = Modifier.settingsSearchTarget("Log in")) {
                            ActionIcon(Res.drawable.ic_login)
                            Text("Log in", fontFamily = AppUiFontFamily)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(
                                if (state.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant))
                            AccountText(if (state.enabled) "Background checking on" else "Background checking off", 14, primary = true)
                        }
                        Spacer(Modifier.height(4.dp))
                        AccountText(accountLastCheckedLabel(activity.lastCheckedMillis, nowMillis), 13, primary = true)
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(onClick = onCheckNow, enabled = state.supported && !busy,
                                modifier = Modifier.settingsSearchTarget("Check now")) {
                                if (state.operation == NotificationSettingsOperation.Checking) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                } else ActionIcon(Res.drawable.ic_refresh)
                                Text("Check now", fontFamily = AppUiFontFamily)
                            }
                            OutlinedButton(onClick = onLogout, enabled = !accountActionBusy,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .38f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (accountActionBusy) .24f else .65f))) {
                                ActionIcon(Res.drawable.ic_logout)
                                Text(if (debugAccount) "Use real account" else "Log out", fontFamily = AppUiFontFamily)
                            }
                        }
                        if (state.enabled && !deliveryAllowed && onOpenSystemSettings != null) {
                            Spacer(Modifier.height(8.dp))
                            AccountText("System notification alerts are disabled", 13, primary = true)
                            TextButton(onClick = onOpenSystemSettings, contentPadding = PaddingValues(vertical = 4.dp)) {
                                Text("Allow alerts in system settings", fontFamily = AppUiFontFamily)
                                Spacer(Modifier.width(8.dp))
                                Icon(painterResource(Res.drawable.ic_open_in_new), null, Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        if (username != null) {
            item(key = "account-status") {
                val message = accountMessage ?: state.message.takeIf { it.isNotBlank() }
                if (message != null) Text(message, Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state.isError || accountMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppUiFontFamily, fontSize = 14.sp)
            }
            item(key = "recent-activity") {
                SettingsCategory("Recent activity") {
                    Row(Modifier.fillMaxWidth().themeBackground { cardBackground }.settingsSearchTarget("Recent activity")
                        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val colors = harmonicFilterButtonColors()
                            HarmonicFilterButton("All", !unreadOnly, 0, colors, { unreadOnly = false }, Modifier.widthIn(min = 64.dp), lastPosition = 1)
                            HarmonicFilterButton("Unread", unreadOnly, 1, colors, { unreadOnly = true }, Modifier.widthIn(min = 80.dp), lastPosition = 1)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onMarkAllRead, enabled = activity.entries.any { !it.read }, modifier = Modifier.testTag("account-mark-all-read")) {
                            Text("Mark all read", fontFamily = AppUiFontFamily)
                        }
                    }
                    SettingsDivider()
                    val emptyTitle = if (visible.isNotEmpty()) "" else when {
                        state.operation == NotificationSettingsOperation.Checking -> "Checking your conversations…"
                        !state.supported -> "Activity checking is not available on this platform yet."
                        state.isError && !activity.initialized -> "Your activity could not be loaded. Try Check now."
                        unreadOnly -> "You're all caught up"
                        else -> "No recent replies yet"
                    }
                    val description = if (visible.isEmpty() && !unreadOnly && !state.isError && state.supported)
                        "Replies to your recent comments and submissions will appear here, even with alerts off." else null
                    AnimatedContent(
                        targetState = AccountActivityContent(visible, unreadOnly, emptyTitle, description, filtered.size, showAll),
                        transitionSpec = {
                            (fadeIn(tween(180)) togetherWith fadeOut(tween(120)))
                                .using(SizeTransform(clip = true) { _, _ -> tween(260) })
                        },
                        contentAlignment = Alignment.TopStart,
                        modifier = Modifier.fillMaxWidth().themeBackground { cardBackground }.testTag("account-activity-content"),
                        label = "account activity filter",
                    ) { content ->
                        Column(Modifier.fillMaxWidth()) {
                            if (content.entries.isEmpty()) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(painterResource(Res.drawable.ic_forum), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(12.dp))
                                    AccountText(content.emptyTitle, 15, FontWeight.Medium)
                                    content.description?.let {
                                        Spacer(Modifier.height(6.dp))
                                        AccountText(it, 13)
                                    }
                                }
                            }
                            content.entries.forEachIndexed { index, entry ->
                                if (index > 0) SettingsDivider()
                                AccountActivityRow(entry, activity.mutedDiscussions.any { it.id == entry.reply.discussionId }, nowMillis, avatarOptions,
                                    onOpen = { onOpenReply(entry) },
                                    onMute = { muted -> onMute(entry.reply.discussionId, entry.reply.storyTitle.ifBlank { "Discussion ${entry.reply.discussionId}" }, muted) })
                            }
                            if (content.count > 5) {
                                SettingsDivider()
                                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (content.showAll) "Show less" else "Show all (${content.count})", fontFamily = AppUiFontFamily)
                                }
                            }
                        }
                    }
                }
            }
            item(key = "account-alerts") {
                SettingsCategory("Notifications") {
                    SwitchSettingRow("Replies to your comments", icon = Res.drawable.ic_comment, checked = preferences.replies,
                        enabled = state.backgroundSupported, onCheckedChange = { onPreferencesChanged(preferences.copy(replies = it)) })
                    SettingsDivider()
                    SwitchSettingRow("Comments on your submissions", icon = Res.drawable.ic_newspaper, checked = preferences.submissionComments,
                        enabled = state.backgroundSupported, onCheckedChange = { onPreferencesChanged(preferences.copy(submissionComments = it)) })
                    SettingsDivider()
                    SettingRow("Muted discussions", summary = if (activity.mutedDiscussions.isEmpty()) "Mute alerts from a discussion using its activity menu" else "${activity.mutedDiscussions.size} muted · Activity stays in your inbox",
                        icon = Res.drawable.ic_notifications_off, onClick = { dialog = "muted" })
                    if (onOpenSystemSettings != null) {
                        SettingsDivider()
                        Column(Modifier.fillMaxWidth().themeBackground { cardBackground }.padding(16.dp)) {
                            if (!deliveryAllowed) {
                                Text("System notifications are disabled. Your activity will still appear here.", color = MaterialTheme.colorScheme.error,
                                    fontFamily = AppUiFontFamily, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(onClick = onOpenSystemSettings,
                                modifier = Modifier.align(Alignment.End).settingsSearchTarget("System notification settings").testTag("account-system-settings")) {
                                Text("System notification settings", fontFamily = AppUiFontFamily)
                                Spacer(Modifier.width(8.dp))
                                Icon(painterResource(Res.drawable.ic_open_in_new), null, Modifier.size(18.dp))
                            }
                        }
                    }
                    if (!state.backgroundSupported) Text("Background alerts are not available on this platform yet. Check now and your activity history still work.",
                        Modifier.fillMaxWidth().themeBackground { cardBackground }.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = AppUiFontFamily, fontSize = 13.sp)
                }
            }
            item(key = "account-background") {
                SettingsCategory("Background checking") {
                    SwitchSettingRow("Check automatically", summary = if (state.enabled) {
                        "${state.checkFrequency.label} · ${if (preferences.wifiOnly) "Wi-Fi only" else "Any connection"}" +
                            "\n${preferences.hoursLabel}${if (preferences.limitedHours) " · Local time" else ""}"
                    } else "Check now is always available", icon = Res.drawable.ic_refresh,
                        checked = state.enabled || state.operation == NotificationSettingsOperation.Enabling,
                        enabled = state.backgroundSupported && !busy, onCheckedChange = onEnabledChanged)
                    SettingsDivider()
                    SettingRow("Background check frequency", summary = state.checkFrequency.label, icon = Res.drawable.ic_schedule,
                        enabled = state.backgroundSupported && !busy, onClick = { dialog = "frequency" })
                    SettingsDivider()
                    SwitchSettingRow("Wi-Fi only", summary = "Applies to automatic checks", icon = Res.drawable.ic_wifi,
                        checked = preferences.wifiOnly, enabled = state.backgroundSupported && !busy,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(wifiOnly = it)) })
                    SettingsDivider()
                    SwitchSettingRow("Limit checking hours", summary = if (preferences.limitedHours) "Checks resume during your chosen hours" else "Check at any time of day",
                        icon = Res.drawable.ic_schedule, checked = preferences.limitedHours, enabled = state.backgroundSupported && !busy,
                        onCheckedChange = { onPreferencesChanged(preferences.copy(limitedHours = it)) })
                    SettingsDivider()
                    SettingRow("Checking hours", summary = "${AccountAlertPreferences.timeLabel(preferences.startMinute)}–${AccountAlertPreferences.timeLabel(preferences.endMinute)} · Local time", icon = Res.drawable.ic_schedule,
                        enabled = state.backgroundSupported && !busy && preferences.limitedHours, onClick = { dialog = "hours" })
                }
                Text("The frequency is a minimum interval. Your device decides when automatic checks run and may delay them to save battery. Activity covers replies to comments and submissions from the last 14 days; the latest 100 replies are kept on this device.",
                    Modifier.padding(horizontal = 24.dp, vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppUiFontFamily, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
    when (dialog) {
        "frequency" -> SingleChoiceDialog(
            title = "Background check frequency", options = ReplyNotificationFrequency.entries.map { it.minutes.toString() to it.label },
            selected = state.checkFrequency.minutes.toString(), onDismiss = { dialog = null },
            onSelected = { onFrequencyChanged(ReplyNotificationFrequency.fromMinutes(it.toInt())); dialog = null })
        "hours" -> AccountCheckingHoursDialog(preferences, onDismiss = { dialog = null }, onSelected = { start, end ->
            onPreferencesChanged(preferences.copy(startMinute = start, endMinute = end)); dialog = null
        })
        "muted" -> SettingsAlertDialog(
            onDismissRequest = { dialog = null }, title = { SettingsDialogTitle("Muted discussions") },
            text = {
                if (activity.mutedDiscussions.isEmpty()) Text("Use a discussion's activity menu to mute its alerts. Its replies will still appear in your activity.")
                else LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(activity.mutedDiscussions, key = { it.id }) { discussion ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(discussion.title, fontFamily = AppUiFontFamily)
                            TextButton(onClick = { onMute(discussion.id, discussion.title, false) }) { Text("Unmute") }
                        }
                    }
                }
            },
            confirmButton = { SettingsDialogTextButton(onClick = { dialog = null }) { Text("Done") } },
        )
    }
}

private data class AccountActivityContent(
    val entries: List<AccountActivityEntry>, val unreadOnly: Boolean, val emptyTitle: String,
    val description: String?, val count: Int, val showAll: Boolean,
)

@Composable
private fun AccountActivityRow(entry: AccountActivityEntry, muted: Boolean, now: Long, avatarOptions: UserAvatarOptions, onOpen: () -> Unit, onMute: (Boolean) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val reply = entry.reply
    Row(Modifier.fillMaxWidth().testTag("account-activity-${reply.id}").themeBackground { cardBackground }.clickable(onClick = onOpen).padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UserAvatar(reply.by, Modifier.size(28.dp).testTag("account-avatar-${reply.id}"), avatarOptions)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!entry.read) Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text(reply.by, fontFamily = AppUiFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (reply.time > 0) AccountText(RelativeTimeFormatter.format(reply.time.toLong(), now), 12)
            }
            AccountText(if (reply.kind == AccountActivityKind.Reply) "Replied to your comment" else "Commented on your submission", 12)
            if (reply.storyTitle.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(reply.storyTitle, fontFamily = AppUiFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(5.dp))
            Text(reply.text, fontFamily = AppUiFontFamily, fontSize = 14.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            AnimatedVisibility(
                visible = muted,
                enter = expandVertically(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180, delayMillis = 40)),
                exit = shrinkVertically(tween(260, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
                label = "discussion muted",
            ) {
                Column(Modifier.testTag("account-muted-${reply.id}")) {
                    Spacer(Modifier.height(6.dp))
                    AccountText("Discussion muted", 12)
                }
            }
        }
        // Center the 48dp touch target on the two-line author/action block above the body.
        Box(Modifier.offset(y = (-8).dp)) {
            TextButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp), shape = CircleShape) {
                Icon(painterResource(Res.drawable.ic_more_vert), "Activity options for reply ${reply.id}", Modifier.size(20.dp))
            }
            HarmonicDropdownMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                DropdownMenuItem(text = { HarmonicMenuText(if (muted) "Unmute discussion" else "Mute discussion") },
                    onClick = { menuOpen = false; onMute(!muted) })
            }
        }
    }
}

@Composable
private fun AccountCheckingHoursDialog(preferences: AccountAlertPreferences, onDismiss: () -> Unit, onSelected: (Int, Int) -> Unit) {
    var start by rememberSaveable { mutableStateOf(AccountAlertPreferences.timeLabel(preferences.startMinute)) }
    var end by rememberSaveable { mutableStateOf(AccountAlertPreferences.timeLabel(preferences.endMinute)) }
    fun parse(value: String): Int? {
        val parts = value.split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }
    val valid = parse(start) != null && parse(end) != null && parse(start) != parse(end)
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        title = { SettingsDialogTitle("Checking hours") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Use local time in 24-hour format. A range can continue past midnight.")
            OutlinedTextField(start, { start = it }, label = { Text("From (HH:mm)") }, singleLine = true, isError = parse(start) == null)
            OutlinedTextField(end, { end = it }, label = { Text("Until (HH:mm)") }, singleLine = true, isError = parse(end) == null || parse(start) == parse(end))
        } },
        confirmButton = { SettingsDialogTextButton(onClick = { onSelected(requireNotNull(parse(start)), requireNotNull(parse(end))) }, enabled = valid) { Text("Save") } },
        dismissButton = { SettingsDialogTextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AccountText(text: String, size: Int, weight: FontWeight = FontWeight.Normal, primary: Boolean = false) {
    Text(text, fontFamily = AppUiFontFamily, fontSize = size.sp, lineHeight = (size + 5).sp, fontWeight = weight,
        color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ActionIcon(icon: DrawableResource) {
    Icon(painterResource(icon), null, Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
}
