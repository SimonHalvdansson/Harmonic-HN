package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CancellationException

object ReplyNotificationKeys {
    const val USERNAME = "reply_notifications_username"
    const val LAST_SEEN_ITEM_ID = "reply_notifications_last_seen_item_id"
}

sealed interface ReplySubscriptionResult {
    data class Enabled(val username: String) : ReplySubscriptionResult
    data object UserNotFound : ReplySubscriptionResult
    data class Failed(val cause: Throwable) : ReplySubscriptionResult
}

sealed interface ReplyCheckResult {
    data object Disabled : ReplyCheckResult
    data class Success(val replies: List<HackerNewsReply>) : ReplyCheckResult
    data object UserNotFound : ReplyCheckResult
    data class Failed(val cause: Throwable) : ReplyCheckResult
}

sealed interface LatestReplyLookupResult {
    data class Found(val reply: HackerNewsReply) : LatestReplyLookupResult
    data object NoRecentReply : LatestReplyLookupResult
    data object UserNotFound : LatestReplyLookupResult
    data class Failed(val cause: Throwable) : LatestReplyLookupResult
}

/** Common subscription, checkpoint and failure workflow for reply notifications. */
class ReplyNotificationUseCase(
    private val scanner: ReplyScanner,
    private val store: KeyValueStore,
) {
    val configuredUsername: String
        get() = store.getString(ReplyNotificationKeys.USERNAME).orEmpty().trim()

    val isEnabled: Boolean get() = configuredUsername.isNotEmpty()

    suspend fun enable(username: String?): ReplySubscriptionResult {
        val normalized = ReplyText.normalizeUsername(username)
        if (normalized.isEmpty()) return ReplySubscriptionResult.UserNotFound
        return try {
            val baseline = scanner.initialize(normalized)
                ?: return ReplySubscriptionResult.UserNotFound
            store.putString(ReplyNotificationKeys.USERNAME, baseline.username)
            store.putString(
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID,
                baseline.lastSeenItemId.toString(),
            )
            ReplySubscriptionResult.Enabled(baseline.username)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ReplySubscriptionResult.Failed(error)
        }
    }

    fun disable() {
        store.putString(ReplyNotificationKeys.USERNAME, null)
        store.putString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID, "0")
    }

    suspend fun check(): ReplyCheckResult {
        val username = configuredUsername
        if (username.isEmpty()) return ReplyCheckResult.Disabled
        return try {
            val result = scanner.scan(username, lastSeenItemId())
            if (!result.userFound) return ReplyCheckResult.UserNotFound
            store.putString(
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID,
                result.lastSeenItemId.toString(),
            )
            ReplyCheckResult.Success(result.replies)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ReplyCheckResult.Failed(error)
        }
    }

    suspend fun findLatest(username: String?): LatestReplyLookupResult {
        val normalized = ReplyText.normalizeUsername(username)
        if (normalized.isEmpty()) return LatestReplyLookupResult.UserNotFound
        return try {
            val result = scanner.findLatestReply(normalized)
            when {
                !result.userFound -> LatestReplyLookupResult.UserNotFound
                result.reply == null -> LatestReplyLookupResult.NoRecentReply
                else -> LatestReplyLookupResult.Found(result.reply)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            LatestReplyLookupResult.Failed(error)
        }
    }

    private fun lastSeenItemId(): Int =
        store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID, "0")
            ?.toIntOrNull()
            ?: 0
}
