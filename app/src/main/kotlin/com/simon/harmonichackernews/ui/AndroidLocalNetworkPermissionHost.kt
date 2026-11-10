package com.simon.harmonichackernews.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.AndroidLocalNetworkAccess
import com.simon.harmonichackernews.ui.settings.SettingsAlertDialog
import com.simon.harmonichackernews.ui.settings.SettingsDialogTextButton
import com.simon.harmonichackernews.ui.settings.SettingsDialogTitle

@Composable
internal fun AndroidLocalNetworkPermissionHost(access: AndroidLocalNetworkAccess) {
    if (Build.VERSION.SDK_INT < 37) return
    val context = LocalContext.current
    val state by access.uiState.collectAsStateWithLifecycle()
    var launchedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        launchedId?.let { access.complete(it, granted, showDenied = true) }
        launchedId = null
    }

    when (val current = state) {
        is AndroidLocalNetworkAccess.UiState.Request -> if (!current.systemPrompt) {
            SettingsAlertDialog(
                onDismissRequest = { access.complete(current.id, false) },
                title = { SettingsDialogTitle(stringResource(R.string.local_network_permission_title)) },
                text = { Text(stringResource(R.string.local_network_permission_reason, current.host)) },
                confirmButton = {
                    SettingsDialogTextButton(onClick = {
                        launchedId = current.id
                        access.beginSystemPrompt(current.id)
                        launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }) { Text(stringResource(R.string.local_network_continue)) }
                },
                dismissButton = {
                    SettingsDialogTextButton(onClick = { access.complete(current.id, false) }) {
                        Text(stringResource(R.string.local_network_not_now))
                    }
                },
            )
        }
        AndroidLocalNetworkAccess.UiState.Denied -> SettingsAlertDialog(
            onDismissRequest = access::dismissDenied,
            title = { SettingsDialogTitle(stringResource(R.string.local_network_denied_title)) },
            text = { Text(stringResource(R.string.local_network_denied_reason)) },
            confirmButton = {
                SettingsDialogTextButton(onClick = {
                    access.dismissDenied()
                    context.startActivity(Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ))
                }) { Text(stringResource(R.string.local_network_open_settings)) }
            },
            dismissButton = {
                SettingsDialogTextButton(onClick = access::dismissDenied) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
        null -> Unit
    }
}
