package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.MaterialTheme
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserTagColor
import com.simon.harmonichackernews.ui.content.userTagColor

import com.simon.harmonichackernews.resources.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.simon.harmonichackernews.ui.common.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.content.UserAvatar
import com.simon.harmonichackernews.ui.theme.AppUiFontFamily
import com.simon.harmonichackernews.ui.common.TextButton
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
fun UserSettingsDialog(
    requestedUserName: String,
    state: UserDialogUiState,
    tag: String,
    blocked: Boolean,
    ownProfile: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenSubmissions: (String) -> Unit,
    onEditTag: () -> Unit,
    onToggleBlocked: (String) -> Unit,
    onReport: (String) -> Unit,
    onOpenLink: (String) -> Unit = {},
    identityResolved: Boolean = true,
    userAvatarsEnabled: Boolean = false,
    userAvatarOptions: UserAvatarOptions = UserAvatarOptions(),
    nestedDialog: (@Composable () -> Unit)? = null,
    tagColor: UserTagColor = UserTagColor.Theme,
) {
    val userName = (state as? UserDialogUiState.Loaded)?.user?.id ?: requestedUserName
    // The host's saveable registry survives native dialog window recreation.
    val listState = rememberLazyListState()
    SettingsAlertDialog(
        onDismissRequest = onDismiss,
        edgeToEdgeContent = true,
        scrollableContent = true,
        text = {
            UserProfileDialogContent(
                userName = userName, state = state, tag = tag, tagColor = tagColor,
                blocked = blocked, ownProfile = ownProfile, identityResolved = identityResolved,
                userAvatarsEnabled = userAvatarsEnabled, userAvatarOptions = userAvatarOptions,
                onRetry = onRetry, onOpenSubmissions = onOpenSubmissions, onEditTag = onEditTag,
                onToggleBlocked = onToggleBlocked, onReport = onReport, onOpenLink = onOpenLink,
                listState = listState,
            )
        },
        confirmButton = {},
        showButtons = false,
        foreground = { nestedDialog?.invoke() },
    )
}

