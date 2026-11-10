package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.presentation.DebugAccountIdentity
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_account_circle
import com.simon.harmonichackernews.ui.common.Button
import com.simon.harmonichackernews.ui.common.OutlinedButton
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.theme.cardBackground
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DebugAccountControls(identity: DebugAccountIdentity, onOpenAccount: () -> Unit) {
    val state by identity.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf(state.username.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.username) { username = state.username.orEmpty() }
    fun select(value: String?) {
        try {
            identity.setUsername(value)
            username = value.orEmpty()
            error = null
            focus.clearFocus()
        } catch (_: Exception) { error = "Could not update the debug account. Try again." }
    }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.cardBackground).settingsSearchTarget("Account preview").padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Account preview", fontFamily = AppUiFontFamily, color = MaterialTheme.colorScheme.onSurface)
        Text("View public activity and test Account settings as another user. Your real Hacker News login stays unchanged.",
            fontFamily = AppUiFontFamily, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(username, { username = it; error = null }, modifier = Modifier.fillMaxWidth(),
            label = { Text("HN username") }, singleLine = true, isError = error != null,
            supportingText = error?.let { message -> { Text(message) } })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = { select(username.trim()) }, enabled = username.isNotBlank() && username.trim().none(Char::isWhitespace)) {
                Text("Use account", fontFamily = AppUiFontFamily)
            }
            OutlinedButton(onClick = { select(null) }, enabled = state.username != null) {
                Text("Use real account", fontFamily = AppUiFontFamily)
            }
        }
        Text(if (state.username != null) "Previewing ${state.username}" else "Using your real account",
            fontFamily = AppUiFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { focus.clearFocus(); onOpenAccount() }, modifier = Modifier.align(Alignment.End).settingsSearchTarget("Open Account settings")) {
            Icon(painterResource(Res.drawable.ic_account_circle), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Open Account settings", fontFamily = AppUiFontFamily)
        }
    }
}
