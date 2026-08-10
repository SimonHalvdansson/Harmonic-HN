package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.KeyValueStore

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

/**
 * Common persistence and mutation logic for bookmarks, favorites and upvotes.
 *
 * Time is supplied by callers for mutations so common code does not depend on a platform clock.
 */
class SavedItemsRepository(
    private val store: KeyValueStore,
) {
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
        store.putString(itemKey(source), SavedItemCodec.encode(items))
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

    fun loadCommentIds(source: SavedItemSource): Set<Int> =
        commentKey(source)?.let(store::getStringSet)
            ?.mapNotNullTo(mutableSetOf(), String::toIntOrNull)
            .orEmpty()

    fun saveCommentIds(source: SavedItemSource, ids: Set<Int>) {
        val key = requireNotNull(commentKey(source)) {
            "Bookmarks do not have a separate comment-id store"
        }
        store.putStringSet(key, ids.mapTo(mutableSetOf(), Int::toString))
    }

    fun setCommentMembership(source: SavedItemSource, id: Int, present: Boolean): Boolean {
        val current = loadCommentIds(source)
        val updated = current.toMutableSet()
        val changed = if (present) updated.add(id) else updated.remove(id)
        if (changed) saveCommentIds(source, updated)
        return changed
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
        saveIds(source, snapshot.itemIds, createdAtMillis)
        saveCommentIds(source, snapshot.commentIds)
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
