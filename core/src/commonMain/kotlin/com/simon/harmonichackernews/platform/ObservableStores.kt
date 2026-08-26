package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HackerNewsAccountState {
    data object Loading : HackerNewsAccountState
    data object LoggedOut : HackerNewsAccountState
    data class LoggedIn(val account: HackerNewsAccount) : HackerNewsAccountState
}

val HackerNewsAccountState.accountOrNull: HackerNewsAccount?
    get() = (this as? HackerNewsAccountState.LoggedIn)?.account

/**
 * Suspend-friendly observable account storage for platform vault implementations.
 *
 * Secure storage is never exposed through a synchronous read. UI code consumes [accountState],
 * while operations that require credentials can suspend through [awaitAccount] until the one-time
 * background initialization has completed.
 */
interface ObservableHackerNewsAccountRepository {
    val accountState: StateFlow<HackerNewsAccountState>
    val currentAccount: HackerNewsAccount?
        get() = accountState.value.accountOrNull

    suspend fun awaitAccount(): HackerNewsAccount? =
        accountState.first { it !is HackerNewsAccountState.Loading }.accountOrNull

    suspend fun saveAccount(account: HackerNewsAccount): Boolean
    suspend fun clearAccount(): Boolean
    fun close() = Unit
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
