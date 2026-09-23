package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.serialization.JsonObject

/** Portable preference format and username matching rules for user tags. */
object UserTagCodec {
    fun decode(serialized: String?, normalizeUsernames: Boolean): MutableMap<String, String> {
        if (serialized.isNullOrEmpty()) return linkedMapOf()
        return runCatching {
            val json = JsonObject(serialized)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val storedKey = keys.next()
                    val storedUsername = storedKey.trim()
                    val username = if (normalizeUsernames) {
                        storedUsername.lowercase()
                    } else {
                        storedUsername
                    }
                    put(username, json.optString(storedKey, ""))
                }
            }.toMutableMap()
        }.getOrDefault(linkedMapOf())
    }

    fun tagFor(serialized: String?, username: String?): String {
        val normalizedUsername = username?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
            ?: return ""
        if (serialized.isNullOrEmpty()) return ""
        return runCatching {
            val json = JsonObject(serialized)
            var tag = ""
            val keys = json.keys()
            while (keys.hasNext()) {
                val storedKey = keys.next()
                if (storedKey.trim().lowercase() == normalizedUsername) {
                    // Keep the last normalized match, just as decode's map does.
                    tag = json.optString(storedKey, "")
                }
            }
            tag
        }.getOrDefault("")
    }

    fun update(serialized: String?, username: String?, tag: String?): String? {
        val key = username?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val tags = decode(serialized, normalizeUsernames = false)
        tags.keys.removeAll { it.equals(key, ignoreCase = true) }
        tag?.trim()?.takeIf(String::isNotEmpty)?.let { tags[key] = it }
        return JsonObject().apply {
            tags.forEach { (savedUsername, savedTag) -> put(savedUsername, savedTag) }
        }.toString()
    }
}