/** Shared dialog body also measured directly by constrained-window rendering tests. */
@Composable
internal fun UserProfileDialogContent(
    userName: String,
    state: UserDialogUiState,
    tag: String,
    tagColor: UserTagColor = UserTagColor.Theme,
    blocked: Boolean = false,
    ownProfile: Boolean = false,
    identityResolved: Boolean = true,
    userAvatarsEnabled: Boolean = false,
    userAvatarOptions: UserAvatarOptions = UserAvatarOptions(),
    onRetry: () -> Unit = {},
    onOpenSubmissions: (String) -> Unit = {},
    onEditTag: () -> Unit = {},
    onToggleBlocked: (String) -> Unit = {},
    onReport: (String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("user-profile-content")
            .heightIn(max = 680.dp),
        state = listState,
        contentPadding = PaddingValues(
            HarmonicDimens.compose_settings_dialog_content_padding,
        ),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (userAvatarsEnabled) {
                    UserAvatar(
                        author = userName,
                        options = userAvatarOptions,
                        modifier = Modifier.padding(end = 8.dp).size(28.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.ic_account_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 8.dp).size(28.dp),
                    )
                }
                Text(
                    text = userName,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AppUiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                )
            }
        }
        item {
            AnimatedContent(
                targetState = state,
                transitionSpec = { (fadeIn(tween(120)) togetherWith fadeOut(tween(90))).using(null) },
                label = "user content",
            ) { current ->
                when (current) {
                    UserDialogUiState.Loading -> UserLoadingPlaceholder()
                    UserDialogUiState.Error -> UserLoadError(onRetry)
                    is UserDialogUiState.Loaded -> UserLoadedContent(
                        user = current.user,
                        tag = tag,
                        tagColor = tagColor,
                        blocked = blocked,
                        ownProfile = ownProfile,
                        identityResolved = identityResolved,
                        onOpenSubmissions = onOpenSubmissions,
                        onEditTag = onEditTag,
                        onToggleBlocked = onToggleBlocked,
                        onReport = onReport,
                        onOpenLink = onOpenLink,
                    )
                }
            }
        }
    }
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
                    .graphicsLayer { alpha = shimmerAlpha }
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun UserLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Loading failed",
            modifier = Modifier.padding(bottom = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = AppUiFontFamily,
            fontSize = 16.sp,
        )
        Text(
            text = "Check the usernameâ€™s capitalization. HN profile names are case-sensitive.",
            modifier = Modifier.padding(bottom = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = AppUiFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Retry",
                modifier = Modifier.padding(start = 6.dp),
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun UserLoadedContent(
    user: UserInfoUi,
    tag: String,
    tagColor: UserTagColor,
    blocked: Boolean,
    ownProfile: Boolean,
    identityResolved: Boolean,
    onOpenSubmissions: (String) -> Unit,
    onEditTag: () -> Unit,
    onToggleBlocked: (String) -> Unit,
    onReport: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var confirmingBlock by rememberSaveable(user.id, blocked) { mutableStateOf(false) }
    AnimatedContent(
        targetState = confirmingBlock,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 80)) togetherWith fadeOut(tween(100)))
                .using(null)
        },
        label = "block user confirmation",
    ) { confirmBlock ->
        if (confirmBlock) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Block ${user.id}?",
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = AppUiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    "Their stories and comments will be hidden. You can unblock them from this profile or Settings â†’ Filters and tags.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppUiFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { confirmingBlock = false }) { Text("Cancel") }
                    TextButton(onClick = { onToggleBlocked(user.id) }) {
                        Text("Block user", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = user.meta,
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            UserProfileBio(user.id, user.about, onOpenLink)
            if (user.hasSubmissions) {
                UserOutlinedAction(
                    label = "Submissions",
                    icon = Res.drawable.ic_forum,
                    onClick = { onOpenSubmissions(user.id) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (identityResolved && !ownProfile) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    val stacked = maxWidth < 240.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val actions: @Composable (Modifier) -> Unit = { actionModifier ->
                        UserIconAction(
                            label = "Set tag" + if (tag.isBlank()) "" else " ($tag)",
                            icon = Res.drawable.ic_sell,
                            onClick = onEditTag,
                            modifier = actionModifier,
                            tint = if (tag.isBlank()) MaterialTheme.colorScheme.onSurface else userTagColor(tagColor),
                            horizontal = stacked,
                        )
                        UserIconAction(
                            label = if (blocked) "Unblock" else "Block",
                            icon = Res.drawable.ic_block,
                            onClick = {
                                if (blocked) onToggleBlocked(user.id) else confirmingBlock = true
                            },
                            modifier = actionModifier,
                            horizontal = stacked,
                        )
                        UserIconAction(
                            label = "Report",
                            icon = Res.drawable.ic_flag,
                            onClick = { onReport(user.id) },
                            modifier = actionModifier,
                            horizontal = stacked,
                        )
                    }
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            actions(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            actions(Modifier.weight(1f))
                        }
                    }
                }
            }
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
            .heightIn(min = 56.dp),
        enabled = enabled,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null)
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = AppUiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun UserIconAction(
    label: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    horizontal: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (horizontal) 56.dp else 72.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        val text: @Composable (Modifier) -> Unit = { textModifier ->
            Text(
                text = label,
                modifier = textModifier,
                color = tint,
                fontFamily = AppUiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = if (horizontal) TextAlign.Start else TextAlign.Center,
                maxLines = if (horizontal) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (horizontal) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = painterResource(icon), contentDescription = null, tint = tint)
                text(Modifier.padding(start = 12.dp).weight(1f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(painter = painterResource(icon), contentDescription = null, tint = tint)
                text(Modifier.padding(top = 4.dp))
            }
        }
    }
}
