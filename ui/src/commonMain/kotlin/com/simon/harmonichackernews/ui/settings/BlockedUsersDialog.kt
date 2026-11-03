package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_close
import com.simon.harmonichackernews.resources.ic_edit
import com.simon.harmonichackernews.resources.ic_person
import com.simon.harmonichackernews.ui.common.PlatformDisableDialogWindowAnimations
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import org.jetbrains.compose.resources.painterResource

@Composable
fun BlockedUsersDialog(
    users: List<String>,
    legacyUsers: List<String>,
    onUsersChanged: (List<String>) -> Unit,
    onProfileRequested: (String) -> Unit,
    onLegacyReviewRequested: (String) -> Unit,
    onLegacyDiscardRequested: (String) -> Unit,
    onDismiss: () -> Unit,
    nestedDialog: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    // Opening a profile is a keyboard handoff, not disposal of this retained list dialog.
    val childOpening = remember { SettingsDialogDismissState() }
    val inputLocked = dismissal.isClosing || childOpening.isClosing
    val additions = input.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
        .filterNot { it in users }
    fun add() {
        if (additions.isEmpty() || inputLocked) return
        onUsersChanged(users + additions)
        input = ""
    }
    fun dismiss() {
        childOpening.cancel()
        dismissal.request(onDismiss)
    }
    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = modifier.testTag("blocked-users-dialog"),
        title = { SettingsDialogTitle("Blocked users") },
        scrollableContent = true,
        compactSpacing = true,
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            CompleteSettingsDialogDismissal(childOpening)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Case sensitive",
                    fontFamily = ProductSansFontFamily, fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { if (!inputLocked) input = it },
                        label = { Text("Exact username") }, singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    SettingsDialogTextButton(onClick = ::add, enabled = additions.isNotEmpty() && !inputLocked) {
                        Text("Block")
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 296.dp).testTag("blocked-users-list")) {
                    if (users.isEmpty() && legacyUsers.isEmpty()) item {
                        Text("No blocked users", Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (users.isNotEmpty()) item(key = "active-heading") {
                        Text("${users.size} active", fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 2.dp))
                    }
                    items(users, key = { "active:$it" }) { username ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("blocked-active-row:$username").animateItem(
                                fadeInSpec = tween(160), placementSpec = tween(220), fadeOutSpec = null,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(username, Modifier.weight(1f), fontFamily = ProductSansFontFamily)
                            IconButton(onClick = { childOpening.request { onProfileRequested(username) } },
                                enabled = !inputLocked, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(Res.drawable.ic_person), "View profile for $username")
                            }
                            IconButton(onClick = { onUsersChanged(users.filterNot { it == username }) },
                                enabled = !inputLocked, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(Res.drawable.ic_close), "Unblock $username")
                            }
                        }
                    }
                    if (legacyUsers.isNotEmpty()) item(key = "legacy-heading") {
                        Column(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${legacyUsers.size} ${if (legacyUsers.size == 1) "needs" else "need"} review · Inactive", fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary)
                            Text("Older versions discarded capitalization. These saved entries do not block anyone until you confirm the exact username.",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(legacyUsers, key = { "legacy:$it" }) { username ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .testTag("blocked-legacy-row:$username").animateItem(
                                fadeInSpec = tween(160), placementSpec = tween(220), fadeOutSpec = null,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(username, fontFamily = ProductSansFontFamily)
                                Text("Needs exact username", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { childOpening.request { onLegacyReviewRequested(username) } },
                                enabled = !inputLocked, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(Res.drawable.ic_edit), "Review saved block for $username")
                            }
                            IconButton(onClick = { onLegacyDiscardRequested(username) },
                                enabled = !inputLocked, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(Res.drawable.ic_close), "Remove saved block for $username")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = ::dismiss, enabled = !dismissal.isClosing) { Text("Done") }
        },
        foreground = { nestedDialog?.invoke() },
    )
}

/** The empty field makes confirming a formerly folded identity an explicit user decision. */
@Composable
fun LegacyUsernameReviewDialog(
    title: String,
    savedUsername: String,
    savedTag: String? = null,
    onResolve: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var username by rememberSaveable(savedUsername) { mutableStateOf("") }
    var error by rememberSaveable(savedUsername) { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val dismissal = remember { SettingsDialogDismissState() }
    fun dismiss() = dismissal.request(onDismiss)
    fun confirm() {
        if (username.isBlank() || dismissal.isClosing) return
        val exact = username.trim()
        dismissal.request {
            val failure = onResolve(exact)
            if (failure == null) onDismiss() else error = failure
        }
    }
    SettingsAlertDialog(
        onDismissRequest = ::dismiss,
        modifier = Modifier.testTag("legacy-username-review-dialog"),
        title = { SettingsDialogTitle(title) },
        scrollableContent = true,
        text = {
            PlatformDisableDialogWindowAnimations()
            CompleteSettingsDialogDismissal(dismissal)
            RequestSettingsDialogFocus(focus, enabled = !dismissal.isClosing)
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("The saved username “$savedUsername” may have lost its capitalization in an older version. It is inactive until you confirm the exact HN username.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                savedTag?.let { Text("Saved tag: $it", fontFamily = ProductSansFontFamily) }
                OutlinedTextField(
                    value = username,
                    onValueChange = { if (!dismissal.isClosing) { username = it; error = null } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    label = { Text("Exact username") }, singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error ?: "Capitalization must match the account on HN.") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                )
            }
        },
        confirmButton = {
            SettingsDialogTextButton(onClick = ::confirm, enabled = username.isNotBlank() && !dismissal.isClosing) {
                Text("Confirm username")
            }
        },
        dismissButton = {
            SettingsDialogTextButton(onClick = ::dismiss, enabled = !dismissal.isClosing) { Text("Cancel") }
        },
    )
}
