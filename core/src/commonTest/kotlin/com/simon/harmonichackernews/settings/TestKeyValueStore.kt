package com.simon.harmonichackernews.settings

internal class TestKeyValueStore(
    initialValues: Map<String, Any?> = emptyMap(),
) : KeyValueStore {
    private val values = initialValues.toMutableMap()

    override fun clear() {
        values.clear()
    }

    override fun contains(key: String): Boolean = key in values

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun getString(key: String, default: String?): String? =
        values[key]?.let { it as String } ?: default

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        values[key]?.let { it as Boolean } ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getInt(key: String, default: Int): Int =
        values[key]?.let { it as Int } ?: default

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getFloat(key: String, default: Float): Float =
        values[key]?.let { it as Float } ?: default

    override fun putFloat(key: String, value: Float) {
        values[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String): Set<String> =
        values[key]?.let { it as Set<String> } ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>?) {
        if (value == null) values.remove(key) else values[key] = value.toSet()
    }
}
