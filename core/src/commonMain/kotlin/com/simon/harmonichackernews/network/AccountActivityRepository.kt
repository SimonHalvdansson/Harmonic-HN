package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class AccountActivityKind { Reply, SubmissionComment }

@Serializable
data class AccountActivityEntry(val reply: HackerNewsReply, val read: Boolean = false)

@Serializable
data class MutedDiscussion(val id: Int, val title: String)

@Serializable
data class AccountAlertPreferences(
    val replies: Boolean = true,
    val submissionComments: Boolean = true,
    val wifiOnly: Boolean = false,
    val limitedHours: Boolean = false,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 22 * 60,
) {
    fun allowsChecking(minute: Int): Boolean = !limitedHours || when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minute in startMinute until endMinute
        else -> minute >= startMinute || minute < endMinute
    }

    val hoursLabel: String get() = if (limitedHours) "${timeLabel(startMinute)}–${timeLabel(endMinute)}" else "All day"

    companion object {
        fun timeLabel(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
    }
}

@Serializable
data class AccountActivitySnapshot(
    val initialized: Boolean = false,
    val historyLoaded: Boolean = false,
    val checkpoint: Int = 0,
    val lastCheckedMillis: Long = 0,
    val entries: List<AccountActivityEntry> = emptyList(),
    val mutedDiscussions: List<MutedDiscussion> = emptyList(),
    val alerts: AccountAlertPreferences = AccountAlertPreferences(),
)

/** Bounded, account-scoped activity, independent of Android notification delivery. */
class AccountActivityRepository(private val store: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cached = mutableMapOf<String, AccountActivitySnapshot>()
    private val mutableChanges = MutableStateFlow(0L)
    val changes = mutableChanges.asStateFlow()

    fun snapshot(username: String): AccountActivitySnapshot = cached.getOrPut(username) {
        store.getString(key(username))?.let { value ->
            runCatching { json.decodeFromString<AccountActivitySnapshot>(value) }.getOrNull()
        } ?: AccountActivitySnapshot()
    }

    fun record(username: String, result: ReplyScanResult, nowMillis: Long, historical: Boolean) {
        val old = snapshot(username)
        val existing = old.entries.associateBy { it.reply.id }
        val incoming = result.replies.map { reply -> existing[reply.id] ?: AccountActivityEntry(reply, read = historical) }
        save(username, old.copy(
            initialized = true,
            historyLoaded = old.historyLoaded || historical,
            checkpoint = maxOf(old.checkpoint, result.lastSeenItemId),
            lastCheckedMillis = nowMillis,
            entries = (incoming + old.entries).distinctBy { it.reply.id }
                .sortedByDescending { it.reply.id }.take(MAX_ENTRIES),
        ))
    }

    fun markRead(username: String, id: Int? = null) {
        val old = snapshot(username)
        save(username, old.copy(entries = old.entries.map {
            if (id == null || it.reply.id == id) it.copy(read = true) else it
        }))
    }

    fun setMuted(username: String, discussionId: Int, title: String, muted: Boolean) {
        if (discussionId <= 0) return
        val old = snapshot(username)
        val remaining = old.mutedDiscussions.filterNot { it.id == discussionId }
        save(username, old.copy(mutedDiscussions = if (muted) remaining + MutedDiscussion(discussionId, title) else remaining))
    }

    fun setAlerts(username: String, alerts: AccountAlertPreferences) {
        val normalized = alerts.copy(startMinute = alerts.startMinute.coerceIn(0, 1439), endMinute = alerts.endMinute.coerceIn(0, 1439))
        save(username, snapshot(username).copy(alerts = normalized))
    }

    fun shouldAlert(username: String, reply: HackerNewsReply): Boolean {
        val state = snapshot(username)
        return state.mutedDiscussions.none { it.id == reply.discussionId } && when (reply.kind) {
            AccountActivityKind.Reply -> state.alerts.replies
            AccountActivityKind.SubmissionComment -> state.alerts.submissionComments
        }
    }

    private fun save(username: String, snapshot: AccountActivitySnapshot) {
        store.putString(key(username), json.encodeToString(snapshot))
        cached[username] = snapshot
        mutableChanges.value++
    }

    private fun key(username: String) = "account_activity_$username"
    companion object { const val MAX_ENTRIES = 100 }
}
