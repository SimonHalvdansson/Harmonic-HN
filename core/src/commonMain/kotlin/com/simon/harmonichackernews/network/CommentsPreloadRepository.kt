package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
    private val storeResponse: suspend (storyId: Int, response: String) -> Unit = { _, _ -> },
    private val nowMillis: () -> Long,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<PreloadKey, PreparedCommentsThread>()
    private val inFlight = mutableMapOf<PreloadKey, CompletableDeferred<PreparedCommentsThread?>>()

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
        return prepare(key) {
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
        } as? PreloadedCommentsThread
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
            entries[key]?.let { return it }
            inFlight[key] ?: CompletableDeferred<PreparedCommentsThread?>().also {
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
            entries.remove(key)?.let { return it }
            inFlight[key].takeIf { awaitInFlight }
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
    }
}

sealed interface PreparedCommentsThread {
    val storyId: Int
    val topLevelCommentIds: List<Int>
    val filteredUsers: Set<String>
    val loadedAtMillis: Long
}

data class PreloadedCommentsThread(
    override val storyId: Int,
    override val topLevelCommentIds: List<Int>,
    override val filteredUsers: Set<String>,
    val response: String,
    val parsed: AlgoliaCommentsResponse,
    override val loadedAtMillis: Long,
) : PreparedCommentsThread

data class PreloadedOfficialCommentsThread(
    override val storyId: Int,
    override val topLevelCommentIds: List<Int>,
    override val filteredUsers: Set<String>,
    val story: Story,
    val comments: MutableList<Comment>,
    val usedAsFallback: Boolean,
    override val loadedAtMillis: Long,
) : PreparedCommentsThread
