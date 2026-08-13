package com.simon.harmonichackernews.settings

/**
 * Minimal platform-neutral persistence surface used by shared repositories.
 *
 * Platform implementations decide where values live. The Android app provides adapters for its
 * existing default and named SharedPreferences stores, so moving logic here does not migrate or
 * rewrite any user data.
 */
interface KeyValueStore {
    /** Clears this store without affecting other platform stores. */
    fun clear() {
        throw UnsupportedOperationException("This key-value store does not support clearing")
    }

    fun contains(key: String): Boolean

    /** Returns stored keys when the platform can enumerate them. */
    fun keys(): Set<String> = emptySet()

    fun remove(key: String)

    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)

    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)

    fun getLong(key: String, default: Long): Long =
        getString(key)?.toLongOrNull() ?: default
    fun putLong(key: String, value: Long) = putString(key, value.toString())

    fun getFloat(key: String, default: Float): Float
    fun putFloat(key: String, value: Float)

    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>?)
}
