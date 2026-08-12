package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History
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
    suspend fun clearBookmarks()
}

data class HistoryStoreSnapshot(
    val histories: List<History> = emptyList(),
    val changeVersion: Long = 0L,
)

/** Suspend-friendly observable history storage for new shared feature code. */
interface ObservableHistoryStore : HistoryStore {
    val historyState: StateFlow<HistoryStoreSnapshot>
    suspend fun initializeHistory()
    suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean
    suspend fun removeHistory(id: Int): Boolean
    suspend fun clearHistory()
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

    override suspend fun clearBookmarks() = mutationMutex.withLock { clear() }

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

    override suspend fun initializeHistory() = mutationMutex.withLock { initialize() }

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
