package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    fun close() = Unit
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
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ObservableHistoryStore {
    private val ledger = HistoryLedger()
    private val mutationMutex = Mutex()
    private val initialization = CompletableDeferred<Unit>()
    private val storageScope = CoroutineScope(SupervisorJob() + storageDispatcher)
    private val mutableHistoryState = MutableStateFlow(HistoryStoreSnapshot())
    override val historyState: StateFlow<HistoryStoreSnapshot> = mutableHistoryState.asStateFlow()

    init {
        storageScope.launch { initializeFromStorage() }
    }

    /** Initialization starts when the store is constructed and never blocks the caller. */
    override fun initialize() = Unit

    override fun load(): List<History> = historyState.value.histories

    /** Legacy synchronous mutations are queued; shared feature code uses the suspend variants. */
    override fun record(id: Int, createdAtMillis: Long) {
        storageScope.launch { recordHistory(id, createdAtMillis) }
    }

    override fun remove(id: Int) {
        storageScope.launch { removeHistory(id) }
    }

    override fun clear() {
        storageScope.launch { clearHistory() }
    }

    override fun contains(id: Int): Boolean = historyState.value.histories.any { it.id == id }

    override val size: Int
        get() = historyState.value.histories.size

    override val changeVersion: Long
        get() = historyState.value.changeVersion

    override suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean =
        withContext(storageDispatcher) {
            initialization.await()
            mutationMutex.withLock {
                ledger.record(id, createdAtMillis).also { changed ->
                    if (changed) persistAndPublish()
                }
            }
        }

    override suspend fun removeHistory(id: Int): Boolean = withContext(storageDispatcher) {
        initialization.await()
        mutationMutex.withLock {
            ledger.remove(id).also { changed ->
                if (changed) persistAndPublish()
            }
        }
    }

    override suspend fun clearHistory() = withContext(storageDispatcher) {
        initialization.await()
        mutationMutex.withLock {
            ledger.clear()
            persistAndPublish()
        }
    }

    override fun close() {
        storageScope.cancel()
    }

    private fun persistAndPublish() {
        store.putString(storageKey, ledger.serialize())
        publish()
    }

    private suspend fun initializeFromStorage() {
        try {
            mutationMutex.withLock {
                val trimmed = runCatching { ledger.initialize(store.getString(storageKey)) }
                    .getOrElse {
                        ledger.initialize(null)
                        false
                    }
                if (trimmed) store.putString(storageKey, ledger.serialize())
                publish()
            }
        } finally {
            initialization.complete(Unit)
        }
    }

    private fun publish() {
        mutableHistoryState.value = HistoryStoreSnapshot(ledger.load(), ledger.changeVersion)
    }
}
