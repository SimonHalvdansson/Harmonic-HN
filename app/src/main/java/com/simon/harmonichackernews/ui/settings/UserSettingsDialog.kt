@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import org.jetbrains.compose.resources.DrawableResource


import com.simon.harmonichackernews.resources.*

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.RepliesChecker
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.Utils
import com.simon.harmonichackernews.utils.GroupedNumberFormatter
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

private data class ComposeUserInfo(
    val id: String,
    val meta: String,
    val about: String,
    val hasSubmissions: Boolean,
)

private sealed interface ComposeUserState {
    data object Loading : ComposeUserState
    data class Loaded(val user: ComposeUserInfo) : ComposeUserState
    data object Error : ComposeUserState
}

@Composable
fun UserSettingsDialog(
    userName: String,
    onDismiss: () -> Unit,
    onTagChanged: () -> Unit,
) {
    val context = LocalContext.current
    val contentFilters = remember(context) {
        ContentFilterRepository(AndroidKeyValueStore.defaults(context))
    }
    val monthNames = stringArrayResource(Res.array.months)
    var state by remember(userName) {
        mutableStateOf<ComposeUserState>(ComposeUserState.Loading)
    }
    var reload by remember(userName) { mutableIntStateOf(0) }
    var tagDialogOpen by rememberSaveable(userName) { mutableStateOf(false) }
    var currentTag by remember(userName) {
        mutableStateOf(Utils.getUserTag(context, userName))
    }
    var isBlocked by remember(userName) {
        mutableStateOf(contentFilters.containsUser(userName))
    }
    DisposableEffect(userName, reload, monthNames) {
        state = ComposeUserState.Loading
        val job = NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.hackerNewsApi.getUser(userName)
                    ?: error("Hacker News user not found")
            },
            onSuccess = { user ->
                state = runCatching {
                    user.toComposeUser(monthNames)
                }.fold(
                    onSuccess = ComposeUserState::Loaded,
                    onFailure = { ComposeUserState.Error },
                )
            },
            onFailure = { state = ComposeUserState.Error },
        )
        onDispose { job.cancel() }
    }

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
                        text = (state as? ComposeUserState.Loaded)?.user?.id ?: userName,
                        color = HarmonicTheme.colors.storyNormal,
                        fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                    )
                }
                item {
                    AnimatedContent(
                        targetState = state,
                        label = "user content",
                    ) { current ->
                        when (current) {
                            ComposeUserState.Loading -> UserLoadingPlaceholder()
                            ComposeUserState.Error -> UserLoadError(
                                onRetry = { reload++ },
                            )
                            is ComposeUserState.Loaded -> UserLoadedContent(
                                user = current.user,
                                tag = currentTag,
                                blocked = isBlocked,
                                onDismiss = onDismiss,
                                onEditTag = { tagDialogOpen = true },
                                onBlockedChanged = { isBlocked = it },
                                onBlock = contentFilters::addUser,
                                onUnblock = contentFilters::removeUser,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        showButtons = false,
    )

    if (tagDialogOpen) {
        UserTagDialog(
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
                    .background(
                        HarmonicTheme.colors.storyDisabled,
                        RoundedCornerShape(8.dp),
                    ),
            )
        }
    }
}

@Composable
private fun UserLoadError(
    onRetry: () -> Unit,
) {
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
        OutlinedButton(
            onClick = onRetry,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.height(56.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_refresh),
                contentDescription = null,
            )
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
    user: ComposeUserInfo,
    tag: String,
    blocked: Boolean,
    onDismiss: () -> Unit,
    onEditTag: () -> Unit,
    onBlockedChanged: (Boolean) -> Unit,
    onBlock: (String) -> Boolean,
    onUnblock: (String) -> Boolean,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ownProfile = user.id.equals(
        AccountUtils.getAccountUsername(context),
        ignoreCase = true,
    )
    var notificationLoading by remember(user.id) { mutableStateOf(false) }
    var notificationStatus by remember(user.id) { mutableStateOf("") }
    var notificationsActive by remember(user.id) {
        mutableStateOf(
            user.id.equals(
                RepliesChecker.getConfiguredUsername(context),
                ignoreCase = true,
            ),
        )
    }
    var permissionActionPending by remember(user.id) { mutableStateOf(false) }

    fun activateNotifications() {
        notificationLoading = true
        notificationStatus = ""
        coroutineScope.launch {
            val success = RepliesChecker.enable(context, user.id)
            notificationLoading = false
            notificationsActive = success &&
                user.id.equals(
                    RepliesChecker.getConfiguredUsername(context),
                    ignoreCase = true,
                )
            notificationStatus = if (success) {
                ""
            } else {
                "Could not activate reply notifications."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && permissionActionPending) {
            activateNotifications()
        } else if (!granted) {
            notificationStatus = "Notification permission denied."
        }
        permissionActionPending = false
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
        if (user.about.isNotBlank()) {
            Text(
                text = user.about,
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
                onClick = {
                    onDismiss()
                    context.startActivity(
                        SubmissionsContract.createIntent(context, user.id),
                    )
                },
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
                onClick = {
                    if (notificationsActive) {
                        RepliesChecker.disable(context)
                        notificationsActive = false
                        notificationStatus = ""
                    } else if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionActionPending = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        activateNotifications()
                    }
                },
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
                onClick = {
                    if (blocked) {
                        if (onUnblock(user.id)) {
                            Toast.makeText(
                                context,
                                "Unblocked ${user.id}",
                                Toast.LENGTH_SHORT,
                            ).show()
                            onBlockedChanged(false)
                        }
                    } else if (onBlock(user.id)) {
                        Toast.makeText(
                            context,
                            "You will no longer see posts or comments from ${user.id}",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    }
                },
            )
            UserTextAction(
                label = "Report (email HN)",
                icon = Res.drawable.ic_flag,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_SENDTO,
                        Uri.parse(
                            "mailto:hn@ycombinator.com?subject=" +
                                Uri.encode("Reporting user ${user.id}"),
                        ),
                    )
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(Intent.createChooser(intent, "Send report via"))
                    }
                },
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
        shapes = ButtonDefaults.shapes(),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        enabled = enabled,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
        )
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
    SettingsDialogTextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = HarmonicTheme.colors.storyNormal,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun HackerNewsUserDto.toComposeUser(months: List<String>): ComposeUserInfo {
    val calendar = Calendar.getInstance().apply {
        time = Date(created * 1_000L)
    }
    val month = months[calendar[Calendar.MONTH]]
    val formattedKarma = GroupedNumberFormatter.format(karma)
    val formattedAbout = if (about != null) {
        @Suppress("DEPRECATION")
        Html.fromHtml(about.orEmpty()).toString().trim()
    } else {
        ""
    }
    return ComposeUserInfo(
        id = id,
        meta = "$formattedKarma karma since $month ${calendar[Calendar.DAY_OF_MONTH]}, " +
            calendar[Calendar.YEAR],
        about = formattedAbout,
        hasSubmissions = submitted.isNotEmpty(),
    )
}
