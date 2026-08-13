package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.utils.HackerNewsLinks
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

data class ReplyNotificationPayload(
    val id: Int,
    val title: String,
    val body: String,
    val deepLink: String,
    val author: String? = null,
)

data class ReplyNotificationBatch(
    val notifications: List<ReplyNotificationPayload>,
    val summary: ReplyNotificationPayload? = null,
)

data class ReplyNotificationSchedule(
    val intervalMillis: Long = 30L * 60L * 1_000L,
    val flexMillis: Long = 5L * 60L * 1_000L,
)

/** Host hooks for the operating system's scheduler and notification surface. */
interface ReplyNotificationPlatform {
    fun prepareNotifications()
    fun scheduleChecks(schedule: ReplyNotificationSchedule)
    fun cancelChecks()
    fun publish(batch: ReplyNotificationBatch)
}

/** Common notification copy, grouping, ordering and deep-link policy. */
object ReplyNotificationPresentation {
    fun present(replies: List<HackerNewsReply>): ReplyNotificationBatch {
        val ordered = replies.distinctBy(HackerNewsReply::id).sortedBy(HackerNewsReply::id)
        if (ordered.isEmpty()) return ReplyNotificationBatch(emptyList())
        val notifications = ordered.map(::individual)
        if (notifications.size == 1) return ReplyNotificationBatch(notifications)

        val latest = ordered.last()
        return ReplyNotificationBatch(
            notifications = notifications,
            summary = ReplyNotificationPayload(
                id = SUMMARY_NOTIFICATION_ID,
                title = "${ordered.size} new replies",
                body = "New Hacker News replies",
                deepLink = deepLink(latest),
            ),
        )
    }

    fun individual(reply: HackerNewsReply): ReplyNotificationPayload = ReplyNotificationPayload(
        id = reply.id,
        title = "New reply from ${reply.by}",
        body = reply.text,
        deepLink = deepLink(reply),
        author = reply.by,
    )

    fun deepLink(reply: HackerNewsReply): String {
        val parentId = reply.parentId.takeIf { it > 0 } ?: reply.id
        return HackerNewsLinks.itemUrl(parentId, reply.id)
    }

    const val SUMMARY_NOTIFICATION_ID = 98_373
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

/**
 * Portable reply-notification coordinator. Platform code only schedules wakeups and turns an
 * already-presented batch into native notifications.
 */
class ReplyNotificationRuntime(
    private val useCase: ReplyNotificationUseCase,
    private val platform: ReplyNotificationPlatform,
    private val schedule: ReplyNotificationSchedule = ReplyNotificationSchedule(),
) {
    val configuredUsername: String get() = useCase.configuredUsername
    val isEnabled: Boolean get() = useCase.isEnabled

    suspend fun enable(username: String?): ReplySubscriptionResult =
        useCase.enable(username).also { result ->
            if (result is ReplySubscriptionResult.Enabled) {
                platform.prepareNotifications()
                platform.scheduleChecks(schedule)
            }
        }

    fun disable() {
        useCase.disable()
        platform.cancelChecks()
    }

    suspend fun checkNow(): ReplyCheckResult = useCase.check().also { result ->
        if (result is ReplyCheckResult.Success) {
            platform.publish(ReplyNotificationPresentation.present(result.replies))
        }
    }

    suspend fun publishLatest(username: String?): LatestReplyLookupResult =
        useCase.findLatest(username).also { result ->
            if (result is LatestReplyLookupResult.Found) {
                platform.prepareNotifications()
                platform.publish(
                    ReplyNotificationBatch(
                        notifications = listOf(ReplyNotificationPresentation.individual(result.reply)),
                    ),
                )
            }
        }
}
