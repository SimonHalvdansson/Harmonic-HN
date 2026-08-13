package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.KeyValueStore

/**
 * Portable access-time metadata for cache files.
 *
 * Multiplatform filesystem APIs expose file metadata but do not portably update modification
 * timestamps. Keeping cache recency in the host's existing key-value store makes touch and
 * eviction semantics identical on Android, iOS and desktop.
 */
class FileAccessTimeStore(
    private val store: KeyValueStore,
) {
    fun read(reference: String): Long? = store.getLong(key(reference), MISSING).takeUnless {
        it == MISSING
    }

    fun readOrInitialize(reference: String, nowMillis: Long): Long =
        read(reference) ?: nowMillis.also { touch(reference, it) }

    fun touch(reference: String, nowMillis: Long) {
        store.putLong(key(reference), nowMillis)
    }

    fun remove(reference: String) {
        store.remove(key(reference))
    }

    fun removeTree(reference: String) {
        val exact = key(reference)
        val unixPrefix = "$exact/"
        val windowsPrefix = "$exact\\"
        store.keys()
            .filter { it == exact || it.startsWith(unixPrefix) || it.startsWith(windowsPrefix) }
            .forEach(store::remove)
    }

    private fun key(reference: String): String = KEY_PREFIX + reference

    private companion object {
        const val KEY_PREFIX = "file_access:"
        const val MISSING = Long.MIN_VALUE
    }
}
