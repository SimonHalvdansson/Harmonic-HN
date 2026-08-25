package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Volatile key-value adapter for previews and tests that must not write host state.
 */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, Any>()
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 32)

    val changes: SharedFlow<Unit> = mutableChanges.asSharedFlow()

    override fun clear() {
        values.clear()
        changed()
    }

    override fun contains(key: String): Boolean = key in values

    override fun keys(): Set<String> = values.keys.toSet()

    override fun remove(key: String) {
        values.remove(key)
        changed()
    }

    override fun getString(key: String, default: String?): String? =
        values[key] as? String ?: default

    override fun putString(key: String, value: String?) = putNullable(key, value)

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    override fun getInt(key: String, default: Int): Int = values[key] as? Int ?: default

    override fun putInt(key: String, value: Int) = put(key, value)

    override fun getFloat(key: String, default: Float): Float = values[key] as? Float ?: default

    override fun putFloat(key: String, value: Float) = put(key, value)

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> =
        (values[key] as? Set<String>)?.toSet().orEmpty()

    override fun putStringSet(key: String, value: Set<String>?) =
        putNullable(key, value?.toSet())

    override fun update(block: KeyValueStore.Editor.() -> Unit) {
        val staged = values.toMutableMap()
        block(object : KeyValueStore.Editor {
            override fun remove(key: String) {
                staged.remove(key)
            }

            override fun putString(key: String, value: String?) = putOrRemove(staged, key, value)
            override fun putBoolean(key: String, value: Boolean) = putOrRemove(staged, key, value)
            override fun putInt(key: String, value: Int) = putOrRemove(staged, key, value)
            override fun putLong(key: String, value: Long) = putOrRemove(staged, key, value.toString())
            override fun putFloat(key: String, value: Float) = putOrRemove(staged, key, value)
            override fun putStringSet(key: String, value: Set<String>?) =
                putOrRemove(staged, key, value?.toSet())
        })
        values.clear()
        values.putAll(staged)
        changed()
    }

    private fun putNullable(key: String, value: Any?) {
        if (value == null) values.remove(key) else values[key] = value
        changed()
    }

    private fun put(key: String, value: Any) {
        values[key] = value
        changed()
    }

    private fun changed() {
        mutableChanges.tryEmit(Unit)
    }

    private fun putOrRemove(target: MutableMap<String, Any>, key: String, value: Any?) {
        if (value == null) target.remove(key) else target[key] = value
    }
}
