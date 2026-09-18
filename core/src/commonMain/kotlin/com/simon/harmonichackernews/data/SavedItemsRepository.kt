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
    // Dispatch uses the login session; settlement uses accountName and mutation revisions so
    // an already dispatched result can still settle its original account after a login switch.
    internal val accountRevision: Long,
    internal val accountName: String?,
) {
    override fun equals(other: Any?): Boolean = other is SavedItemMutationToken &&
        source == other.source && itemId == other.itemId && isComment == other.isComment &&
        itemSourceEpoch == other.itemSourceEpoch &&
        commentSourceEpoch == other.commentSourceEpoch && itemRevision == other.itemRevision &&
        accountName == other.accountName

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + itemId
        result = 31 * result + isComment.hashCode()
        result = 31 * result + itemSourceEpoch.hashCode()
        result = 31 * result + commentSourceEpoch.hashCode()
        result = 31 * result + itemRevision.hashCode()
        return 31 * result + accountName.hashCode()
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
    private val itemIdsCache = mutableMapOf<SourceAccount, Set<Int>>()
    private val commentIdsCache = mutableMapOf<SourceAccount, Set<Int>>()
    private val sourceEpochs = mutableMapOf<SourceKind, Long>()
    private val itemMutationRevisions = mutableMapOf<MembershipKey, Long>()
    private val actionLocksGuard = Mutex()
    private val actionLocks = mutableMapOf<MembershipKey, ActionLock>()
    private var accountName: (() -> String?)? = null
    private var activeAccountName: String? = null
    private var accountSession: (() -> Any?)? = null
    private var activeAccountSession: Any? = null
    private var accountRevision = 0L

    /**
     * Remote membership belongs to an HN account; local bookmarks remain device-owned.
     * [accountSession] must identify the current login state when supplied. Its reference changes
     * invalidate pending dispatches even when an intermediate account change was not observed.
     */
    fun bindAccountScope(
        accountSession: (() -> Any?)? = null,
        accountName: () -> String?,
    ) {
        itemCache.keys.removeAll { it.source != SavedItemSource.BOOKMARKS }
        itemIdsCache.keys.removeAll { it.source != SavedItemSource.BOOKMARKS }
        commentIdsCache.clear()
        sourceEpochs.keys.removeAll { it.source != SavedItemSource.BOOKMARKS }
        itemMutationRevisions.keys.removeAll { it.source != SavedItemSource.BOOKMARKS }
        this.accountName = accountName
        this.accountSession = accountSession
        changeAccountScope(accountName(), accountSession?.invoke())
    }

    val currentAccountRevision: Long
        get() {
            refreshAccountScope()
            return accountRevision
        }

    val currentAccountName: String?
        get() {
            refreshAccountScope()
            return activeAccountName
        }

    fun refreshAccountScope() {
        val provider = accountName ?: return
        val name = provider()
        val session = accountSession?.invoke()
        if (name != activeAccountName || session !== activeAccountSession) changeAccountScope(name, session)
    }

    private fun changeAccountScope(name: String?, session: Any?) {
        activeAccountName = name
        activeAccountSession = session
        accountRevision++
        publish(SavedItemSource.FAVORITES)
        publish(SavedItemSource.UPVOTED)
    }

    /** Mutations made through this repository instance, after they have been persisted. */
    val changes: SharedFlow<SavedItemsChange> = mutableChanges.asSharedFlow()

    fun loadItems(
        source: SavedItemSource,
        sortedByCreated: Boolean = false,
    ): List<TimestampedItem> {
        refreshAccountScope()
        return loadItemsForAccount(source, sortedByCreated, scopeFor(source))
    }

    private fun loadItemsForAccount(
        source: SavedItemSource,
        sortedByCreated: Boolean,
        account: String?,
    ): List<TimestampedItem> {
        val key = ItemCacheKey(source, sortedByCreated, account)
        return itemCache[key] ?: SavedItemCodec.decode(
            store.getString(itemKey(source, account)),
            sortedByCreated,
        ).let { normalizeItems(source, it) }.also { items ->
            itemCache[key] = items
            itemIdsCache.getOrPut(SourceAccount(source, account)) {
                items.mapTo(mutableSetOf(), TimestampedItem::id)
            }
        }
    }

    fun loadItemsByDescendingId(source: SavedItemSource): List<TimestampedItem> =
        loadItems(source).sortedByDescending(TimestampedItem::id)

    fun contains(source: SavedItemSource, id: Int): Boolean =
        id in loadItemIds(source)

    fun saveItems(source: SavedItemSource, items: List<TimestampedItem>) {
        refreshAccountScope()
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
        refreshAccountScope()
        return setMembershipForAccount(source, id, present, createdAtMillis, scopeFor(source))
    }

    private fun setMembershipForAccount(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
        createdAtMillis: Long,
        account: String?,
    ): Boolean {
        val current = loadItemsForAccount(source, false, account)
        val updated = SavedItemCodec.setMembership(current, id, present, createdAtMillis)
        if (updated == current) return false
        writeItems(source, updated, account)
        publish(source, account)
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

    fun loadCommentIds(source: SavedItemSource): Set<Int> {
        refreshAccountScope()
        return loadCommentIdsForAccount(source, scopeFor(source))
    }

    private fun loadCommentIdsForAccount(source: SavedItemSource, account: String?): Set<Int> =
        commentIdsCache.getOrPut(SourceAccount(source, account)) {
            commentKey(source, account)?.let(store::getStringSet)
                ?.mapNotNullTo(mutableSetOf(), String::toIntOrNull)
                .orEmpty()
        }

    fun saveCommentIds(source: SavedItemSource, ids: Set<Int>) {
        refreshAccountScope()
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

    suspend fun setCommentMembershipAtomic(
        source: SavedItemSource,
        id: Int,
        present: Boolean,
    ): Boolean = mutationMutex.withLock {
        setCommentMembership(source, id, present).also {
            advanceItemRevision(source, id, isComment = true)
        }
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
        refreshAccountScope()
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
        refreshAccountScope()
        val key = MembershipKey(source, itemId, scopeFor(source))
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

    suspend fun saveSnapshotIfAccountCurrent(
        source: SavedItemSource,
        snapshot: SavedItemSnapshot,
        createdAtMillis: Long,
        expectedAccountRevision: Long,
    ): Boolean = mutationMutex.withLock {
        if (currentAccountRevision != expectedAccountRevision) return@withLock false
        saveSnapshot(source, snapshot, createdAtMillis)
        advanceSourceEpoch(source, isComment = false)
        advanceSourceEpoch(source, isComment = true)
        true
    }

    suspend fun restoreMembershipIfCurrentAtomic(
        token: SavedItemMutationToken,
        previousItemPresent: Boolean,
        previousCommentPresent: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        refreshAccountScope()
        if (currentToken(token.source, token.itemId, token.isComment, token.accountName) != token) {
            return@withLock false
        }
        if (token.isComment) {
            setClassifiedMembership(
                token.source,
                token.itemId,
                previousItemPresent,
                previousCommentPresent,
                createdAtMillis,
                account = token.accountName,
            )
        } else {
            setMembershipForAccount(
                token.source, token.itemId, previousItemPresent, createdAtMillis, token.accountName,
            )
        }
        advanceItemRevision(token.source, token.itemId, token.isComment, token.accountName)
        true
    }

    suspend fun reconcileMembershipIfNoNewerMutationAtomic(
        token: SavedItemMutationToken,
        present: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        refreshAccountScope()
        val current = currentToken(token.source, token.itemId, token.isComment, token.accountName)
        if (current == token) return@withLock true
        if (current.itemRevision != token.itemRevision) return@withLock false
        if (token.isComment) {
            setClassifiedMembership(
                token.source,
                token.itemId,
                itemPresent = present,
                commentPresent = present,
                createdAtMillis = createdAtMillis,
                account = token.accountName,
            )
        } else {
            setMembershipForAccount(
                token.source, token.itemId, present, createdAtMillis, token.accountName,
            )
        }
        advanceItemRevision(token.source, token.itemId, token.isComment, token.accountName)
        true
    }

    private fun publish(source: SavedItemSource, account: String? = scopeFor(source)) {
        refreshAccountScope()
        if (account != scopeFor(source)) return
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
        refreshAccountScope()
        itemIdsCache[sourceAccount(source)]?.let { return it }
        loadItems(source)
        return itemIdsCache.getValue(sourceAccount(source))
    }

    private fun writeItems(
        source: SavedItemSource,
        items: List<TimestampedItem>,
        account: String? = scopeFor(source),
    ) {
        val normalized = normalizeItems(source, items)
        store.putString(itemKey(source, account), SavedItemCodec.encode(normalized))
        cacheItems(source, normalized, account)
    }

    private fun normalizeItems(source: SavedItemSource, items: List<TimestampedItem>): List<TimestampedItem> =
        if (source == SavedItemSource.BOOKMARKS) SavedItemCodec.deduplicate(items) else items

    private fun cacheItems(
        source: SavedItemSource,
        items: List<TimestampedItem>,
        account: String? = scopeFor(source),
    ) {
        val cachedItems = items.toList()
        itemCache[ItemCacheKey(source, sortedByCreated = false, account)] = cachedItems
        itemCache[ItemCacheKey(source, sortedByCreated = true, account)] =
            cachedItems.sortedByDescending(TimestampedItem::created)
        itemIdsCache[SourceAccount(source, account)] = cachedItems.mapTo(mutableSetOf(), TimestampedItem::id)
    }

    private fun writeCommentIds(source: SavedItemSource, ids: Set<Int>) {
        val key = requireNotNull(commentKey(source)) {
            "Bookmarks do not have a separate comment-id store"
        }
        val cachedIds = ids.toSet()
        store.putStringSet(key, cachedIds.mapTo(mutableSetOf(), Int::toString))
        cacheCommentIds(source, cachedIds)
    }

    private fun cacheCommentIds(
        source: SavedItemSource,
        ids: Set<Int>,
        account: String? = scopeFor(source),
    ) {
        commentIdsCache[SourceAccount(source, account)] = ids.toSet()
    }

    private fun setClassifiedMembership(
        source: SavedItemSource,
        id: Int,
        itemPresent: Boolean,
        commentPresent: Boolean,
        createdAtMillis: Long,
        account: String? = scopeFor(source),
    ) {
        val commentKey = requireNotNull(commentKey(source, account)) {
            "Bookmarks do not have a separate comment-id store"
        }
        val currentItems = loadItemsForAccount(source, false, account)
        val currentCommentIds = loadCommentIdsForAccount(source, account)
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
            putString(itemKey(source, account), SavedItemCodec.encode(updatedItems))
            putStringSet(commentKey, updatedCommentIds.mapTo(mutableSetOf(), Int::toString))
        }
        cacheItems(source, updatedItems, account)
        cacheCommentIds(source, updatedCommentIds, account)
        publish(source, account)
    }

    private fun itemKey(source: SavedItemSource, account: String? = scopeFor(source)): String = when (source) {
        SavedItemSource.BOOKMARKS -> SavedItemKeys.BOOKMARKS
        SavedItemSource.FAVORITES -> accountKey(SavedItemKeys.FAVORITES, account)
        SavedItemSource.UPVOTED -> accountKey(SavedItemKeys.UPVOTED, account)
    }

    private fun commentKey(source: SavedItemSource, account: String? = scopeFor(source)): String? = when (source) {
        SavedItemSource.BOOKMARKS -> null
        SavedItemSource.FAVORITES -> accountKey(SavedItemKeys.FAVORITE_COMMENTS, account)
        SavedItemSource.UPVOTED -> accountKey(SavedItemKeys.UPVOTED_COMMENTS, account)
    }

    private fun accountKey(legacyKey: String, account: String?): String {
        if (accountName == null) return legacyKey
        // Old global caches cannot reliably be assigned to an account. Leave them untouched;
        // each account rebuilds its own remote membership on the next HN synchronization.
        val identity = account?.let { "${it.length}:$it" } ?: "signed-out"
        return "$legacyKey.account:$identity"
    }

    private fun advanceSourceEpoch(source: SavedItemSource, isComment: Boolean) {
        val kind = SourceKind(source, isComment, scopeFor(source))
        sourceEpochs[kind] = (sourceEpochs[kind] ?: 0L) + 1L
    }

    private fun advanceItemRevision(
        source: SavedItemSource,
        itemId: Int,
        isComment: Boolean,
        account: String? = scopeFor(source),
    ): SavedItemMutationToken {
        val key = MembershipKey(source, itemId, account)
        val revision = (itemMutationRevisions[key] ?: 0L) + 1L
        itemMutationRevisions[key] = revision
        return SavedItemMutationToken(
            source = source,
            itemId = itemId,
            isComment = isComment,
            itemSourceEpoch = sourceEpochs[SourceKind(source, isComment = false, account)] ?: 0L,
            commentSourceEpoch = sourceEpochs[SourceKind(source, isComment = true, account)] ?: 0L,
            itemRevision = revision,
            accountRevision = if (source == SavedItemSource.BOOKMARKS) 0L else accountRevision,
            accountName = account,
        )
    }

    private fun currentToken(
        source: SavedItemSource,
        itemId: Int,
        isComment: Boolean,
        account: String? = scopeFor(source),
    ): SavedItemMutationToken {
        refreshAccountScope()
        return SavedItemMutationToken(
            source = source,
            itemId = itemId,
            isComment = isComment,
            itemSourceEpoch = sourceEpochs[SourceKind(source, false, account)] ?: 0L,
            commentSourceEpoch = sourceEpochs[SourceKind(source, true, account)] ?: 0L,
            itemRevision = itemMutationRevisions[MembershipKey(source, itemId, account)] ?: 0L,
            accountRevision = if (source == SavedItemSource.BOOKMARKS) 0L else accountRevision,
            accountName = account,
        )
    }

    private fun scopeFor(source: SavedItemSource): String? =
        activeAccountName.takeUnless { source == SavedItemSource.BOOKMARKS }

    private fun sourceAccount(source: SavedItemSource) = SourceAccount(source, scopeFor(source))

    private data class SourceAccount(val source: SavedItemSource, val account: String?)

    private data class ItemCacheKey(
        val source: SavedItemSource,
        val sortedByCreated: Boolean,
        val account: String?,
    )

    private data class SourceKind(
        val source: SavedItemSource,
        val isComment: Boolean,
        val account: String?,
    )

    private data class MembershipKey(
        val source: SavedItemSource,
        val itemId: Int,
        val account: String?,
    )

    private class ActionLock(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}
