package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SavedItemSource {
    BOOKMARKS,
    FAVORITES,
    UPVOTED,
}

/** Existing Android preference keys, now owned by the platform-neutral saved-item domain. */
object SavedItemKeys {
    const val BOOKMARKS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_BOOKMARKS"
    const val FAVORITES = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITES"
    const val FAVORITE_COMMENTS =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS"
    const val UPVOTED = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED"
    const val UPVOTED_COMMENTS =
        "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS"
}

data class SavedItemsChange(
    val source: SavedItemSource,
    val itemIds: List<Int>,
    val commentIds: Set<Int>,
)

/**
 * Common persistence and mutation logic for bookmarks, favorites and upvotes.
 *
 * Time is supplied by callers for mutations so common code does not depend on a platform clock.
 */
class SavedItemsRepository(
    private val store: KeyValueStore,
) {
    private val mutationMutex = Mutex()
    private val mutableChanges = MutableSharedFlow<SavedItemsChange>(extraBufferCapacity = 32)

    /** Mutations made through this repository instance, after they have been persisted. */
    val changes: SharedFlow<SavedItemsChange> = mutableChanges.asSharedFlow()

    fun loadItems(
        source: SavedItemSource,
        sortedByCreated: Boolean = false,
    ): List<TimestampedItem> = SavedItemCodec.decode(
        store.getString(itemKey(source)),
        sortedByCreated,
    )

    fun loadItemsByDescendingId(source: SavedItemSource): List<TimestampedItem> =
        loadItems(source).sortedByDescending(TimestampedItem::id)

    fun contains(source: SavedItemSource, id: Int): Boolean =
        loadItems(source).any { it.id == id }

    fun saveItems(source: SavedItemSource, items: List<TimestampedItem>) {
        writeItems(source, items)
        publish(source)
    }

    fun saveIds(source: SavedItemSource, ids: List<Int>, createdAtMillis: Long) {
        saveItems(source, SavedItemCodec.fromIds(ids, createdAtMillis))
    }

    fun setMembership(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
        createdAtMillis: Long,
    ): Boolean {
        val current = loadItems(source)
        val updated = SavedItemCodec.setMembership(current, id, present, createdAtMillis)
        if (updated == current) return false
        saveItems(source, updated)
        return true
    }

    /** Serializes read-modify-write membership changes made by this repository instance. */
    suspend fun setMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        setMembership(source, id, present, createdAtMillis)
    }

    fun loadCommentIds(source: SavedItemSource): Set<Int> =
        commentKey(source)?.let(store::getStringSet)
            ?.mapNotNullTo(mutableSetOf(), String::toIntOrNull)
            .orEmpty()

    fun saveCommentIds(source: SavedItemSource, ids: Set<Int>) {
        writeCommentIds(source, ids)
        publish(source)
    }

    fun setCommentMembership(source: SavedItemSource, id: Int, present: Boolean): Boolean {
        val current = loadCommentIds(source)
        val updated = current.toMutableSet()
        val changed = if (present) updated.add(id) else updated.remove(id)
        if (changed) saveCommentIds(source, updated)
        return changed
    }

    /** Serializes read-modify-write comment changes made by this repository instance. */
    suspend fun setCommentMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
    ): Boolean = mutationMutex.withLock {
        setCommentMembership(source, id, present)
    }

    fun loadSnapshot(source: SavedItemSource): SavedItemSnapshot {
        require(source != SavedItemSource.BOOKMARKS)
        return SavedItemSnapshot(
            itemIds = loadItemsByDescendingId(source).map(TimestampedItem::id).distinct(),
            commentIds = loadCommentIds(source),
        )
    }

    fun saveSnapshot(
        source: SavedItemSource,
        snapshot: SavedItemSnapshot,
        createdAtMillis: Long,
    ) {
        require(source != SavedItemSource.BOOKMARKS)
        writeItems(source, SavedItemCodec.fromIds(snapshot.itemIds, createdAtMillis))
        writeCommentIds(source, snapshot.commentIds)
        publish(source)
    }

    /** Persists both halves of a saved-item snapshot without interleaving local mutations. */
    suspend fun saveSnapshotAtomic(
        source: SavedItemSource,
        snapshot: SavedItemSnapshot,
        createdAtMillis: Long,
    ) = mutationMutex.withLock {
        saveSnapshot(source, snapshot, createdAtMillis)
    }

    private fun publish(source: SavedItemSource) {
        mutableChanges.tryEmit(
            SavedItemsChange(
                source = source,
                itemIds = loadItemsByDescendingId(source).map(TimestampedItem::id).distinct(),
                commentIds = if (source == SavedItemSource.BOOKMARKS) {
                    emptySet()
                } else {
                    loadCommentIds(source)
                },
            ),
        )
    }

    private fun writeItems(source: SavedItemSource, items: List<TimestampedItem>) {
        store.putString(itemKey(source), SavedItemCodec.encode(items))
    }

    private fun writeCommentIds(source: SavedItemSource, ids: Set<Int>) {
        val key = requireNotNull(commentKey(source)) {
            "Bookmarks do not have a separate comment-id store"
        }
        store.putStringSet(key, ids.mapTo(mutableSetOf(), Int::toString))
    }

    private fun itemKey(source: SavedItemSource): String = when (source) {
        SavedItemSource.BOOKMARKS -> SavedItemKeys.BOOKMARKS
        SavedItemSource.FAVORITES -> SavedItemKeys.FAVORITES
        SavedItemSource.UPVOTED -> SavedItemKeys.UPVOTED
    }

    private fun commentKey(source: SavedItemSource): String? = when (source) {
        SavedItemSource.BOOKMARKS -> null
        SavedItemSource.FAVORITES -> SavedItemKeys.FAVORITE_COMMENTS
        SavedItemSource.UPVOTED -> SavedItemKeys.UPVOTED_COMMENTS
    }
}
