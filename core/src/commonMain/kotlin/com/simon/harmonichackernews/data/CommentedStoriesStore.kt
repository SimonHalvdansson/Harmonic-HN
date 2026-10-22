package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaUserComment
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Stories the signed-in account has commented on, so story lists can mark them.
 *
 * Comments posted from the app are recorded immediately. Comments made on the website or another
 * device arrive through [sync], which asks Algolia only for comments newer than the last one seen.
 * The set belongs to a single account and is discarded when another account, or none, signs in.
 */
class CommentedStoriesStore(
    private val storage: KeyValueStore,
    private val algolia: AlgoliaRepository,
    /** Suspends until account storage has loaded, so startup is not mistaken for a sign-out. */
    private val currentUser: suspend () -> String?,
    private val nowMillis: () -> Long,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val syncMutex = Mutex()
    private var initialized = false
    private var owner: String? = null
    // Insertion ordered, oldest first, so trimming keeps the most recent stories.
    private val storyIds = LinkedHashSet<Int>()
    private var newestCommentEpochSeconds = 0
    private var lastSyncMillis = 0L
    private val mutableIds = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = mutableIds.asStateFlow()

    /** Marks [storyId] right after posting, before Algolia has indexed the new comment. */
    suspend fun record(storyId: Int): Boolean {
        if (storyId <= 0) return false
        val user = currentUser()
        return withContext(dispatcher) {
            mutex.withLock {
                bindLocked(user) && addLocked(listOf(storyId))
            }
        }
    }

    /**
     * Fetches comments made since the previous sync. Requests within [SYNC_INTERVAL_MILLIS] of a
     * successful sync are skipped unless [force] is set, as are requests while one is running.
     * Returns whether the set of commented stories changed.
     */
    suspend fun sync(force: Boolean = false): Boolean = withContext(dispatcher) {
        if (!syncMutex.tryLock()) return@withContext false
        try {
            val user = currentUser()?.trim()?.takeIf(String::isNotEmpty)
            val since = mutex.withLock {
                if (!bindLocked(user)) return@withContext false
                val syncedRecently = lastSyncMillis > 0L &&
                    nowMillis() - lastSyncMillis in 0 until SYNC_INTERVAL_MILLIS
                if (!force && syncedRecently) {
                    return@withContext false
                }
                newestCommentEpochSeconds.takeIf { it > 0 }
            }
            val comments = try {
                algolia.getUserComments(requireNotNull(user), since)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@withContext false
            }
            mutex.withLock {
                // The account changed while the request was in flight.
                if (owner != user) return@withLock false
                applySyncLocked(comments)
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private fun applySyncLocked(comments: List<AlgoliaUserComment>): Boolean {
        lastSyncMillis = nowMillis()
        comments.maxOfOrNull { it.createdAtEpochSeconds }?.let {
            newestCommentEpochSeconds = maxOf(newestCommentEpochSeconds, it)
        }
        // Algolia returns the newest comment first.
        val changed = addLocked(comments.asReversed().map { it.storyId })
        if (!changed) persistLocked()
        return changed
    }

    /** Returns whether a user is signed in; switching users discards the previous user's stories. */
    private fun bindLocked(user: String?): Boolean {
        initializeLocked()
        val normalized = user?.trim()?.takeIf(String::isNotEmpty)
        if (normalized != owner) {
            owner = normalized
            storyIds.clear()
            newestCommentEpochSeconds = 0
            lastSyncMillis = 0L
            persistLocked()
            publishLocked()
        }
        return normalized != null
    }

    private fun addLocked(newIds: List<Int>): Boolean {
        val before = storyIds.size
        var reordered = false
        newIds.filter { it > 0 }.forEach { id ->
            if (storyIds.remove(id)) reordered = true
            storyIds.add(id)
        }
        val added = storyIds.size != before
        while (storyIds.size > MAX_STORIES) storyIds.remove(storyIds.first())
        if (added || reordered) persistLocked()
        if (added) publishLocked()
        return added
    }

    private fun initializeLocked() {
        if (initialized) return
        initialized = true
        owner = storage.getString(OWNER_KEY)?.takeIf(String::isNotEmpty)
        storage.getString(STORY_IDS_KEY).orEmpty().split(',')
            .mapNotNullTo(storyIds) { it.toIntOrNull()?.takeIf { id -> id > 0 } }
        newestCommentEpochSeconds = storage.getInt(NEWEST_COMMENT_KEY, 0)
        lastSyncMillis = storage.getLong(LAST_SYNC_KEY, 0L)
        publishLocked()
    }

    private fun persistLocked() {
        storage.update {
            putString(OWNER_KEY, owner)
            putString(STORY_IDS_KEY, storyIds.joinToString(","))
            putInt(NEWEST_COMMENT_KEY, newestCommentEpochSeconds)
            putLong(LAST_SYNC_KEY, lastSyncMillis)
        }
    }

    private fun publishLocked() {
        mutableIds.value = storyIds.toSet()
    }

    companion object {
        const val OWNER_KEY = "com.simon.harmonichackernews.COMMENTED_STORIES_OWNER"
        const val STORY_IDS_KEY = "com.simon.harmonichackernews.COMMENTED_STORIES"
        const val NEWEST_COMMENT_KEY = "com.simon.harmonichackernews.COMMENTED_STORIES_NEWEST"
        const val LAST_SYNC_KEY = "com.simon.harmonichackernews.COMMENTED_STORIES_LAST_SYNC"
        const val SYNC_INTERVAL_MILLIS = 10L * 60L * 1_000L
        const val MAX_STORIES = 5_000
    }
}
