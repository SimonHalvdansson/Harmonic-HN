package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.ReplyNotificationFrequency
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

object ReplyNotificationKeys {
    const val USERNAME = "reply_notifications_username"
    const val LAST_SEEN_ITEM_ID = "reply_notifications_last_seen_item_id"
    const val CHECK_FREQUENCY_MINUTES = "reply_notifications_check_frequency_minutes"
    const val ACCOUNT_SCOPED = "reply_notifications_account_scoped"
}

sealed interface ReplySubscriptionResult {
    data class Enabled(val username: String) : ReplySubscriptionResult
    data object Superseded : ReplySubscriptionResult
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
    val intervalMillis: Long = ReplyNotificationFrequency.DEFAULT.intervalMillis,
    val flexMillis: Long = maxOf(5L * 60L * 1_000L, intervalMillis / 20),
    val wifiOnly: Boolean = false,
    val delayMillis: Long? = null,
)

/** Host hooks for the operating system's scheduler and notification surface. */
interface ReplyNotificationPlatform {
    val supported: Boolean get() = true
    val settingsHost: com.simon.harmonichackernews.platform.NotificationSettingsHost? get() = null
    fun prepareNotifications()
    fun scheduleChecks(schedule: ReplyNotificationSchedule)
    fun cancelChecks()
    /** Completes after delivery; implementations must keep blocking platform calls off the caller. */
    suspend fun publish(batch: ReplyNotificationBatch)

    companion object {
        /** Hosts without native alerts still retain the portable foreground activity inbox. */
        val Unavailable: ReplyNotificationPlatform = object : ReplyNotificationPlatform {
            override val supported = false
            override fun prepareNotifications() = Unit
            override fun scheduleChecks(schedule: ReplyNotificationSchedule) = Unit
            override fun cancelChecks() = Unit
            override suspend fun publish(batch: ReplyNotificationBatch) = Unit
        }
    }
}

/** Common notification copy, grouping, ordering and deep-link policy. */
object ReplyNotificationPresentation {
    fun present(replies: List<HackerNewsReply>): ReplyNotificationBatch {
        val ordered = replies.distinctBy(HackerNewsReply::id).sortedBy(HackerNewsReply::id)
        if (ordered.isEmpty()) return ReplyNotificationBatch(emptyList())
        val notifications = ordered.takeLast(MAX_INDIVIDUAL_NOTIFICATIONS).map(::individual)
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
    const val MAX_INDIVIDUAL_NOTIFICATIONS = 5
}

/** Common subscription, checkpoint and failure workflow for reply notifications. */
class ReplyNotificationUseCase(
    private val scanner: ReplyScanner,
    private val store: KeyValueStore,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val activity = AccountActivityRepository(store)
    private var accountSession: (() -> Any?)? = null
    private var accountUsername: (() -> String?)? = null

    fun bindAccountScope(session: () -> Any?, username: () -> String?) {
        accountSession = session
        accountUsername = username
    }

    private fun matchesAccount(username: String): Boolean = accountUsername?.let { it() == username } ?: true
    internal fun accountToken(): Any? = accountSession?.invoke()
    internal fun ownsAccount(username: String, token: Any?): Boolean = !accountScoped || matchesAccount(username) && token === accountToken()
    private val checkMutex = Mutex()
    internal var subscriptionRevision = 0L
        private set

    val configuredUsername: String
        get() = store.getString(ReplyNotificationKeys.USERNAME).orEmpty().trim()

    val isEnabled: Boolean get() = configuredUsername.isNotEmpty()
    val accountScoped: Boolean get() = store.getBoolean(ReplyNotificationKeys.ACCOUNT_SCOPED, true)

    var checkFrequency: ReplyNotificationFrequency
        get() = ReplyNotificationFrequency.fromMinutes(
            store.getInt(
                ReplyNotificationKeys.CHECK_FREQUENCY_MINUTES,
                ReplyNotificationFrequency.DEFAULT.minutes,
            ),
        )
        set(value) = store.putInt(ReplyNotificationKeys.CHECK_FREQUENCY_MINUTES, value.minutes)

    suspend fun enable(username: String?, accountScoped: Boolean = true): ReplySubscriptionResult {
        val normalized = ReplyText.normalizeUsername(username)
        if (normalized.isEmpty()) return ReplySubscriptionResult.UserNotFound
        if (accountScoped && !matchesAccount(normalized)) return ReplySubscriptionResult.Superseded
        val session = accountSession?.invoke()
        val revision = ++subscriptionRevision
        return try {
            val baseline = scanner.initialize(normalized)
            if (revision != subscriptionRevision || accountScoped && (session !== accountSession?.invoke() || !matchesAccount(normalized))) return ReplySubscriptionResult.Superseded
            if (baseline == null) return ReplySubscriptionResult.UserNotFound
            subscriptionRevision++
            store.update {
                putString(ReplyNotificationKeys.USERNAME, baseline.username)
                putBoolean(ReplyNotificationKeys.ACCOUNT_SCOPED, accountScoped)
                putString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID, baseline.lastSeenItemId.toString())
            }
            ReplySubscriptionResult.Enabled(baseline.username)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ReplySubscriptionResult.Failed(error)
        }
    }

