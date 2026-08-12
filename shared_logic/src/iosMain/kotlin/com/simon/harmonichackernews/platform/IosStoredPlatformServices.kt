package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlin.time.Clock

/** NSUserDefaults-backed bookmark adapter using the shared saved-item codec and repository. */
class IosBookmarkStore private constructor(
    store: KeyValueStore,
    private val nowMillis: () -> Long,
) : BookmarkStore {
    constructor(store: KeyValueStore) : this(
        store,
        { Clock.System.now().toEpochMilliseconds() },
    )

    private val savedItems = SavedItemsRepository(store)

    override fun load(): List<Bookmark> = SavedItemCodec.toBookmarks(
        savedItems.loadItems(SavedItemSource.BOOKMARKS, sortedByCreated = true),
    )

    override fun add(id: Int) {
        savedItems.setMembership(SavedItemSource.BOOKMARKS, id, true, nowMillis())
    }

    override fun remove(id: Int) {
        savedItems.setMembership(SavedItemSource.BOOKMARKS, id, false, nowMillis())
    }

    override fun clear() = savedItems.saveItems(SavedItemSource.BOOKMARKS, emptyList())

}

/** NSUserDefaults-backed history adapter preserving the existing serialized history format. */
class IosHistoryStore(
    private val store: KeyValueStore,
) : HistoryStore {
    private val ledger = HistoryLedger()
    private var initialized = false

    override fun initialize() {
        ledger.initialize(store.getString(HISTORY_KEY))
        initialized = true
    }

    override fun load(): List<History> =
        HistoryLedger.decodeHistories(store.getString(HISTORY_KEY), sorted = true)

    override fun record(id: Int, createdAtMillis: Long) {
        ensureInitialized()
        if (ledger.record(id, createdAtMillis)) persist()
    }

    override fun remove(id: Int) {
        ensureInitialized()
        if (ledger.remove(id)) persist()
    }

    override fun clear() {
        ledger.clear()
        initialized = true
        persist()
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

    private fun ensureInitialized() {
        if (!initialized) initialize()
    }

    private fun persist() {
        store.putString(HISTORY_KEY, ledger.serialize())
    }

    private companion object {
        const val HISTORY_KEY = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_HISTORIES"
    }
}
