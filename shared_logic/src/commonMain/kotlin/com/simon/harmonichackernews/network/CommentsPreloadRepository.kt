package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Small application-scoped cache of fully downloaded and parsed Algolia discussions.
 *
 * Entries are consumed by the first comments screen that opens them because the portable comment
 * models are mutable presentation objects. The raw response is also written through the normal
 * story cache, so later openings retain the existing disk-cache behavior.
 */
class CommentsPreloadRepository(
    private val algolia: AlgoliaRepository,
    private val parser: AlgoliaCommentsParser = AlgoliaCommentsParser(),
    private val storeResponse: suspend (storyId: Int, response: String) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<PreloadKey, PreloadedCommentsThread>()
    private val inFlight = mutableMapOf<PreloadKey, CompletableDeferred<PreloadedCommentsThread?>>()

    suspend fun preload(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedCommentsThread? {
        if (storyId <= 0) return null
        val key = PreloadKey.create(storyId, topLevelCommentIds, filteredUsers)
        var creator = false
        val deferred = mutex.withLock {
            removeExpiredLocked()
            entries[key]?.let { return it }
            inFlight[key] ?: CompletableDeferred<PreloadedCommentsThread?>().also {
                inFlight[key] = it
                creator = true
            }
        }
        if (creator) {
            var cancellation: CancellationException? = null
            val loaded = try {
                val response = algolia.getItemJson(storyId)
                val parsed = parser.parse(response, key.topLevelCommentIds, key.filteredUsers)
                storeResponse(storyId, response)
                PreloadedCommentsThread(
                    storyId = storyId,
                    topLevelCommentIds = key.topLevelCommentIds,
                    filteredUsers = key.filteredUsers,
                    response = response,
                    parsed = parsed,
                    loadedAtMillis = nowMillis(),
                )
            } catch (error: CancellationException) {
                cancellation = error
                null
            } catch (_: Throwable) {
                null
            }
            withContext(NonCancellable) {
                mutex.withLock {
                    inFlight.remove(key)
                    if (loaded != null) {
                        entries[key] = loaded
                        trimLocked()
                    }
                }
                deferred.complete(loaded)
            }
            cancellation?.let { throw it }
        }
        return deferred.await()
    }

    /** Returns and removes a prepared thread, waiting only when that exact thread is in flight. */
    suspend fun takeOrAwait(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedCommentsThread? {
        if (storyId <= 0) return null
        val key = PreloadKey.create(storyId, topLevelCommentIds, filteredUsers)
        val pending = mutex.withLock {
            removeExpiredLocked()
            entries.remove(key)?.let { return it }
            inFlight[key]
        } ?: return null
        pending.await() ?: return null
        return mutex.withLock {
            removeExpiredLocked()
            entries.remove(key)
        }
    }

    suspend fun isPrepared(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): Boolean {
        val key = PreloadKey.create(storyId, topLevelCommentIds, filteredUsers)
        return mutex.withLock {
            removeExpiredLocked()
            key in entries || key in inFlight
        }
    }

    suspend fun preparedCount(): Int = mutex.withLock {
        removeExpiredLocked()
        entries.size
    }

    private fun removeExpiredLocked() {
        val cutoff = nowMillis() - maxAgeMillis
        entries.entries.removeAll { (_, value) -> value.loadedAtMillis < cutoff }
    }

    private fun trimLocked() {
        while (entries.size > maxEntries.coerceAtLeast(1)) {
            val oldest = entries.minByOrNull { it.value.loadedAtMillis }?.key ?: return
            entries.remove(oldest)
        }
    }

    private data class PreloadKey(
        val storyId: Int,
        val topLevelCommentIds: List<Int>,
        val filteredUsers: Set<String>,
    ) {
        companion object {
            fun create(
                storyId: Int,
                topLevelCommentIds: List<Int>,
                filteredUsers: Set<String>,
            ) = PreloadKey(
                storyId = storyId,
                topLevelCommentIds = topLevelCommentIds.toList(),
                filteredUsers = filteredUsers.mapTo(linkedSetOf()) { it.lowercase() },
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 24
        const val DEFAULT_MAX_AGE_MILLIS = 5 * 60 * 1_000L
    }
}

data class PreloadedCommentsThread(
    val storyId: Int,
    val topLevelCommentIds: List<Int>,
    val filteredUsers: Set<String>,
    val response: String,
    val parsed: AlgoliaCommentsResponse,
    val loadedAtMillis: Long,
)
