package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.serialization.JsonObject

/** Portable preference format and username matching rules for user tags. */
object UserTagCodec {
    fun decode(serialized: String?): MutableMap<String, String> =
        decodeOrNull(serialized) ?: linkedMapOf()

    /** Null distinguishes corrupt data from an empty map so edits cannot erase it silently. */
    internal fun decodeOrNull(serialized: String?): MutableMap<String, String>? {
        if (serialized.isNullOrEmpty()) return linkedMapOf()
        return runCatching {
            val json = JsonObject(serialized)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val storedKey = keys.next()
                    val username = storedKey.trim()
                    if (username.isNotEmpty()) put(username, json.optString(storedKey, ""))
                }
            }.toMutableMap()
        }.getOrNull()
    }

    fun tagFor(serialized: String?, username: String?): String {
        val exactUsername = username?.trim()?.takeIf(String::isNotEmpty)
            ?: return ""
        return decode(serialized)[exactUsername].orEmpty()
    }

    fun update(serialized: String?, username: String?, tag: String?): String? {
        val key = username?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val tags = decodeOrNull(serialized) ?: return null
        tags.remove(key)
        tag?.trim()?.takeIf(String::isNotEmpty)?.let { tags[key] = it }
        return encode(tags)
    }

    internal fun encode(tags: Map<String, String>): String = JsonObject().apply {
        tags.forEach { (savedUsername, savedTag) -> put(savedUsername, savedTag) }
    }.toString()
}
