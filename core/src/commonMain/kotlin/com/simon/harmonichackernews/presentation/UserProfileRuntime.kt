package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.platform.HackerNewsAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface UserProfileLoader {
    suspend fun load(username: String): HackerNewsUserDto?
}

interface UserProfileBlockPort {
    fun isBlocked(username: String): Boolean
    fun setBlocked(username: String, blocked: Boolean): Boolean
}

interface UserProfileNotificationPort {
    fun configuredUsername(): String?
    suspend fun enable(username: String): Boolean
    fun disable()
}

sealed interface UserProfileLoadState {
    data object Loading : UserProfileLoadState
    data class Loaded(val profile: UserProfilePresentation) : UserProfileLoadState
    data object Error : UserProfileLoadState
}

enum class UserProfileNotificationOutcome {
    IDLE,
    ENABLED,
    DISABLED,
    ENABLE_FAILED,
    PERMISSION_DENIED,
}

data class UserProfileRuntimeState(
    val loadState: UserProfileLoadState = UserProfileLoadState.Loading,
    val blocked: Boolean = false,
    val ownProfile: Boolean = false,
    val notificationsActive: Boolean = false,
    val notificationLoading: Boolean = false,
    val notificationStatus: String = "",
    val notificationOutcome: UserProfileNotificationOutcome = UserProfileNotificationOutcome.IDLE,
    val blockOutcome: UserProfileBlockOutcome? = null,
)

data class UserProfileBlockOutcome(
    val blocked: Boolean,
    val message: String,
    val dismissProfile: Boolean,
)

/** Portable profile workflow; platform hosts retain permission, worker and intent side effects. */
class UserProfileRuntime(
    username: String,
    private val monthNames: List<String>,
    private val loader: UserProfileLoader,
    private val accounts: HackerNewsAccountRepository,
    private val blocks: UserProfileBlockPort,
    private val notifications: UserProfileNotificationPort,
) {
    private val username = username.trim()
    private val mutableState = MutableStateFlow(
        UserProfileRuntimeState(
            blocked = blocks.isBlocked(this.username),
            notificationsActive = matches(notifications.configuredUsername(), this.username),
        ),
    )
    val state: StateFlow<UserProfileRuntimeState> = mutableState.asStateFlow()

    suspend fun load() {
        mutableState.value = mutableState.value.copy(loadState = UserProfileLoadState.Loading)
        mutableState.value = try {
            val user = loader.load(username) ?: error("Hacker News user not found")
            val profile = UserProfilePresenter.present(user, monthNames)
            mutableState.value.copy(
                loadState = UserProfileLoadState.Loaded(profile),
                ownProfile = matches(profile.id, accounts.load()?.username),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            mutableState.value.copy(loadState = UserProfileLoadState.Error, ownProfile = false)
        }
    }

    suspend fun retry() = load()

    fun toggleBlocked(): UserProfileBlockOutcome? {
        val nextBlocked = !mutableState.value.blocked
        if (!blocks.setBlocked(username, nextBlocked)) return null
        val outcome = UserProfileBlockOutcome(
            blocked = nextBlocked,
            message = if (nextBlocked) {
                "You will no longer see posts or comments from $username"
            } else {
                "Unblocked $username"
            },
            dismissProfile = nextBlocked,
        )
        mutableState.value = mutableState.value.copy(blocked = nextBlocked, blockOutcome = outcome)
        return outcome
    }

    suspend fun enableNotifications() {
        mutableState.value = mutableState.value.copy(
            notificationLoading = true,
            notificationStatus = "",
            notificationOutcome = UserProfileNotificationOutcome.IDLE,
        )
        val enabled = try {
            notifications.enable(username)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            false
        }
        val active = enabled && matches(notifications.configuredUsername(), username)
        mutableState.value = mutableState.value.copy(
            notificationsActive = active,
            notificationLoading = false,
            notificationStatus = if (active) "" else "Could not activate reply notifications.",
            notificationOutcome = if (active) {
                UserProfileNotificationOutcome.ENABLED
            } else {
                UserProfileNotificationOutcome.ENABLE_FAILED
            },
        )
    }

    fun disableNotifications() {
        notifications.disable()
        mutableState.value = mutableState.value.copy(
            notificationsActive = false,
            notificationLoading = false,
            notificationStatus = "",
            notificationOutcome = UserProfileNotificationOutcome.DISABLED,
        )
    }

    fun notificationPermissionDenied() {
        mutableState.value = mutableState.value.copy(
            notificationLoading = false,
            notificationStatus = "Notification permission denied.",
            notificationOutcome = UserProfileNotificationOutcome.PERMISSION_DENIED,
        )
    }

    private fun matches(first: String?, second: String?): Boolean =
        !first.isNullOrBlank() && first.equals(second, ignoreCase = true)
}
