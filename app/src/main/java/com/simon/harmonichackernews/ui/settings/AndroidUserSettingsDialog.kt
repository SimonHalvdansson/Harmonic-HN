package com.simon.harmonichackernews.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.presentation.UserProfileLoadState
import com.simon.harmonichackernews.presentation.UserProfileSessionEffect
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.months
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun AndroidUserSettingsDialog(
    userName: String,
    onDismiss: () -> Unit,
    onTagChanged: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appComposition = LocalHarmonicUiDependencies.current
    val userTags = appComposition.userTags
    val monthNames = stringArrayResource(Res.array.months)
    val session = remember(userName, monthNames, coroutineScope) {
        appComposition.createUserProfileSession(coroutineScope, userName, monthNames)
    }
    val runtime = session.runtime
    val runtimeState by runtime.state.collectAsState()
    var tagDialogOpen by rememberSaveable(userName) { mutableStateOf(false) }
    var currentTag by remember(userName) { mutableStateOf(userTags.tagFor(userName)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        session.notificationPermissionResult(granted)
    }

    LaunchedEffect(session) {
        session.start()
        session.effects.collect { effect ->
            when (effect) {
                is UserProfileSessionEffect.OpenSubmissions -> {
                    onDismiss()
                    appComposition.navigation.openSubmissions(effect.username)
                }
                is UserProfileSessionEffect.RequestNotificationPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        session.notificationPermissionResult(granted = true)
                    }
                }
                is UserProfileSessionEffect.ComposeReportEmail -> {
                    val intent = Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse(
                            "mailto:hn@ycombinator.com?subject=" +
                                Uri.encode("Reporting user ${effect.username}"),
                        ),
                    )
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(Intent.createChooser(intent, "Send report via"))
                    }
                }
                is UserProfileSessionEffect.Message ->
                    appComposition.userMessages.show(effect.text)
                UserProfileSessionEffect.Dismiss -> onDismiss()
            }
        }
    }
    DisposableEffect(session) { onDispose(session::dispose) }

    val state = when (val loadState = runtimeState.loadState) {
        UserProfileLoadState.Loading -> UserDialogUiState.Loading
        UserProfileLoadState.Error -> UserDialogUiState.Error
        is UserProfileLoadState.Loaded -> UserDialogUiState.Loaded(
            UserInfoUi(
                id = loadState.profile.id,
                meta = loadState.profile.meta,
                about = loadState.profile.about,
                hasSubmissions = loadState.profile.hasSubmissions,
            ),
        )
    }
    UserSettingsDialog(
        requestedUserName = userName,
        state = state,
        tag = currentTag,
        blocked = runtimeState.blocked,
        ownProfile = runtimeState.ownProfile,
        notificationsActive = runtimeState.notificationsActive,
        notificationLoading = runtimeState.notificationLoading,
        notificationStatus = runtimeState.notificationStatus,
        onDismiss = onDismiss,
        onRetry = session::retry,
        onOpenSubmissions = session::openSubmissions,
        onEditTag = { tagDialogOpen = true },
        onToggleBlocked = { session.toggleBlocked() },
        onToggleNotifications = {
            val permissionGranted = !(
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            )
            session.toggleNotifications(permissionGranted)
        },
        onReport = session::report,
        onOpenLink = { appComposition.links.open(it) },
    )

    if (tagDialogOpen) {
        UserTagRoute(
            userName = userName,
            currentTag = currentTag,
            onDismiss = { tagDialogOpen = false },
            onSaved = { saved ->
                currentTag = saved
                tagDialogOpen = false
                onTagChanged()
            },
        )
    }
}
