package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSRecursiveLock
import platform.Foundation.NSUserDefaults

/** Foundation-backed settings adapter. Keep one instance in the iOS application composition. */
class IosKeyValueStore(
    private val defaults: NSUserDefaults,
) : KeyValueStore {
    constructor() : this(NSUserDefaults.standardUserDefaults)

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    private val lock = NSRecursiveLock()
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    override fun clear() {
        locked {
            defaults.dictionaryRepresentation().keys.forEach { key ->
                (key as? String)?.let { defaults.removeObjectForKey(it) }
            }
        }
        changed()
    }

    override fun contains(key: String): Boolean = locked { defaults.objectForKey(key) != null }

    override fun keys(): Set<String> = locked {
        defaults.dictionaryRepresentation().keys.mapNotNull { it as? String }.toSet()
    }

    override fun remove(key: String) {
        locked { defaults.removeObjectForKey(key) }
        changed()
    }

    override fun getString(key: String, default: String?): String? =
        locked { defaults.stringForKey(key) ?: default }

    override fun putString(key: String, value: String?) {
        locked {
            if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
        }
        changed()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        locked { if (defaults.objectForKey(key) != null) defaults.boolForKey(key) else default }

    override fun putBoolean(key: String, value: Boolean) {
        locked { defaults.setBool(value, key) }
        changed()
    }

    override fun getInt(key: String, default: Int): Int =
        locked { if (defaults.objectForKey(key) != null) defaults.integerForKey(key).toInt() else default }

    override fun putInt(key: String, value: Int) {
        locked { defaults.setInteger(value.toLong(), key) }
        changed()
    }

    override fun getFloat(key: String, default: Float): Float =
        locked { if (defaults.objectForKey(key) != null) defaults.floatForKey(key) else default }

    override fun putFloat(key: String, value: Float) {
        locked { defaults.setFloat(value, key) }
        changed()
    }

    override fun getStringSet(key: String): Set<String> =
        locked {
            defaults.stringArrayForKey(key)
                ?.mapNotNull { it as? String }
                ?.toSet()
                .orEmpty()
        }

    override fun putStringSet(key: String, value: Set<String>?) {
        locked {
            if (value == null) {
                defaults.removeObjectForKey(key)
            } else {
                defaults.setObject(value.toList(), key)
            }
        }
        changed()
    }

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        // Build the complete edit first so exceptions cannot leave a partially applied batch, then
        // publish one change signal after Foundation has received every related value.
        val edits = mutableListOf<() -> Unit>()
        block(object : KeyValueStore.Editor {
            override fun remove(key: String) {
                edits += { defaults.removeObjectForKey(key) }
            }

            override fun putString(key: String, value: String?) {
                edits += {
                    if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
                }
            }

            override fun putBoolean(key: String, value: Boolean) {
                edits += { defaults.setBool(value, key) }
            }

            override fun putInt(key: String, value: Int) {
                edits += { defaults.setInteger(value.toLong(), key) }
            }

            override fun putLong(key: String, value: Long) {
                edits += { defaults.setObject(value.toString(), key) }
            }

            override fun putFloat(key: String, value: Float) {
                edits += { defaults.setFloat(value, key) }
            }

            override fun putStringSet(key: String, value: Set<String>?) {
                edits += {
                    if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value.toList(), key)
                }
            }
        })
        locked { edits.forEach { it() } }
        changed()
    }

    /** Call after the native host writes directly to the same defaults domain. */
    fun notifyChanged() = changed()

    private fun changed() {
        mutableChanges.tryEmit(Unit)
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
