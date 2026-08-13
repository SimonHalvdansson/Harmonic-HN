package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Suspend-friendly observable account storage for platform vault implementations. */
interface ObservableHackerNewsAccountRepository : HackerNewsAccountRepository {
    val accountState: StateFlow<HackerNewsAccount?>
    suspend fun saveAccount(account: HackerNewsAccount): Boolean
    suspend fun clearAccount(): Boolean
}

/** Suspend-friendly observable bookmark storage for new shared feature code. */
interface ObservableBookmarkStore : BookmarkStore {
    val bookmarkState: StateFlow<List<Bookmark>>
    suspend fun setBookmarked(id: Int, bookmarked: Boolean, createdAtMillis: Long): Boolean
}

data class HistoryStoreSnapshot(
    val histories: List<History> = emptyList(),
    val changeVersion: Long = 0L,
)

/** Suspend-friendly observable history storage for new shared feature code. */
interface ObservableHistoryStore : HistoryStore {
    val historyState: StateFlow<HistoryStoreSnapshot>
    suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean
    suspend fun removeHistory(id: Int): Boolean
    suspend fun clearHistory()
}

/**
 * Portable bookmark storage used by every platform that persists application data in a
 * [KeyValueStore]. Platforms choose the backing store; membership, ordering, timestamps, atomic
 * mutation and observation remain identical.
 */
class StoredBookmarkStore private constructor(
    private val repository: SavedItemsRepository,
    private val nowMillis: () -> Long,
) : ObservableBookmarkStore {
    constructor(store: KeyValueStore) : this(
        repository = SavedItemsRepository(store),
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )
    constructor(repository: SavedItemsRepository) : this(
        repository = repository,
        nowMillis = { Clock.System.now().toEpochMilliseconds() },
    )

    private val mutableBookmarkState = MutableStateFlow(loadPersisted())
    private val mutationMutex = Mutex()
    override val bookmarkState: StateFlow<List<Bookmark>> = mutableBookmarkState.asStateFlow()

    override fun load(): List<Bookmark> = loadPersisted()

    override fun add(id: Int) {
        if (repository.setMembership(SavedItemSource.BOOKMARKS, id, true, nowMillis())) publish()
    }

    override fun remove(id: Int) {
        if (repository.setMembership(SavedItemSource.BOOKMARKS, id, false, nowMillis())) publish()
    }

    override fun clear() {
        repository.saveItems(SavedItemSource.BOOKMARKS, emptyList())
        publish()
    }

    override suspend fun setBookmarked(
        id: Int,
        bookmarked: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        repository.setMembershipAtomic(
            source = SavedItemSource.BOOKMARKS,
            id = id,
            present = bookmarked,
            createdAtMillis = createdAtMillis,
        ).also { changed ->
            if (changed) publish()
        }
    }

    private fun loadPersisted(): List<Bookmark> = SavedItemCodec.toBookmarks(
        repository.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true),
    )

    private fun publish() {
        mutableBookmarkState.value = loadPersisted()
    }
}

object StoredHistoryKeys {
    const val HISTORIES = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_HISTORIES"
}

/**
 * Portable history ledger and persistence. Android, iOS, and desktop only provide a
 * [KeyValueStore], preventing platform implementations from drifting on ordering and versions.
 */
