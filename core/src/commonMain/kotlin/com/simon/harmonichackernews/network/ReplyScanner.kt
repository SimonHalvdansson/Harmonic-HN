package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import kotlin.math.max
import kotlin.time.Clock

data class HackerNewsReply(
    val id: Int,
    val parentId: Int,
    val by: String,
    val text: String,
)

data class ReplySubscriptionBaseline(
    val username: String,
    val lastSeenItemId: Int,
)

data class ReplyScanResult(
    val replies: List<HackerNewsReply>,
    val lastSeenItemId: Int,
    val userFound: Boolean = true,
)

data class LatestReplyResult(
    val reply: HackerNewsReply?,
    val userFound: Boolean,
)

interface ReplyScanner {
    suspend fun initialize(username: String): ReplySubscriptionBaseline?
    suspend fun scan(username: String, previousLastSeenItemId: Int): ReplyScanResult
    suspend fun findLatestReply(username: String): LatestReplyResult
}

class DefaultReplyScanner(
    private val api: HackerNewsApi,
    private val clock: Clock = Clock.System,
    private val maxSubmissionsPerCheck: Int = 1_000,
) : ReplyScanner {
    override suspend fun initialize(username: String): ReplySubscriptionBaseline? {
        val normalized = normalizeUsername(username)
        if (normalized.isEmpty()) return null
        val user = api.getUser(normalized) ?: return null
        if (!user.id.equals(normalized, ignoreCase = true)) return null
        val maxItemId = api.getMaxItemId()
        if (maxItemId <= 0) return null
        return ReplySubscriptionBaseline(user.id.ifBlank { normalized }, maxItemId)
    }

    override suspend fun scan(
        username: String,
        previousLastSeenItemId: Int,
    ): ReplyScanResult {
        val normalized = normalizeUsername(username)
        val currentMaxItemId = api.getMaxItemId()
        if (currentMaxItemId <= 0) throw IllegalStateException("HN returned no current item ID")
        if (previousLastSeenItemId <= 0) {
            return ReplyScanResult(emptyList(), currentMaxItemId)
        }
        val user = api.getUser(normalized)
            ?: return ReplyScanResult(emptyList(), previousLastSeenItemId, userFound = false)
        if (user.submitted.isEmpty()) return ReplyScanResult(emptyList(), currentMaxItemId)

        val replies = mutableListOf<HackerNewsReply>()
        var highestProcessedReplyId = previousLastSeenItemId
        for (parentId in user.submitted.take(maxSubmissionsPerCheck)) {
            if (parentId <= 0) continue
            val parent = api.getItem(parentId) ?: continue
            if (isOlderThanReplyWindow(parent.time)) break
            parent.kids.forEach { kidId ->
                if (kidId <= previousLastSeenItemId) return@forEach
                highestProcessedReplyId = max(highestProcessedReplyId, kidId)
                parseReply(api.getItem(kidId), normalized, parentId)?.let(replies::add)
            }
        }
        return ReplyScanResult(
            replies = replies,
            lastSeenItemId = max(currentMaxItemId, highestProcessedReplyId),
        )
    }

    override suspend fun findLatestReply(username: String): LatestReplyResult {
        val normalized = normalizeUsername(username)
        val user = api.getUser(normalized) ?: return LatestReplyResult(null, false)
        var latest: HackerNewsReply? = null
        for (parentId in user.submitted.take(maxSubmissionsPerCheck)) {
            if (parentId <= 0) continue
            val parent = api.getItem(parentId) ?: continue
            if (isOlderThanReplyWindow(parent.time)) break
            parent.kids.forEach { kidId ->
                if (kidId <= 0) return@forEach
                val reply = parseReply(api.getItem(kidId), normalized, parentId)
                if (reply != null && (latest?.id ?: Int.MIN_VALUE) < reply.id) latest = reply
            }
        }
        return LatestReplyResult(latest, true)
    }

    private fun parseReply(
        item: HackerNewsItemDto?,
        username: String,
        fallbackParentId: Int,
    ): HackerNewsReply? {
        if (item == null || item.deleted || item.dead || item.type != "comment") return null
        val by = item.by.orEmpty()
        if (by.isBlank() || by.equals(username, ignoreCase = true)) return null
        if (isOlderThanReplyWindow(item.time) || item.id <= 0) return null
        return HackerNewsReply(
            id = item.id,
            parentId = item.parent.takeIf { it > 0 } ?: fallbackParentId,
            by = by,
            text = plainReplyText(item.text),
        )
    }

    private fun isOlderThanReplyWindow(epochSeconds: Int): Boolean =
        epochSeconds > 0 && epochSeconds < clock.now().epochSeconds - REPLY_WINDOW_SECONDS

    private companion object {
        const val REPLY_WINDOW_SECONDS = 14L * 24L * 60L * 60L
    }
}

object ReplyText {
    const val EMPTY_REPLY_TEXT = "Tap to view the reply."

    fun normalizeUsername(username: String?): String = username.orEmpty().trim()

    fun plainReplyText(html: String?): String {
        if (html.isNullOrBlank()) return EMPTY_REPLY_TEXT
        val text = Ksoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return EMPTY_REPLY_TEXT
        return if (text.length > 240) text.take(237) + "..." else text
    }
}

private fun normalizeUsername(username: String?): String = ReplyText.normalizeUsername(username)

private fun plainReplyText(html: String?): String = ReplyText.plainReplyText(html)
