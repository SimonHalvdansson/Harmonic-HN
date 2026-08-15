package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

data class UserInfoUi(
    val id: String,
    val meta: String,
    val about: String,
    val hasSubmissions: Boolean,
)

sealed interface UserDialogUiState {
    data object Loading : UserDialogUiState
    data class Loaded(val user: UserInfoUi) : UserDialogUiState
    data object Error : UserDialogUiState
}

@Composable
fun SharedUserSettingsDialog(
    requestedUserName: String,
    state: UserDialogUiState,
    tag: String,
    blocked: Boolean,
    ownProfile: Boolean,
    notificationsActive: Boolean,
    notificationLoading: Boolean,
    notificationStatus: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubmissions: (String) -> Unit,
    onEditTag: () -> Unit,
    onToggleBlocked: (String) -> Unit,
    onToggleNotifications: (String) -> Unit,
    onReport: (String) -> Unit,
    onOpenLink: (String) -> Unit = {},
) {
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .padding(8.dp),
            ) {
                item {
                    Text(
                        text = (state as? UserDialogUiState.Loaded)?.user?.id ?: requestedUserName,
                        color = HarmonicTheme.colors.storyNormal,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                }
                item {
                    AnimatedContent(targetState = state, label = "user content") { current ->
                        when (current) {
                            UserDialogUiState.Loading -> UserLoadingPlaceholder()
                            UserDialogUiState.Error -> UserLoadError(onRetry)
                            is UserDialogUiState.Loaded -> UserLoadedContent(
                                user = current.user,
                                tag = tag,
                                blocked = blocked,
                                ownProfile = ownProfile,
                                notificationsActive = notificationsActive,
                                notificationLoading = notificationLoading,
                                notificationStatus = notificationStatus,
                                onOpenSubmissions = onOpenSubmissions,
                                onEditTag = onEditTag,
                                onToggleBlocked = onToggleBlocked,
                                onToggleNotifications = onToggleNotifications,
                                onReport = onReport,
                                onOpenLink = onOpenLink,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )
}

@Composable
private fun UserLoadingPlaceholder() {
    val transition = rememberInfiniteTransition(label = "user loading")
    val shimmerAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading alpha",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        listOf(196.dp, 280.dp, 220.dp).forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .padding(top = if (index == 0) 0.dp else 5.dp)
                    .width(width)
                    .height(16.dp)
                    .alpha(shimmerAlpha)
                    .background(HarmonicTheme.colors.storyDisabled, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun UserLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Loading failed",
            modifier = Modifier.padding(vertical = 24.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.height(56.dp)) {
            Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = null)
            Text(
                text = "Retry",
                modifier = Modifier.padding(start = 8.dp),
                fontFamily = ProductSansFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun UserLoadedContent(
    user: UserInfoUi,
    tag: String,
    blocked: Boolean,
    ownProfile: Boolean,
    notificationsActive: Boolean,
    notificationLoading: Boolean,
    notificationStatus: String,
    onOpenSubmissions: (String) -> Unit,
    onEditTag: () -> Unit,
    onToggleBlocked: (String) -> Unit,
    onToggleNotifications: (String) -> Unit,
    onReport: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val linkColor = HarmonicTheme.colors.link
    val linkListener = remember(onOpenLink) {
        LinkInteractionListener { annotation ->
            if (annotation is LinkAnnotation.Url) onOpenLink(annotation.url)
        }
    }
    val formattedAbout = remember(
        user.about,
        linkColor,
        linkListener,
    ) {
        htmlAnnotatedString(user.about, linkColor, linkListener)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = user.meta,
            modifier = Modifier.padding(bottom = 4.dp),
            color = HarmonicTheme.colors.storyDisabled,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
        if (formattedAbout.isNotBlank()) {
            Text(
                text = formattedAbout,
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        if (user.hasSubmissions) {
            UserOutlinedAction(
                label = "Submissions",
                icon = Res.drawable.ic_forum,
                onClick = { onOpenSubmissions(user.id) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (ownProfile) {
            UserOutlinedAction(
                label = if (notificationsActive) {
                    "Deactivate notifications"
                } else {
                    "Activate notifications"
                },
                icon = Res.drawable.ic_notifications,
                enabled = !notificationLoading,
                onClick = { onToggleNotifications(user.id) },
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Get notifications when someone replies to your comments or " +
                    "comments on your stories.",
                modifier = Modifier.padding(top = 6.dp),
                color = HarmonicTheme.colors.storyDisabled,
                fontFamily = ProductSansFontFamily,
                fontSize = 14.sp,
            )
            if (notificationLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            if (notificationStatus.isNotEmpty()) {
                Text(
                    text = notificationStatus,
                    modifier = Modifier.padding(top = 6.dp),
                    color = HarmonicTheme.colors.storyDisabled,
                    fontFamily = ProductSansFontFamily,
                    fontSize = 13.sp,
                )
            }
        } else {
            UserTextAction(
                label = "Set tag" + if (tag.isBlank()) "" else " ($tag)",
                icon = Res.drawable.ic_sell,
                onClick = onEditTag,
                modifier = Modifier.padding(top = 8.dp),
            )
            UserTextAction(
                label = if (blocked) "Unblock" else "Block",
                icon = Res.drawable.ic_block,
                onClick = { onToggleBlocked(user.id) },
            )
            UserTextAction(
                label = "Report (email HN)",
                icon = Res.drawable.ic_flag,
                onClick = { onReport(user.id) },
            )
        }
    }
}

@Composable
private fun UserOutlinedAction(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun UserTextAction(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsDialogTextButton(onClick = onClick, modifier = modifier) {
        Icon(painter = painterResource(icon), contentDescription = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}
