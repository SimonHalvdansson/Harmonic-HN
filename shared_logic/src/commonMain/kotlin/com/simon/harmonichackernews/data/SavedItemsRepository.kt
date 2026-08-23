package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

data class SavedItemMembershipUpdate(
    val previousPresent: Boolean,
    val currentPresent: Boolean,
    val token: SavedItemMutationToken,
    val previousItemPresent: Boolean = previousPresent,
    val previousCommentPresent: Boolean = false,
) {
    val changed: Boolean get() = previousPresent != currentPresent
}

class SavedItemMutationToken internal constructor(
    internal val source: SavedItemSource,
    internal val itemId: Int,
    internal val isComment: Boolean,
    internal val itemSourceEpoch: Long,
    internal val commentSourceEpoch: Long,
    internal val itemRevision: Long,
) {
    override fun equals(other: Any?): Boolean = other is SavedItemMutationToken &&
        source == other.source && itemId == other.itemId && isComment == other.isComment &&
        itemSourceEpoch == other.itemSourceEpoch &&
        commentSourceEpoch == other.commentSourceEpoch && itemRevision == other.itemRevision

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + itemId
        result = 31 * result + isComment.hashCode()
        result = 31 * result + itemSourceEpoch.hashCode()
        result = 31 * result + commentSourceEpoch.hashCode()
        return 31 * result + itemRevision.hashCode()
    }
}

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
    private val itemCache = mutableMapOf<ItemCacheKey, List<TimestampedItem>>()
    private val itemIdsCache = mutableMapOf<SavedItemSource, Set<Int>>()
    private val commentIdsCache = mutableMapOf<SavedItemSource, Set<Int>>()
    private val sourceEpochs = mutableMapOf<SourceKind, Long>()
    private val itemMutationRevisions = mutableMapOf<MembershipKey, Long>()
    private val actionLocksGuard = Mutex()
    private val actionLocks = mutableMapOf<MembershipKey, ActionLock>()

    /** Mutations made through this repository instance, after they have been persisted. */
    val changes: SharedFlow<SavedItemsChange> = mutableChanges.asSharedFlow()

    fun loadItems(
        source: SavedItemSource,
        sortedByCreated: Boolean = false,
    ): List<TimestampedItem> {
        val key = ItemCacheKey(source, sortedByCreated)
        return itemCache[key] ?: SavedItemCodec.decode(
            store.getString(itemKey(source)),
            sortedByCreated,
        ).also { items ->
            itemCache[key] = items
            itemIdsCache.getOrPut(source) {
                items.mapTo(mutableSetOf(), TimestampedItem::id)
            }
        }
    }

    fun loadItemsByDescendingId(source: SavedItemSource): List<TimestampedItem> =
        loadItems(source).sortedByDescending(TimestampedItem::id)

    fun contains(source: SavedItemSource, id: Int): Boolean =
        id in loadItemIds(source)

    fun saveItems(source: SavedItemSource, items: List<TimestampedItem>) {
        writeItems(source, items)
        publish(source)
    }

    suspend fun saveItemsAtomic(source: SavedItemSource, items: List<TimestampedItem>) =
        mutationMutex.withLock {
            saveItems(source, items)
            advanceSourceEpoch(source, isComment = false)
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
        setMembership(source, id, present, createdAtMillis).also {
            advanceItemRevision(source, id, isComment = false)
        }
    }

    suspend fun updateMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
        createdAtMillis: Long,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previous = contains(source, id)
        setMembership(source, id, present, createdAtMillis)
        SavedItemMembershipUpdate(
            previous,
            present,
            advanceItemRevision(source, id, isComment = false),
        )
    }

    suspend fun toggleMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        createdAtMillis: Long,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previous = contains(source, id)
        setMembership(source, id, !previous, createdAtMillis)
        SavedItemMembershipUpdate(
            previous,
            !previous,
            advanceItemRevision(source, id, isComment = false),
        )
    }

    fun loadCommentIds(source: SavedItemSource): Set<Int> = commentIdsCache.getOrPut(source) {
        commentKey(source)?.let(store::getStringSet)
            ?.mapNotNullTo(mutableSetOf(), String::toIntOrNull)
            .orEmpty()
    }

    fun saveCommentIds(source: SavedItemSource, ids: Set<Int>) {
        writeCommentIds(source, ids)
        publish(source)
    }

    suspend fun saveCommentIdsAtomic(source: SavedItemSource, ids: Set<Int>) =
        mutationMutex.withLock {
            saveCommentIds(source, ids)
            advanceSourceEpoch(source, isComment = true)
        }

    fun setCommentMembership(source: SavedItemSource, id: Int, present: Boolean): Boolean {
        val current = loadCommentIds(source)
        val updated = current.toMutableSet()
        val changed = if (present) updated.add(id) else updated.remove(id)
        if (changed) saveCommentIds(source, updated)
        return changed
    }

    suspend fun setCommentMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
    ): Boolean = mutationMutex.withLock {
        setCommentMembership(source, id, present).also {
            advanceItemRevision(source, id, isComment = true)
        }
    }

    suspend fun updateCommentMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previous = id in loadCommentIds(source)
        setCommentMembership(source, id, present)
        SavedItemMembershipUpdate(
            previous,
            present,
            advanceItemRevision(source, id, isComment = true),
        )
    }

    suspend fun toggleCommentMembershipAtomic(
        source: SavedItemSource,
        id: Int,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previous = id in loadCommentIds(source)
        setCommentMembership(source, id, !previous)
        SavedItemMembershipUpdate(
            previous,
            !previous,
            advanceItemRevision(source, id, isComment = true),
        )
    }

    suspend fun updateClassifiedMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
        createdAtMillis: Long,
        previousFromComment: Boolean,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previousItem = contains(source, id)
        val previousComment = id in loadCommentIds(source)
        setClassifiedMembership(source, id, present, present, createdAtMillis)
        SavedItemMembershipUpdate(
            previousPresent = if (previousFromComment) previousComment else previousItem,
            currentPresent = present,
            token = advanceItemRevision(source, id, isComment = true),
            previousItemPresent = previousItem,
            previousCommentPresent = previousComment,
        )
    }

    suspend fun toggleClassifiedMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        createdAtMillis: Long,
        previousFromComment: Boolean,
    ): SavedItemMembershipUpdate = mutationMutex.withLock {
        val previousItem = contains(source, id)
        val previousComment = id in loadCommentIds(source)
        val previous = if (previousFromComment) previousComment else previousItem
        val present = !previous
        setClassifiedMembership(source, id, present, present, createdAtMillis)
        SavedItemMembershipUpdate(
            previousPresent = previous,
            currentPresent = present,
            token = advanceItemRevision(source, id, isComment = true),
            previousItemPresent = previousItem,
            previousCommentPresent = previousComment,
        )
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
        val items = SavedItemCodec.fromIds(snapshot.itemIds, createdAtMillis)
        val commentKey = requireNotNull(commentKey(source))
        store.update {
            putString(itemKey(source), SavedItemCodec.encode(items))
            putStringSet(commentKey, snapshot.commentIds.mapTo(mutableSetOf(), Int::toString))
        }
        cacheItems(source, items)
        cacheCommentIds(source, snapshot.commentIds)
        publish(source)
    }

    /** Persists both halves of a saved-item snapshot without interleaving local mutations. */
    suspend fun saveSnapshotAtomic(
        source: SavedItemSource,
        snapshot: SavedItemSnapshot,
        createdAtMillis: Long,
    ) = mutationMutex.withLock {
        saveSnapshot(source, snapshot, createdAtMillis)
        advanceSourceEpoch(source, isComment = false)
        advanceSourceEpoch(source, isComment = true)
    }

    internal suspend fun <T> withSerializedAction(
        source: SavedItemSource,
        itemId: Int,
        block: suspend () -> T,
    ): T {
        val key = MembershipKey(source, itemId)
        val entry = actionLocksGuard.withLock {
            actionLocks.getOrPut(key, ::ActionLock).also { it.users++ }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            withContext(NonCancellable) {
                actionLocksGuard.withLock {
                    entry.users--
                    if (entry.users == 0 && actionLocks[key] === entry) actionLocks.remove(key)
                }
            }
        }
    }

    suspend fun restoreMembershipIfCurrentAtomic(
        token: SavedItemMutationToken,
        previousItemPresent: Boolean,
        previousCommentPresent: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        if (currentToken(token.source, token.itemId, token.isComment) != token) {
            return@withLock false
        }
        if (token.isComment) {
            setClassifiedMembership(
                token.source,
                token.itemId,
                previousItemPresent,
                previousCommentPresent,
                createdAtMillis,
            )
        } else {
            setMembership(token.source, token.itemId, previousItemPresent, createdAtMillis)
        }
        advanceItemRevision(token.source, token.itemId, token.isComment)
        true
    }

    suspend fun reconcileMembershipIfNoNewerMutationAtomic(
        token: SavedItemMutationToken,
        present: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        val current = currentToken(token.source, token.itemId, token.isComment)
        if (current == token) return@withLock true
        if (current.itemRevision != token.itemRevision) return@withLock false
        if (token.isComment) {
            setClassifiedMembership(
                token.source,
                token.itemId,
                itemPresent = present,
                commentPresent = present,
                createdAtMillis = createdAtMillis,
            )
        } else {
            setMembership(token.source, token.itemId, present, createdAtMillis)
        }
        advanceItemRevision(token.source, token.itemId, token.isComment)
        true
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

    private fun loadItemIds(source: SavedItemSource): Set<Int> {
        itemIdsCache[source]?.let { return it }
        loadItems(source)
        return itemIdsCache.getValue(source)
    }

    private fun writeItems(source: SavedItemSource, items: List<TimestampedItem>) {
        store.putString(itemKey(source), SavedItemCodec.encode(items))
        cacheItems(source, items)
    }

    private fun cacheItems(source: SavedItemSource, items: List<TimestampedItem>) {
        val cachedItems = items.toList()
        itemCache[ItemCacheKey(source, sortedByCreated = false)] = cachedItems
        itemCache[ItemCacheKey(source, sortedByCreated = true)] =
            cachedItems.sortedByDescending(TimestampedItem::created)
        itemIdsCache[source] = cachedItems.mapTo(mutableSetOf(), TimestampedItem::id)
    }

    private fun writeCommentIds(source: SavedItemSource, ids: Set<Int>) {
        val key = requireNotNull(commentKey(source)) {
            "Bookmarks do not have a separate comment-id store"
        }
        val cachedIds = ids.toSet()
        store.putStringSet(key, cachedIds.mapTo(mutableSetOf(), Int::toString))
        cacheCommentIds(source, cachedIds)
    }

    private fun cacheCommentIds(source: SavedItemSource, ids: Set<Int>) {
        commentIdsCache[source] = ids.toSet()
    }

    private fun setClassifiedMembership(
        source: SavedItemSource,
        id: Int,
        itemPresent: Boolean,
        commentPresent: Boolean,
        createdAtMillis: Long,
    ) {
        val commentKey = requireNotNull(commentKey(source)) {
            "Bookmarks do not have a separate comment-id store"
        }
        val currentItems = loadItems(source)
        val currentCommentIds = loadCommentIds(source)
        val updatedItems = SavedItemCodec.setMembership(
            currentItems,
            id,
            itemPresent,
            createdAtMillis,
        )
        val updatedCommentIds = currentCommentIds.toMutableSet().apply {
            if (commentPresent) add(id) else remove(id)
        }
        if (updatedItems == currentItems && updatedCommentIds == currentCommentIds) return
        store.update {
            putString(itemKey(source), SavedItemCodec.encode(updatedItems))
            putStringSet(commentKey, updatedCommentIds.mapTo(mutableSetOf(), Int::toString))
        }
        cacheItems(source, updatedItems)
        cacheCommentIds(source, updatedCommentIds)
        publish(source)
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

    private fun advanceSourceEpoch(source: SavedItemSource, isComment: Boolean) {
        val kind = SourceKind(source, isComment)
        sourceEpochs[kind] = (sourceEpochs[kind] ?: 0L) + 1L
    }

    private fun advanceItemRevision(
        source: SavedItemSource,
        itemId: Int,
        isComment: Boolean,
    ): SavedItemMutationToken {
        val key = MembershipKey(source, itemId)
        val revision = (itemMutationRevisions[key] ?: 0L) + 1L
        itemMutationRevisions[key] = revision
        return SavedItemMutationToken(
            source = source,
            itemId = itemId,
            isComment = isComment,
            itemSourceEpoch = sourceEpochs[SourceKind(source, isComment = false)] ?: 0L,
            commentSourceEpoch = sourceEpochs[SourceKind(source, isComment = true)] ?: 0L,
            itemRevision = revision,
        )
    }

    private fun currentToken(
        source: SavedItemSource,
        itemId: Int,
        isComment: Boolean,
    ): SavedItemMutationToken = SavedItemMutationToken(
        source = source,
        itemId = itemId,
        isComment = isComment,
        itemSourceEpoch = sourceEpochs[SourceKind(source, isComment = false)] ?: 0L,
        commentSourceEpoch = sourceEpochs[SourceKind(source, isComment = true)] ?: 0L,
        itemRevision = itemMutationRevisions[MembershipKey(source, itemId)] ?: 0L,
    )

    private data class ItemCacheKey(
        val source: SavedItemSource,
        val sortedByCreated: Boolean,
    )

    private data class SourceKind(val source: SavedItemSource, val isComment: Boolean)

    private data class MembershipKey(
        val source: SavedItemSource,
        val itemId: Int,
    )

    private class ActionLock(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}