class StoredHistoryStore(
    private val store: KeyValueStore,
    private val storageKey: String = StoredHistoryKeys.HISTORIES,
) : ObservableHistoryStore {
    private val ledger = HistoryLedger()
    private var initialized = false
    private val mutationMutex = Mutex()
    private val mutableHistoryState = MutableStateFlow(snapshotFromStorage())
    override val historyState: StateFlow<HistoryStoreSnapshot> = mutableHistoryState.asStateFlow()

    override fun initialize() {
        ledger.initialize(store.getString(storageKey))
        initialized = true
        publish()
    }

    override fun load(): List<History> =
        HistoryLedger.decodeHistories(store.getString(storageKey), sorted = true)

    override fun record(id: Int, createdAtMillis: Long) {
        ensureInitialized()
        if (ledger.record(id, createdAtMillis)) persistAndPublish()
    }

    override fun remove(id: Int) {
        ensureInitialized()
        if (ledger.remove(id)) persistAndPublish()
    }

    override fun clear() {
        ledger.clear()
        initialized = true
        persistAndPublish()
    }

    override fun contains(id: Int): Boolean {
        ensureInitialized()
        return ledger.contains(id)
    }

    override val size: Int
        get() {
            ensureInitialized()
            return ledger.size
        }

    override val changeVersion: Long
        get() {
            ensureInitialized()
            return ledger.changeVersion
        }

    override suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean =
        mutationMutex.withLock {
            val previousVersion = changeVersion
            record(id, createdAtMillis)
            changeVersion != previousVersion
        }

    override suspend fun removeHistory(id: Int): Boolean = mutationMutex.withLock {
        val previousVersion = changeVersion
        remove(id)
        changeVersion != previousVersion
    }

    override suspend fun clearHistory() = mutationMutex.withLock { clear() }

    private fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private fun persistAndPublish() {
        store.putString(storageKey, ledger.serialize())
        publish()
    }

    private fun snapshotFromStorage() = HistoryStoreSnapshot(
        histories = HistoryLedger.decodeHistories(store.getString(storageKey), sorted = true),
        changeVersion = if (initialized) ledger.changeVersion else 0L,
    )

    private fun publish() {
        mutableHistoryState.value = HistoryStoreSnapshot(ledger.load(), ledger.changeVersion)
    }
}

/** Compatibility adapter for hosts that have not yet replaced their synchronous bookmark port. */
class LegacyObservableBookmarkStoreAdapter(
    private val legacy: BookmarkStore,
) : ObservableBookmarkStore {
    private val mutationMutex = Mutex()
    private val mutableBookmarkState = MutableStateFlow(legacy.load())
    override val bookmarkState: StateFlow<List<Bookmark>> = mutableBookmarkState.asStateFlow()

    override fun load(): List<Bookmark> = legacy.load()

    override fun add(id: Int) {
        legacy.add(id)
        publish()
    }

    override fun remove(id: Int) {
        legacy.remove(id)
        publish()
    }

    override fun clear() {
        legacy.clear()
        publish()
    }

    override suspend fun setBookmarked(
        id: Int,
        bookmarked: Boolean,
        createdAtMillis: Long,
    ): Boolean = mutationMutex.withLock {
        val wasBookmarked = legacy.load().any { it.id == id }
        if (bookmarked) legacy.add(id) else legacy.remove(id)
        publish()
        wasBookmarked != bookmarked
    }

    private fun publish() {
        mutableBookmarkState.value = legacy.load()
    }
}

/** Compatibility adapter for hosts that have not yet replaced their synchronous history port. */
class LegacyObservableHistoryStoreAdapter(
    private val legacy: HistoryStore,
) : ObservableHistoryStore {
    private val mutationMutex = Mutex()
    private val mutableHistoryState = MutableStateFlow(snapshot())
    override val historyState: StateFlow<HistoryStoreSnapshot> = mutableHistoryState.asStateFlow()

    override fun initialize() {
        legacy.initialize()
        publish()
    }

    override fun load(): List<History> = legacy.load()

    override fun record(id: Int, createdAtMillis: Long) {
        legacy.record(id, createdAtMillis)
        publish()
    }

    override fun remove(id: Int) {
        legacy.remove(id)
        publish()
    }

    override fun clear() {
        legacy.clear()
        publish()
    }

    override fun contains(id: Int): Boolean = legacy.contains(id)
    override val size: Int get() = legacy.size
    override val changeVersion: Long get() = legacy.changeVersion

    override suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean =
        mutationMutex.withLock {
            val previousVersion = legacy.changeVersion
            record(id, createdAtMillis)
            legacy.changeVersion != previousVersion
        }

    override suspend fun removeHistory(id: Int): Boolean = mutationMutex.withLock {
        val previousVersion = legacy.changeVersion
        remove(id)
        legacy.changeVersion != previousVersion
    }

    override suspend fun clearHistory() = mutationMutex.withLock { clear() }

    private fun snapshot() = HistoryStoreSnapshot(legacy.load(), legacy.changeVersion)

    private fun publish() {
        mutableHistoryState.value = snapshot()
    }
}
