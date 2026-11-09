package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.ReplyCheckResult
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.network.ReplySubscriptionResult
import com.simon.harmonichackernews.network.AccountActivitySnapshot
import com.simon.harmonichackernews.network.AccountAlertPreferences
import com.simon.harmonichackernews.settings.ReplyNotificationFrequency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NotificationSettingsOperation { Enabling, Checking }

data class NotificationsSettingsState(
    val enabled: Boolean,
    val supported: Boolean,
    val checkFrequency: ReplyNotificationFrequency = ReplyNotificationFrequency.DEFAULT,
    val operation: NotificationSettingsOperation? = null,
    val message: String = "",
    val isError: Boolean = false,
    val activity: AccountActivitySnapshot = AccountActivitySnapshot(),
    val backgroundSupported: Boolean = supported,
)

/** Account-scoped notification controls; hosts own permission requests and system settings. */
class NotificationsSettingsRuntime(
    private val username: String,
    private val notifications: ReplyNotificationRuntime?,
) {
    private val mutableState = MutableStateFlow(
        NotificationsSettingsState(
            enabled = isEnabled(),
            supported = notifications != null,
            backgroundSupported = notifications?.backgroundSupported == true,
            checkFrequency = notifications?.checkFrequency ?: ReplyNotificationFrequency.DEFAULT,
            activity = notifications?.activity?.snapshot(username) ?: AccountActivitySnapshot(),
        ),
    )
    val state = mutableState.asStateFlow()

    fun refresh() {
        mutableState.value = mutableState.value.copy(
            enabled = isEnabled(),
            checkFrequency = notifications?.checkFrequency ?: ReplyNotificationFrequency.DEFAULT,
            activity = notifications?.activity?.snapshot(username) ?: AccountActivitySnapshot(),
        )
    }

    suspend fun enable() {
        val runtime = notifications ?: return
        if (mutableState.value.operation != null || !runtime.backgroundSupported) return
        mutableState.value = mutableState.value.copy(
            operation = NotificationSettingsOperation.Enabling,
            message = "Setting up reply notifications…",
            isError = false,
        )
        try {
            when (runtime.enable(username)) {
                is ReplySubscriptionResult.Enabled -> finish("")
                ReplySubscriptionResult.Superseded -> finish("")
                ReplySubscriptionResult.UserNotFound -> finish("Hacker News user not found. Try again.", true)
                is ReplySubscriptionResult.Failed -> finish("Could not enable notifications. Check your connection and try again.", true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            finish("Could not enable notifications. Try again.", true)
        } finally {
            mutableState.value = mutableState.value.copy(enabled = isEnabled(), operation = null)
        }
    }

    fun disable() {
        if (mutableState.value.operation != null || !isEnabled()) return
        notifications?.disable()
        finish("")
    }

    fun setCheckFrequency(frequency: ReplyNotificationFrequency) {
        val runtime = notifications ?: return
        if (mutableState.value.operation != null) return
        try {
            runtime.setCheckFrequency(frequency)
            finish("")
        } catch (_: Exception) {
            finish("Could not update the background check frequency. Try again.", true)
        }
        refresh()
    }

    fun permissionDenied() {
        finish("Background checking is on. Allow notifications in system settings to receive alerts.")
    }

    fun setAlertPreferences(preferences: AccountAlertPreferences) {
        try {
            notifications?.setAlertPreferences(username, preferences)
            finish("")
        } catch (_: Exception) {
            finish("Could not update notification preferences. Try again.", true)
        }
        refresh()
    }

    suspend fun refreshActivity() {
        val runtime = notifications ?: return
        if (mutableState.value.operation != null) return
        mutableState.value = mutableState.value.copy(operation = NotificationSettingsOperation.Checking, message = "", isError = false)
        try {
            when (runtime.refreshActivity(username)) {
                is ReplyCheckResult.Success, ReplyCheckResult.Disabled -> finish("")
                ReplyCheckResult.UserNotFound -> finish("No public activity found for this Hacker News account.", true)
                is ReplyCheckResult.Failed -> finish("Could not check for replies. Check your connection and try again.", true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            finish("Could not check for replies. Try again.", true)
        } finally {
            mutableState.value = mutableState.value.copy(operation = null)
            refresh()
        }
    }

    suspend fun checkNow() {
        val runtime = notifications ?: return
        if (!isEnabled() || mutableState.value.operation != null) return
        mutableState.value = mutableState.value.copy(
            operation = NotificationSettingsOperation.Checking,
            message = "Checking for new replies…",
            isError = false,
        )
        try {
            when (val result = runtime.checkNow()) {
                is ReplyCheckResult.Success -> finish(
                    when (val count = result.replies.size) {
                        0 -> ""
                        1 -> "Found 1 new reply."
                        else -> "Found $count new replies."
                    },
                )
                ReplyCheckResult.Disabled -> finish("")
                ReplyCheckResult.UserNotFound -> finish("Hacker News user not found. Try again.", true)
                is ReplyCheckResult.Failed -> finish("Could not check for replies. Check your connection and try again.", true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            finish("Could not check for replies. Try again.", true)
        } finally {
            mutableState.value = mutableState.value.copy(enabled = isEnabled(), operation = null)
        }
    }

    private fun isEnabled(): Boolean = username.trim().let {
        it.isNotEmpty() && notifications?.configuredUsername == it
    }

    private fun finish(message: String, isError: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            enabled = isEnabled(), message = message, isError = isError,
        )
    }
}
