package com.simon.harmonichackernews.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TimestampedItem(
    val id: Int,
    val created: Long,
)

object SavedItemCodec {
    fun decode(value: String?, sortedByCreated: Boolean = false): List<TimestampedItem> {
        if (value.isNullOrEmpty()) return emptyList()
        val items = ArrayList<TimestampedItem>()
        var segmentStart = 0
        while (segmentStart <= value.length) {
            val segmentEnd = value.indexOf('-', segmentStart).let {
                if (it < 0) value.length else it
            }
            val separator = value.indexOf('q', segmentStart)
            if (separator in (segmentStart + 1)..<segmentEnd &&
                value.indexOf('q', separator + 1).let { it < 0 || it >= segmentEnd }
            ) {
                val id = parseInt(value, segmentStart, separator)
                val created = parseLong(value, separator + 1, segmentEnd)
                if (id != null && created != null) items += TimestampedItem(id, created)
            }
            if (segmentEnd == value.length) break
            segmentStart = segmentEnd + 1
        }
        if (sortedByCreated) items.sortByDescending(TimestampedItem::created)
        return items
    }

    private fun parseInt(value: String, start: Int, end: Int): Int? {
        if (start >= end) return null
        var index = start
        val negative = value[index] == '-'
        if (negative && ++index == end) return null
        val limit = if (negative) Int.MIN_VALUE else -Int.MAX_VALUE
        val multiplicationLimit = limit / 10
        var result = 0
        while (index < end) {
            val digit = value[index++] - '0'
            if (digit !in 0..9 || result < multiplicationLimit) return null
            result *= 10
            if (result < limit + digit) return null
            result -= digit
        }
        return if (negative) result else -result
    }

    private fun parseLong(value: String, start: Int, end: Int): Long? {
        if (start >= end) return null
        var index = start
        val negative = value[index] == '-'
        if (negative && ++index == end) return null
        val limit = if (negative) Long.MIN_VALUE else -Long.MAX_VALUE
        val multiplicationLimit = limit / 10L
        var result = 0L
        while (index < end) {
            val digit = value[index++] - '0'
            if (digit !in 0..9 || result < multiplicationLimit) return null
            result *= 10L
            if (result < limit + digit) return null
            result -= digit
        }
        return if (negative) result else -result
    }

    fun encode(items: List<TimestampedItem>): String =
        items.joinToString("-") { "${it.id}q${it.created}" }

    fun add(items: List<TimestampedItem>, id: Int, created: Long): List<TimestampedItem> =
        if (items.any { it.id == id }) items.toList() else items + TimestampedItem(id, created)

    fun remove(items: List<TimestampedItem>, id: Int): List<TimestampedItem> {
        var removed = false
        return items.filter { item ->
            if (!removed && item.id == id) {
                removed = true
                false
            } else {
                true
            }
        }
    }

    fun setMembership(
        items: List<TimestampedItem>,
        id: Int,
        present: Boolean,
        created: Long,
    ): List<TimestampedItem> = if (present) add(items, id, created) else remove(items, id)

    fun fromIds(ids: List<Int>, createdAtMillis: Long): List<TimestampedItem> {
        val seen = mutableSetOf<Int>()
        return ids.mapNotNull { id ->
            if (seen.add(id)) TimestampedItem(id, createdAtMillis - seen.size + 1L) else null
        }
    }

}

data class SavedItemSnapshot(
    val itemIds: List<Int>,
    val commentIds: Set<Int>,
) {
    fun matches(cachedItems: List<TimestampedItem>, cachedCommentIds: Set<Int>): Boolean =
        cachedItems.map(TimestampedItem::id) == itemIds && cachedCommentIds == commentIds
}

object SavedItemSnapshots {
    fun normalize(itemIds: List<Int>, commentIds: List<Int>): SavedItemSnapshot {
        val normalizedItemIds = itemIds.distinct().sortedDescending()
        val itemIdSet = normalizedItemIds.toHashSet()
        return SavedItemSnapshot(
            itemIds = normalizedItemIds,
            commentIds = commentIds.filterTo(mutableSetOf()) { it in itemIdSet },
        )
    }
}

class HistoryLedger {
    private val histories = mutableListOf<History>()
    private val historyIds = mutableSetOf<Int>()

    var changeVersion: Long = 0L
        private set

    val size: Int get() = histories.size

    fun initialize(serialized: String?) {
        histories.clear()
        histories += decodeHistories(serialized, sorted = true)
        historyIds.clear()
        histories.mapTo(historyIds, History::id)
        changeVersion++
    }

    fun load(): List<History> = histories.toList()

    fun find(id: Int): History? = histories.firstOrNull { it.id == id }

    fun contains(id: Int): Boolean = id in historyIds

    fun record(id: Int, createdAtMillis: Long): Boolean {
        if (!historyIds.add(id)) return false
        histories += History(id, createdAtMillis)
        changeVersion++
        return true
    }

    fun remove(id: Int): Boolean {
        val index = histories.indexOfFirst { it.id == id }
        if (index < 0) return false
        histories.removeAt(index)
        if (histories.none { it.id == id }) historyIds.remove(id)
        changeVersion++
        return true
    }

    fun clear() {
        histories.clear()
        historyIds.clear()
        changeVersion++
    }

    fun serialize(): String = SavedItemCodec.encode(
        histories.map { TimestampedItem(it.id, it.created) },
    )

    companion object {
        fun decodeHistories(serialized: String?, sorted: Boolean): MutableList<History> =
            SavedItemCodec.decode(serialized, sorted)
                .mapTo(mutableListOf()) { History(it.id, it.created) }
    }
}

data class HistoryLedgerSnapshot(
    val histories: List<History>,
    val changeVersion: Long,
    val serialized: String,
)

/**
 * Coroutine-safe, observable history state for new platform persistence adapters.
 * Existing adapters can migrate without changing [HistoryLedger]'s legacy synchronous API.
 */
class AtomicHistoryLedger(serialized: String? = null) {
    private val ledger = HistoryLedger().apply { initialize(serialized) }
    private val mutationMutex = Mutex()
    private val mutableState = MutableStateFlow(ledger.snapshot())

    val state: StateFlow<HistoryLedgerSnapshot> = mutableState.asStateFlow()

    suspend fun initialize(serialized: String?) = mutationMutex.withLock {
        ledger.initialize(serialized)
        publish()
    }

    suspend fun record(id: Int, createdAtMillis: Long): Boolean = mutationMutex.withLock {
        ledger.record(id, createdAtMillis).also { changed ->
            if (changed) publish()
        }
    }

    suspend fun remove(id: Int): Boolean = mutationMutex.withLock {
        ledger.remove(id).also { changed ->
            if (changed) publish()
        }
    }

    suspend fun clear() = mutationMutex.withLock {
        ledger.clear()
        publish()
    }

    fun current(): HistoryLedgerSnapshot = state.value

    private fun publish() {
        mutableState.value = ledger.snapshot()
    }

    private fun HistoryLedger.snapshot() = HistoryLedgerSnapshot(
        histories = load(),
        changeVersion = changeVersion,
        serialized = serialize(),
    )
}
