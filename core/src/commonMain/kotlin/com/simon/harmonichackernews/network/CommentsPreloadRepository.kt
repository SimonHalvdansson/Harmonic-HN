package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Small application-scoped cache of fully downloaded and prepared comment discussions.
 *
 * Entries are consumed by the first comments screen that opens them because the portable comment
 * models are mutable presentation objects. Algolia responses are also written through the normal
 * story cache, so later openings retain the existing disk-cache behavior.
 */
class CommentsPreloadRepository(
    private val algolia: AlgoliaRepository,
    private val official: OfficialCommentThreadLoader? = null,
    private val parser: AlgoliaCommentsParser = AlgoliaCommentsParser(),
    private val storeResponse: suspend (storyId: Int, response: String, summary: AlgoliaStorySummary?) -> Unit = { _, _, _ -> },
    private val nowMillis: () -> Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRetainedBytes: Long = DEFAULT_MAX_RETAINED_BYTES,
) {
    init {
        require(maxRetainedBytes >= 0) { "Preload memory budget cannot be negative" }
    }

    private val mutex = Mutex()
    private val entries = MutableStateFlow<Map<PreloadKey, PreparedCommentsThread>>(emptyMap())
    private val requests = AlgoliaCommentRequests(algolia, requestDispatcher)

    /** Ready preloads also supply immutable bytes when display order or filters have changed. */
    fun acquireAlgoliaRequest(storyId: Int): AlgoliaCommentRequest {
        val cutoff = nowMillis() - maxAgeMillis
        val ready = entries.value.values.firstOrNull {
            it is PreloadedCommentsThread && it.storyId == storyId && it.loadedAtMillis >= cutoff
        } as? PreloadedCommentsThread
        return ready?.let { AlgoliaCommentRequest.completed(storyId, it.response) }
            ?: requests.acquire(storyId)
    }

    private class PendingPreload {
        val result = CompletableDeferred<PreparedCommentsThread?>()
        var oversizedConsumed = false
    }
    private val inFlight = mutableMapOf<PreloadKey, PendingPreload>()

    suspend fun preload(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedCommentsThread? {
        if (storyId <= 0) return null
        val key = PreloadKey.create(
            storyId,
            topLevelCommentIds,
            filteredUsers,
            CommentThreadSource.ALGOLIA,
        )
        val request = acquireAlgoliaRequest(storyId)
        return try {
            prepare(key) {
                val response = request.await()
                val parsed = parser.parseForDisplay(response, key.topLevelCommentIds, key.filteredUsers)
                PreloadedCommentsThread(
                    storyId = storyId,
                    topLevelCommentIds = key.topLevelCommentIds,
                    filteredUsers = key.filteredUsers,
                    response = response,
                    parsed = parsed,
                    loadedAtMillis = nowMillis(),
                )
            } as? PreloadedCommentsThread
        } finally {
            request.close()
        }
    }

    suspend fun preloadOfficial(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedOfficialCommentsThread? {
        if (storyId <= 0) return null
        val loader = official ?: return null
        val key = PreloadKey.create(
            storyId,
            topLevelCommentIds,
            filteredUsers,
            CommentThreadSource.OFFICIAL,
        )
        return prepare(key) {
            when (val loaded = loader.load(storyId, key.filteredUsers, usedAsFallback = false)) {
                is CommentThreadLoadResult.Official -> PreloadedOfficialCommentsThread(
                    storyId = storyId,
                    topLevelCommentIds = key.topLevelCommentIds,
                    filteredUsers = key.filteredUsers,
                    story = loaded.story,
                    comments = loaded.comments,
                    usedAsFallback = loaded.usedAsFallback,
                    loadedAtMillis = nowMillis(),
                )
                is CommentThreadLoadResult.Algolia,
                is CommentThreadLoadResult.Failure,
                -> null
            }
        } as? PreloadedOfficialCommentsThread
    }

    private suspend fun prepare(
        key: PreloadKey,
        load: suspend () -> PreparedCommentsThread?,
    ): PreparedCommentsThread? {
        var creator = false
        val deferred = mutex.withLock {
            removeExpiredLocked()
            entries.value[key]?.let { return it }
            inFlight[key] ?: PendingPreload().also {
                inFlight[key] = it
                creator = true
            }
        }
        if (creator) {
            var cancellation: CancellationException? = null
            val loaded = try {
                load()
            } catch (error: CancellationException) {
                cancellation = error
                null
            } catch (_: Throwable) {
                null
            }
            withContext(NonCancellable) {
                mutex.withLock {
                    inFlight.remove(key)
                    if (loaded != null && loaded.estimatedRetainedBytes <= maxRetainedBytes) {
                        entries.value = entries.value + (key to loaded)
                        trimLocked()
                    }
                }
                deferred.result.complete(loaded)
                // An opening screen may consume the prepared content immediately. Persistence
                // completes even if leaving the feed cancels its preload job after publication.
                if (loaded is PreloadedCommentsThread) {
                    try {
                        storeResponse(loaded.storyId, loaded.response, loaded.parsed.cacheSummary)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // A failed disk cache must not discard successfully downloaded comments.
                    }
                }
            }
            cancellation?.let { throw it }
        }
        return deferred.result.await()
    }

    /** Returns and removes a prepared thread, waiting only when that exact thread is in flight. */
    suspend fun takeOrAwait(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
        awaitInFlight: Boolean = true,
    ): PreloadedCommentsThread? {
        if (storyId <= 0) return null
        return takePrepared(
            PreloadKey.create(
                storyId,
                topLevelCommentIds,
                filteredUsers,
                CommentThreadSource.ALGOLIA,
            ),
            awaitInFlight = awaitInFlight,
        ) as? PreloadedCommentsThread
    }

    suspend fun takeOfficialOrAwait(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedOfficialCommentsThread? {
        if (storyId <= 0) return null
        return takePrepared(
            PreloadKey.create(
                storyId,
                topLevelCommentIds,
                filteredUsers,
                CommentThreadSource.OFFICIAL,
            ),
        ) as? PreloadedOfficialCommentsThread
    }

    private suspend fun takePrepared(
        key: PreloadKey,
        awaitInFlight: Boolean = true,
    ): PreparedCommentsThread? {
        val pending = mutex.withLock {
            removeExpiredLocked()
            entries.value[key]?.let {
                entries.value = entries.value - key
                return it
            }
            inFlight[key].takeIf { awaitInFlight }
        } ?: return null
        val loaded = pending.result.await() ?: return null
        return mutex.withLock {
            removeExpiredLocked()
            entries.value[key]?.also { entries.value = entries.value - key }
                // A currently opening screen can consume a large result once without retaining it
                // in the application cache. The models are mutable, so never share that handoff.
                ?: loaded.takeIf {
                    it.estimatedRetainedBytes > maxRetainedBytes && !pending.oversizedConsumed &&
                        it.loadedAtMillis >= nowMillis() - maxAgeMillis
                }?.also { pending.oversizedConsumed = true }
        }
    }

    suspend fun isPrepared(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): Boolean {
        val key = PreloadKey.create(
            storyId,
            topLevelCommentIds,
            filteredUsers,
            CommentThreadSource.ALGOLIA,
        )
        return isPrepared(key)
    }

    suspend fun isOfficialPrepared(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): Boolean {
        val key = PreloadKey.create(
            storyId,
            topLevelCommentIds,
            filteredUsers,
            CommentThreadSource.OFFICIAL,
        )
        return isPrepared(key)
    }

    private suspend fun isPrepared(key: PreloadKey): Boolean {
        return mutex.withLock {
            removeExpiredLocked()
            key in entries.value || key in inFlight
        }
    }

    suspend fun preparedCount(): Int = mutex.withLock {
        removeExpiredLocked()
        entries.value.size
    }

    private fun removeExpiredLocked() {
        val cutoff = nowMillis() - maxAgeMillis
        entries.value = entries.value.filterValues { it.loadedAtMillis >= cutoff }
    }

    private fun trimLocked() {
        var bytes = entries.value.values.sumOf { it.estimatedRetainedBytes }
        while (entries.value.size > maxEntries.coerceAtLeast(1) || bytes > maxRetainedBytes) {
            val oldest = entries.value.minByOrNull { it.value.loadedAtMillis }?.key ?: return
            bytes -= entries.value.getValue(oldest).estimatedRetainedBytes
            entries.value = entries.value - oldest
        }
    }

    private data class PreloadKey(
        val storyId: Int,
        val topLevelCommentIds: List<Int>,
        val filteredUsers: Set<String>,
        val source: CommentThreadSource,
    ) {
        companion object {
            fun create(
                storyId: Int,
                topLevelCommentIds: List<Int>,
                filteredUsers: Set<String>,
                source: CommentThreadSource,
            ) = PreloadKey(
                storyId = storyId,
                topLevelCommentIds = topLevelCommentIds.toList(),
                filteredUsers = filteredUsers.mapTo(linkedSetOf()) { it.lowercase() },
                source = source,
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 24
        const val DEFAULT_MAX_AGE_MILLIS = 5 * 60 * 1_000L
        const val DEFAULT_MAX_RETAINED_BYTES = 8L * 1024 * 1024
    }
}

sealed interface PreparedCommentsThread {
    val storyId: Int
    val topLevelCommentIds: List<Int>
    val filteredUsers: Set<String>
    val loadedAtMillis: Long
    /** Estimated text/object weight, not an exact VM heap measurement. */
    val estimatedRetainedBytes: Long
}

data class PreloadedCommentsThread(
    override val storyId: Int,
    override val topLevelCommentIds: List<Int>,
    override val filteredUsers: Set<String>,
    val response: String,
    val parsed: AlgoliaCommentsResponse,
    override val loadedAtMillis: Long,
) : PreparedCommentsThread {
    override val estimatedRetainedBytes: Long = response.length * 2L +
        parsed.comments.sumOf { it.retainedTextWeight() } +
        (parsed.cacheSummary?.preparedThread?.comments?.sumOf {
            128L + 2L * (it.html.length + (it.expandedHtml?.length ?: 0) + it.author.length)
        } ?: 0L)
}

data class PreloadedOfficialCommentsThread(
    override val storyId: Int,
    override val topLevelCommentIds: List<Int>,
    override val filteredUsers: Set<String>,
    val story: Story,
    val comments: MutableList<Comment>,
    val usedAsFallback: Boolean,
    override val loadedAtMillis: Long,
) : PreparedCommentsThread {
    override val estimatedRetainedBytes: Long = 512L +
        2L * (story.text?.length ?: 0) + comments.sumOf {
            // Official comments have not expanded anchors yet. Budget room for that copy without
            // invoking the lazy HTML parser on the preload caller just to estimate memory.
            256L + 4L * (it.text?.length ?: 0) + 2L * (it.by?.length ?: 0)
        }
}

private fun Comment.retainedTextWeight(): Long = 256L +
    2L * ((text?.length ?: 0) + (expandedAnchorText?.length ?: 0) + (by?.length ?: 0))
