package com.simon.harmonichackernews.data

data class TimestampedItem(
    val id: Int,
    val created: Long,
)

object SavedItemCodec {
    fun decode(value: String?, sortedByCreated: Boolean = false): List<TimestampedItem> {
        if (value.isNullOrEmpty()) return emptyList()
        val items = value.splitToSequence('-').mapNotNull { encoded ->
            val separator = encoded.indexOf('q')
            if (separator <= 0 || separator == encoded.lastIndex || encoded.indexOf('q', separator + 1) >= 0) {
                return@mapNotNull null
            }
            val id = encoded.substring(0, separator).toIntOrNull() ?: return@mapNotNull null
            val created = encoded.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            TimestampedItem(id, created)
        }.toMutableList()
        if (sortedByCreated) items.sortByDescending(TimestampedItem::created)
        return items
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

    fun toBookmarks(items: List<TimestampedItem>): ArrayList<Bookmark> = ArrayList(
        items.map { item ->
            Bookmark().apply {
                id = item.id
                created = item.created
            }
        },
    )

    fun fromBookmarks(items: List<Bookmark>): List<TimestampedItem> =
        items.map { TimestampedItem(it.id, it.created) }
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
