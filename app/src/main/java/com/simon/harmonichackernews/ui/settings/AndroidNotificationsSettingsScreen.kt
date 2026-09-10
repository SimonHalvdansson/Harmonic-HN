package com.simon.harmonichackernews.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.network.AndroidReplyNotificationPlatform
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun AndroidNotificationsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = context.getSystemService(NotificationManager::class.java)

    fun deliveryAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(AndroidReplyNotificationPlatform.CHANNEL_ID)?.importance !=
        NotificationManager.IMPORTANCE_NONE

    var allowed by remember { mutableStateOf(deliveryAllowed()) }
    var refreshVersion by remember { mutableIntStateOf(0) }
    var pendingPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = deliveryAllowed()
        val callback = pendingPermission
        pendingPermission = null
        callback?.invoke(it && allowed)
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = deliveryAllowed()
                refreshVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    NotificationsSettingsRoute(
        accounts = app.platform.accounts,
        notifications = app.replyNotifications,
        showNavigation = showNavigation,
        onBack = onBack,
        deliveryAllowed = allowed,
        refreshVersion = refreshVersion,
        onRequestPermission = { onResult ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                pendingPermission = onResult
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                allowed = deliveryAllowed()
                onResult(allowed)
            }
        },
        onOpenSystemSettings = {
            val channelExists = manager.getNotificationChannel(AndroidReplyNotificationPlatform.CHANNEL_ID) != null
            val intent = if (NotificationManagerCompat.from(context).areNotificationsEnabled() && channelExists) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidReplyNotificationPlatform.CHANNEL_ID)
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            }
            context.startActivity(intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        },
    )
}
