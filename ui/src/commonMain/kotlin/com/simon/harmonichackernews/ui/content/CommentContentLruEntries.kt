package com.simon.harmonichackernews.ui.content

/** UI-owned entries; hits refresh recency without scanning keys or allocating another map node. */
internal class CommentContentLruEntries<K : Any, V : Any> {
    // Revision cleanup visits every key on a miss; retain the original map's linked iteration.
    private val entries = linkedMapOf<K, Entry<K, V>>()
    private var oldest: Entry<K, V>? = null
    private var newest: Entry<K, V>? = null

    val size: Int get() = entries.size
    // Used only to find obsolete content revisions; iteration does not refresh recency.
    val keys: Set<K> get() = entries.keys

    fun isNotEmpty(): Boolean = entries.isNotEmpty()
    operator fun contains(key: K): Boolean = key in entries
    fun peek(key: K): V? = entries[key]?.value
    fun oldestKey(): K = checkNotNull(oldest).key

    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (entry !== newest) {
            unlink(entry)
            append(entry)
        }
        return entry.value
    }

    /** An already installed value and its recency are retained, matching prefetch installation. */
    fun install(key: K, value: V) {
        if (key in entries) return
        val entry = Entry(key, value)
        entries[key] = entry
        append(entry)
    }

    fun remove(key: K): V? {
        val entry = entries.remove(key) ?: return null
        unlink(entry)
        return entry.value
    }

    fun clear() {
        entries.clear()
        oldest = null
        newest = null
    }

    private fun unlink(entry: Entry<K, V>) {
        val previous = entry.previous
        val next = entry.next
        if (previous == null) oldest = next else previous.next = next
        if (next == null) newest = previous else next.previous = previous
    }

    private fun append(entry: Entry<K, V>) {
        entry.previous = newest
        entry.next = null
        val previousNewest = newest
        if (previousNewest == null) oldest = entry else previousNewest.next = entry
        newest = entry
    }

    private class Entry<K, V>(val key: K, val value: V) {
        var previous: Entry<K, V>? = null
        var next: Entry<K, V>? = null
    }
}
