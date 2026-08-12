package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.Foundation.NSUserDefaults

/** Foundation-backed settings adapter. Keep one instance in the iOS application composition. */
class IosKeyValueStore(
    private val defaults: NSUserDefaults,
) : KeyValueStore {
    constructor() : this(NSUserDefaults.standardUserDefaults)

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    override fun clear() {
        defaults.dictionaryRepresentation().keys.forEach { key ->
            (key as? String)?.let { defaults.removeObjectForKey(it) }
        }
        changed()
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
        changed()
    }

    override fun getString(key: String, default: String?): String? =
        defaults.stringForKey(key) ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
        changed()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (contains(key)) defaults.boolForKey(key) else default

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, key)
        changed()
    }

    override fun getInt(key: String, default: Int): Int =
        if (contains(key)) defaults.integerForKey(key).toInt() else default

    override fun putInt(key: String, value: Int) {
        defaults.setInteger(value.toLong(), key)
        changed()
    }

    override fun getFloat(key: String, default: Float): Float =
        if (contains(key)) defaults.floatForKey(key) else default

    override fun putFloat(key: String, value: Float) {
        defaults.setFloat(value, key)
        changed()
    }

    override fun getStringSet(key: String): Set<String> =
        defaults.stringArrayForKey(key)
            ?.mapNotNull { it as? String }
            ?.toSet()
            .orEmpty()

    override fun putStringSet(key: String, value: Set<String>?) {
        if (value == null) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value.toList(), key)
        }
        changed()
    }

    /** Call after the native host writes directly to the same defaults domain. */
    fun notifyChanged() = changed()

    private fun changed() {
        mutableChanges.tryEmit(Unit)
    }
}
