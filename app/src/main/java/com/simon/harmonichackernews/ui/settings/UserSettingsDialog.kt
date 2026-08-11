package com.simon.harmonichackernews.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.RepliesChecker
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.months
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.ui.submissions.SubmissionsContract
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.GroupedNumberFormatter
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource

@Composable
fun UserSettingsDialog(
    userName: String,
    onDismiss: () -> Unit,
    onTagChanged: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val contentFilters = remember(context) {
        ContentFilterRepository(AndroidKeyValueStore.defaults(context))
    }
    val userTags = remember(context) {
        UserTagsRepository(AndroidKeyValueStore.defaults(context))
    }
    val monthNames = stringArrayResource(Res.array.months)
    var state by remember(userName) {
        mutableStateOf<UserDialogUiState>(UserDialogUiState.Loading)
    }
    var reload by remember(userName) { mutableIntStateOf(0) }
    var tagDialogOpen by rememberSaveable(userName) { mutableStateOf(false) }
    var currentTag by remember(userName) { mutableStateOf(userTags.tagFor(userName)) }
    var isBlocked by remember(userName) {
        mutableStateOf(contentFilters.containsUser(userName))
    }
    var notificationLoading by remember(userName) { mutableStateOf(false) }
    var notificationStatus by remember(userName) { mutableStateOf("") }
    var notificationsActive by remember(userName) {
        mutableStateOf(
            userName.equals(
                RepliesChecker.getConfiguredUsername(context),
                ignoreCase = true,
            ),
        )
    }
    var permissionActionPending by remember(userName) { mutableStateOf(false) }

    DisposableEffect(userName, reload, monthNames) {
        state = UserDialogUiState.Loading
        val job = NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.hackerNewsApi.getUser(userName)
                    ?: error("Hacker News user not found")
            },
            onSuccess = { user ->
                state = runCatching { user.toUserInfoUi(monthNames) }.fold(
                    onSuccess = UserDialogUiState::Loaded,
                    onFailure = { UserDialogUiState.Error },
                )
            },
            onFailure = { state = UserDialogUiState.Error },
        )
        onDispose { job.cancel() }
    }

    fun activateNotifications(targetUser: String) {
        notificationLoading = true
        notificationStatus = ""
        coroutineScope.launch {
            val success = RepliesChecker.enable(context, targetUser)
            notificationLoading = false
            notificationsActive = success && targetUser.equals(
                RepliesChecker.getConfiguredUsername(context),
                ignoreCase = true,
            )
            notificationStatus = if (success) "" else "Could not activate reply notifications."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && permissionActionPending) {
            activateNotifications(userName)
        } else if (!granted) {
            notificationStatus = "Notification permission denied."
        }
        permissionActionPending = false
    }

    val loadedUser = (state as? UserDialogUiState.Loaded)?.user
    val ownProfile = loadedUser?.id?.equals(
        AccountUtils.getAccountUsername(context),
        ignoreCase = true,
    ) == true
    SharedUserSettingsDialog(
        requestedUserName = userName,
        state = state,
        tag = currentTag,
        blocked = isBlocked,
        ownProfile = ownProfile,
        notificationsActive = notificationsActive,
        notificationLoading = notificationLoading,
        notificationStatus = notificationStatus,
        onDismiss = onDismiss,
        onRetry = { reload++ },
        onOpenSubmissions = { targetUser ->
            onDismiss()
            context.startActivity(SubmissionsContract.createIntent(context, targetUser))
        },
        onEditTag = { tagDialogOpen = true },
        onToggleBlocked = { targetUser ->
            if (isBlocked) {
                if (contentFilters.removeUser(targetUser)) {
                    Toast.makeText(context, "Unblocked $targetUser", Toast.LENGTH_SHORT).show()
                    isBlocked = false
                }
            } else if (contentFilters.addUser(targetUser)) {
                Toast.makeText(
                    context,
                    "You will no longer see posts or comments from $targetUser",
                    Toast.LENGTH_SHORT,
                ).show()
                onDismiss()
            }
        },
        onToggleNotifications = { targetUser ->
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
                activateNotifications(targetUser)
            }
        },
        onReport = { targetUser ->
            val intent = Intent(
                Intent.ACTION_SENDTO,
                Uri.parse(
                    "mailto:hn@ycombinator.com?subject=" +
                        Uri.encode("Reporting user $targetUser"),
                ),
            )
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Send report via"))
            }
        },
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

private fun HackerNewsUserDto.toUserInfoUi(months: List<String>): UserInfoUi {
    val calendar = Calendar.getInstance().apply { time = Date(created * 1_000L) }
    val month = months[calendar[Calendar.MONTH]]
    val formattedKarma = GroupedNumberFormatter.format(karma)
    val formattedAbout = if (about != null) {
        @Suppress("DEPRECATION")
        Html.fromHtml(about.orEmpty()).toString().trim()
    } else {
        ""
    }
    return UserInfoUi(
        id = id,
        meta = "$formattedKarma karma since $month ${calendar[Calendar.DAY_OF_MONTH]}, " +
            calendar[Calendar.YEAR],
        about = formattedAbout,
        hasSubmissions = submitted.isNotEmpty(),
    )
}
