package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.time.Clock
import kotlinx.serialization.Serializable

@Serializable
data class HackerNewsReply(
    val id: Int,
    val parentId: Int,
    val by: String,
    val text: String,
    val time: Int = 0,
    val storyId: Int = 0,
    val storyTitle: String = "",
    val kind: AccountActivityKind = AccountActivityKind.Reply,
) {
    val discussionId: Int get() = storyId.takeIf { it > 0 } ?: parentId
}

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
    suspend fun recentReplies(username: String): ReplyScanResult = scan(username, 0)
}

class DefaultReplyScanner(
    private val api: HackerNewsApi,
    private val clock: Clock = Clock.System,
    private val maxSubmissionsPerCheck: Int = 1_000,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReplyScanner {
    override suspend fun initialize(username: String): ReplySubscriptionBaseline? {
        val normalized = normalizeUsername(username)
        if (normalized.isEmpty()) return null
        val user = api.getUser(normalized) ?: return null
        if (user.id != normalized) return null
        val maxItemId = api.getMaxItemId()
        if (maxItemId <= 0) return null
        return ReplySubscriptionBaseline(user.id, maxItemId)
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
        if (user.id != normalized) return ReplyScanResult(emptyList(), previousLastSeenItemId, userFound = false)
        if (user.submitted.isEmpty()) return ReplyScanResult(emptyList(), currentMaxItemId)

        val scanned = loadRecentReplies(normalized, user.submitted, previousLastSeenItemId)
        return ReplyScanResult(
            replies = scanned.replies,
            lastSeenItemId = max(currentMaxItemId, scanned.highestProcessedId),
        )
    }

    override suspend fun findLatestReply(username: String): LatestReplyResult {
        val normalized = normalizeUsername(username)
        val user = api.getUser(normalized) ?: return LatestReplyResult(null, false)
        if (user.id != normalized) return LatestReplyResult(null, false)
        val scanned = loadRecentReplies(normalized, user.submitted, minimumId = 0)
        return LatestReplyResult(scanned.replies.maxByOrNull { it.id }, true)
    }

    override suspend fun recentReplies(username: String): ReplyScanResult {
        val normalized = normalizeUsername(username)
        val user = api.getUser(normalized)
            ?: return ReplyScanResult(emptyList(), 0, userFound = false)
        if (user.id != normalized) return ReplyScanResult(emptyList(), 0, userFound = false)
        val checkpoint = api.getMaxItemId()
        check(checkpoint > 0) { "HN returned no current item ID" }
        val scanned = loadRecentReplies(normalized, user.submitted, minimumId = 0)
        return ReplyScanResult(scanned.replies, max(checkpoint, scanned.highestProcessedId))
    }

    private data class ScannedReplies(val replies: List<HackerNewsReply>, val highestProcessedId: Int)

    private suspend fun loadRecentReplies(
        username: String,
        submitted: List<Int>,
        minimumId: Int,
    ): ScannedReplies = withContext(dispatcher) {
        val replies = mutableListOf<HackerNewsReply>()
        val storyContexts = mutableMapOf<Int, Pair<Int, String>>()
        var highestProcessedId = minimumId
        // Only prefetch a small batch: older submissions terminate the scan. Await in source
        // order so concurrency cannot change the age cutoff, reply order, or checkpoint.
        for (parentIds in submitted.take(maxSubmissionsPerCheck).filter { it > 0 }.chunked(MAX_REQUESTS)) {
            val recent = loadRecentParents(parentIds)
            data class Child(val id: Int, val parentId: Int, val context: Pair<Int, String>, val kind: AccountActivityKind)
            val children = mutableListOf<Child>()
            for ((parentId, parent) in recent) {
                val ids = parent?.kids.orEmpty().filter { it > minimumId }
                if (ids.isEmpty() || parent == null) continue
                val context = storyContext(parent, storyContexts)
                val kind = if (parent.type == "comment") AccountActivityKind.Reply else AccountActivityKind.SubmissionComment
                children += ids.map { Child(it, parentId, context, kind) }
            }
            for (batch in children.chunked(MAX_REQUESTS)) {
                val items = batch.map { child ->
                    async { parseReply(api.getItem(child.id), username, child.parentId)?.copy(
                        storyId = child.context.first, storyTitle = child.context.second, kind = child.kind,
                    ) }
                }.awaitAll()
                highestProcessedId = max(highestProcessedId, batch.maxOf { it.id })
                replies += items.filterNotNull()
            }
            if (recent.size != parentIds.size) break
        }
        ScannedReplies(replies, highestProcessedId)
    }

    private suspend fun storyContext(parent: HackerNewsItemDto, cache: MutableMap<Int, Pair<Int, String>>): Pair<Int, String> {
        cache[parent.id]?.let { return it }
        var current = parent
        val visited = mutableSetOf<Int>()
        repeat(64) {
            cache[current.id]?.let { return it }
            if (!visited.add(current.id)) return 0 to ""
            if (current.type != "comment") {
                val context = current.id to current.title.orEmpty()
                visited.forEach { cache[it] = context }
                return context
            }
            if (current.parent <= 0) return 0 to ""
            current = api.getItem(current.parent) ?: return 0 to ""
        }
        return 0 to ""
    }

    private suspend fun loadRecentParents(ids: List<Int>): List<Pair<Int, HackerNewsItemDto?>> = coroutineScope {
        val pending = ids.map { id ->
            async {
                try {
                    Result.success(id to api.getItem(id))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // A speculative read beyond the age cutoff must not fail the scan.
                    Result.failure(error)
                }
            }
        }
        try {
            buildList {
                for (request in pending) {
                    val entry = request.await().getOrThrow()
                    if (entry.second?.let { isOlderThanReplyWindow(it.time) } == true) break
                    add(entry)
                }
            }
        } finally {
            // Do not wait for slow requests for submissions older than the cutoff.
            pending.forEach { it.cancel() }
        }
    }

    private fun parseReply(
        item: HackerNewsItemDto?,
        username: String,
        fallbackParentId: Int,
    ): HackerNewsReply? {
        if (item == null || item.deleted || item.dead || item.type != "comment") return null
        val by = item.by.orEmpty().trim()
        if (by.isBlank() || by == username) return null
        if (isOlderThanReplyWindow(item.time) || item.id <= 0) return null
        return HackerNewsReply(
            id = item.id,
            parentId = item.parent.takeIf { it > 0 } ?: fallbackParentId,
            by = by,
            text = plainReplyText(item.text),
            time = item.time,
        )
    }

    private fun isOlderThanReplyWindow(epochSeconds: Int): Boolean =
        epochSeconds > 0 && epochSeconds < clock.now().epochSeconds - REPLY_WINDOW_SECONDS

    private companion object {
        const val MAX_REQUESTS = 4
        const val REPLY_WINDOW_SECONDS = 14L * 24L * 60L * 60L
    }
}

object ReplyText {
    const val EMPTY_REPLY_TEXT = "Tap to view the reply."
    private val whitespace = Regex("\\s+")

    fun normalizeUsername(username: String?): String = username.orEmpty().trim()

    fun plainReplyText(html: String?): String {
        if (html.isNullOrBlank()) return EMPTY_REPLY_TEXT
        val text = Ksoup.parse(html).text().replace(whitespace, " ").trim()
        if (text.isEmpty()) return EMPTY_REPLY_TEXT
        return if (text.length > 240) text.take(237) + "..." else text
    }
}

private fun normalizeUsername(username: String?): String = ReplyText.normalizeUsername(username)

private fun plainReplyText(html: String?): String = ReplyText.plainReplyText(html)