    fun disable() {
        subscriptionRevision++
        store.putString(ReplyNotificationKeys.USERNAME, null)
        store.putString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID, "0")
    }

    suspend fun check(): ReplyCheckResult = checkMutex.withLock {
        val username = configuredUsername
        if (username.isEmpty() || accountScoped && !matchesAccount(username)) return@withLock ReplyCheckResult.Disabled
        val session = accountSession?.invoke()
        val revision = subscriptionRevision
        try {
            val alertCheckpoint = lastSeenItemId()
            val historyCheckpoint = activity.snapshot(username).checkpoint
            val checkpoint = if (historyCheckpoint > 0) minOf(alertCheckpoint, historyCheckpoint) else alertCheckpoint
            val result = scanner.scan(username, checkpoint)
            // Settings can change while the scan is suspended. Its result belongs to the
            // original subscription, even if the same username has since been re-enabled.
            if (revision != subscriptionRevision || username != configuredUsername || !ownsAccount(username, session)) {
                return@withLock ReplyCheckResult.Disabled
            }
            if (!result.userFound) return@withLock ReplyCheckResult.UserNotFound
            activity.record(username, result, nowMillis(), historical = false)
            store.putString(
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID,
                result.lastSeenItemId.toString(),
            )
            ReplyCheckResult.Success(result.replies.filter { it.id > alertCheckpoint && activity.shouldAlert(username, it) })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ReplyCheckResult.Failed(error)
        }
    }

    /** Foreground refresh is available without a background subscription or alert permission. */
    suspend fun refreshActivity(username: String): ReplyCheckResult = checkMutex.withLock {
        if (!matchesAccount(username)) return@withLock ReplyCheckResult.Disabled
        val session = accountSession?.invoke()
        val revision = subscriptionRevision
        try {
            val old = activity.snapshot(username)
            val result = if (old.historyLoaded) scanner.scan(username, old.checkpoint) else scanner.recentReplies(username)
            if (session !== accountSession?.invoke() || !matchesAccount(username) || revision != subscriptionRevision) return@withLock ReplyCheckResult.Disabled
            if (!result.userFound) return@withLock ReplyCheckResult.UserNotFound
            activity.record(username, result, nowMillis(), historical = !old.historyLoaded)
            if (configuredUsername == username) store.putString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID, result.lastSeenItemId.toString())
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
    private val presentationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val currentMinutesFromMidnight: () -> Int = { 12 * 60 },
) {
    val backgroundSupported: Boolean get() = platform.supported
    val settingsHost get() = platform.settingsHost
    val activity get() = useCase.activity
    val configuredUsername: String get() = useCase.configuredUsername
    val isEnabled: Boolean get() = useCase.isEnabled
    val accountScoped: Boolean get() = useCase.accountScoped
    val checkFrequency: ReplyNotificationFrequency get() = useCase.checkFrequency
    private val schedule: ReplyNotificationSchedule
        get() {
            val preferences = activity.snapshot(configuredUsername).alerts
            val minute = currentMinutesFromMidnight()
            val delay = if (preferences.allowsChecking(minute)) null else {
                ((preferences.startMinute - minute + 1440) % 1440) * 60_000L
            }
            return ReplyNotificationSchedule(intervalMillis = checkFrequency.intervalMillis,
                wifiOnly = preferences.wifiOnly, delayMillis = delay)
        }

    /** Re-evaluate local checking hours after a wakeup, including after a time-zone change. */
    fun rescheduleChecks() {
        if (isEnabled && backgroundSupported) platform.scheduleChecks(schedule) else platform.cancelChecks()
    }

    fun bindAccountScope(session: () -> Any?, username: () -> String?) = useCase.bindAccountScope(session, username)

    fun setAlertPreferences(username: String, preferences: AccountAlertPreferences) {
        activity.setAlerts(username, preferences)
        if (configuredUsername == username) platform.scheduleChecks(schedule)
    }

    suspend fun refreshActivity(username: String): ReplyCheckResult = useCase.refreshActivity(username)

    suspend fun checkInBackground(): ReplyCheckResult {
        if (!backgroundSupported || !isEnabled || !activity.snapshot(configuredUsername).alerts.allowsChecking(currentMinutesFromMidnight())) return ReplyCheckResult.Disabled
        return checkNow()
    }

    fun setCheckFrequency(frequency: ReplyNotificationFrequency) {
        if (checkFrequency == frequency) return
        useCase.checkFrequency = frequency
        // Replace the existing periodic job without initializing a new reply checkpoint.
        if (isEnabled) platform.scheduleChecks(schedule)
    }

    suspend fun enable(username: String?, accountScoped: Boolean = true): ReplySubscriptionResult {
        if (!backgroundSupported) return ReplySubscriptionResult.Failed(UnsupportedOperationException("Native notifications are unavailable"))
        return useCase.enable(username, accountScoped).also { result ->
            if (result is ReplySubscriptionResult.Enabled) {
                platform.prepareNotifications()
                platform.scheduleChecks(schedule)
            }
        }
    }

    fun disable() {
        useCase.disable()
        platform.cancelChecks()
    }

    suspend fun checkNow(): ReplyCheckResult {
        val revision = useCase.subscriptionRevision
        val account = useCase.accountToken()
        val result = useCase.check()
        if (result is ReplyCheckResult.Success) {
            val username = configuredUsername
            val allowed = result.replies.filter { activity.shouldAlert(username, it) }
            val batch = withContext(presentationDispatcher) {
                ReplyNotificationPresentation.present(allowed)
            }
            // Preparing a large backlog suspends the owner. Settings may disable or replace
            // the subscription during that time; never publish that obsolete batch.
            if (revision != useCase.subscriptionRevision || !useCase.ownsAccount(username, account)) return ReplyCheckResult.Disabled
            // Alert preferences and mute state can change while presentation is suspended.
            val stillAllowed = allowed.filter { activity.shouldAlert(username, it) }
            platform.publish(if (stillAllowed.size == allowed.size) batch else ReplyNotificationPresentation.present(stillAllowed))
        }
        return result
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
