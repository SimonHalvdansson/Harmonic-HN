package com.simon.harmonichackernews.settings

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-owned, coalesced persistence for content/cache preferences. Readers see pending values
 * immediately; only the worker calls commit(), so writes never join Android's lifecycle apply queue.
 * No preference or disk calls occur under [lock].
 */
internal class QueuedPreferenceWrites(
    private val preferences: SharedPreferences,
    scope: CoroutineScope,
) {
    private data class Change(val value: Any?)
    private val lock = Any()
    private val pending = linkedMapOf<String, Change>()
    private var pendingClear: Any? = null
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) {
                delay(50) // Combine a viewport's palette/preview updates into one write.
                while (true) {
                    val (clear, changes) = synchronized(lock) { pendingClear to pending.toMap() }
                    if (clear == null && changes.isEmpty()) break
                    val editor = preferences.edit()
                    if (clear != null) editor.clear()
                    changes.forEach { (key, change) ->
                        when (val value = change.value) {
                            null -> editor.remove(key)
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                            else -> error("Unsupported preference value")
                        }
                    }
                    @Suppress("ApplySharedPref")
                    val committed = editor.commit()
                    if (!committed) {
                        Log.w("HarmonicPreferences", "Preference write failed; retaining pending changes")
                        delay(1_000)
                        continue
                    }
                    synchronized(lock) {
                        changes.forEach { (key, change) ->
                            if (pending[key] === change) pending.remove(key)
                        }
                        if (pendingClear === clear) pendingClear = null
                    }
                }
            }
        }
    }

    fun update(values: Map<String, Any?>, clear: Boolean = false) {
        synchronized(lock) {
            if (clear) {
                pending.clear()
                pendingClear = Any()
            }
            values.forEach { (key, value) -> pending[key] = Change(value) }
        }
        requests.trySend(Unit)
    }

    fun <T> read(key: String, default: T, persisted: () -> T): T {
        val (change, cleared) = synchronized(lock) { pending[key] to (pendingClear != null) }
        if (change != null) {
            @Suppress("UNCHECKED_CAST")
            return (change.value ?: default) as T
        }
        return if (cleared) default else persisted()
    }

    fun keys(): Set<String> {
        val stored = preferences.all.keys
        return synchronized(lock) {
            (if (pendingClear == null) stored.toMutableSet() else mutableSetOf()).apply {
                pending.forEach { (key, change) -> if (change.value == null) remove(key) else add(key) }
            }
        }
    }
}
