package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.presentation.UserProfileLoadState
import com.simon.harmonichackernews.presentation.UserProfileSessionEffect
import com.simon.harmonichackernews.ui.common.FailureDetailDialog
import com.simon.harmonichackernews.ui.common.SharedLoginDialog
import com.simon.harmonichackernews.ui.common.UserMessageSnackbarHost
import com.simon.harmonichackernews.ui.settings.MessageActionDialog
import com.simon.harmonichackernews.ui.settings.SettingsChangelogDialog
import com.simon.harmonichackernews.ui.settings.SharedUserSettingsDialog
import com.simon.harmonichackernews.ui.settings.SharedUserTagRoute
import com.simon.harmonichackernews.ui.settings.UserDialogUiState
import com.simon.harmonichackernews.ui.settings.UserInfoUi
import com.simon.harmonichackernews.ui.stories.CacheStoriesDialog
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
internal fun BoxScope.DesktopAppForeground(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    navigation: MainNavigationSnapshot,
    storiesController: com.simon.harmonichackernews.ui.stories.StoriesComposeController?,
) {
    if (navigation.welcomeDialogVisible) {
        DesktopWelcomeDialog(app, scene.navigation::dismissWelcomeDialog)
    }
    if (navigation.changelogDialogVisible) {
        SettingsChangelogDialog(
            onDismiss = scene.navigation::dismissChangelogDialog,
            onOpenGithub = { scene.links.open(app.metadata.projectUrl) },
        )
    }
    if (navigation.cacheStoriesDialogVisible) {
        CacheStoriesDialog(
            initialStoryCount = app.userSettings.cache.storiesToCache,
            onDismiss = scene.navigation::dismissCacheStoriesDialog,
            onConfirm = { count ->
                scene.navigation.dismissCacheStoriesDialog()
                storiesController?.cacheStories(count)
                    ?: scene.userMessages.show("Return to the stories screen to cache posts")
            },
        )
    }
    if (navigation.loginDialogVisible) {
        SharedLoginDialog(
            onDismiss = scene.navigation::dismissLoginDialog,
            workflow = app.login,
            onLoginSucceeded = {
                scene.navigation.dismissLoginDialog()
                scene.userMessages.show("Logged in to Hacker News")
            },
            onCreateAccount = {
                scene.links.open("https://news.ycombinator.com/login?goto=news")
            },
            captchaDialog = { _, cancel, _ ->
                MessageActionDialog(
                    title = "Hacker News CAPTCHA required",
                    message = "The desktop host does not yet bundle an embedded browser engine " +
                        "for Hacker News CAPTCHA challenges.",
                    positiveLabel = "Close",
                    onPositive = cancel,
                    onDismiss = cancel,
                )
            },
        )
    }
    navigation.captchaRequest?.let {
        MessageActionDialog(
            title = "Hacker News CAPTCHA required",
            message = "Complete this browser-only challenge on the Hacker News website.",
            positiveLabel = "Close",
            onPositive = { scene.navigation.dismissCaptchaDialog() },
            onDismiss = { scene.navigation.dismissCaptchaDialog() },
        )
    }
    navigation.userRequest?.let { request ->
        DesktopUserProfileDialog(
            app = app,
            scene = scene,
            userName = request.userName,
            onDismiss = { scene.navigation.dismissUserDialog() },
            onTagChanged = {},
        )
    }
    navigation.failureRequest?.let { request ->
        FailureDetailDialog(
            title = request.title,
            message = request.message,
            showCopyComment = request.clipboardText != null,
            onCopyComment = {
                request.clipboardText?.let {
                    app.platform.clipboard.copy("Hacker News comment", it)
                    scene.userMessages.show("Comment copied to clipboard")
                }
                scene.navigation.dismissFailureDetailDialog()
            },
            onDismiss = { scene.navigation.dismissFailureDetailDialog() },
        )
    }
    UserMessageSnackbarHost(
        messages = scene.userMessages,
        modifier = Modifier.align(Alignment.BottomCenter).zIndex(100f),
    )
}

@Composable
internal fun DesktopUserProfileDialog(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    userName: String,
    onDismiss: () -> Unit,
    onTagChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
    val session = remember(app, scope, userName) {
        app.createUserProfileSession(scope, userName, monthNames)
    }
    val runtimeState by session.runtime.state.collectAsState()
    var tagDialogOpen by rememberSaveable(userName) { mutableStateOf(false) }
    var currentTag by remember(userName) { mutableStateOf(app.userTags.tagFor(userName)) }

    LaunchedEffect(session) {
        session.start()
        session.effects.collect { effect ->
            when (effect) {
                is UserProfileSessionEffect.OpenSubmissions -> {
                    onDismiss()
                    scene.navigation.openSubmissions(effect.username)
                }
                is UserProfileSessionEffect.RequestNotificationPermission -> {
                    session.notificationPermissionResult(granted = false)
                    scene.userMessages.show("Reply notifications are currently Android-only")
                }
                is UserProfileSessionEffect.ComposeReportEmail -> {
                    val subject = URLEncoder.encode(
                        "Reporting user ${effect.username}",
                        StandardCharsets.UTF_8,
                    )
                    scene.links.open("mailto:hn@ycombinator.com?subject=$subject")
                }
                is UserProfileSessionEffect.Message -> scene.userMessages.show(effect.text)
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
    SharedUserSettingsDialog(
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
            scene.userMessages.show("Reply notifications are currently Android-only")
        },
        onReport = session::report,
    )

    if (tagDialogOpen) {
        SharedUserTagRoute(
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
