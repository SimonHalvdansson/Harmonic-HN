package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.serialization.JsonObject

data class StoryCacheIndexUpdate(
    val encodedEntries: Set<String>,
    val evictedStoryIds: List<Int>,
)

data class StoryCacheEntry(val storyId: Int, val cachedAtMillis: Long)

object StoryCacheIndex {
    const val DEFAULT_MAX_AGE_MILLIS: Long = 24L * 60L * 60L * 1_000L

    fun record(
        encodedEntries: Set<String>,
        storyId: Int,
        cachedAtMillis: Long,
        maximumEntries: Int,
    ): StoryCacheIndexUpdate {
        val validEntries = encodedEntries.mapNotNull(::parse).associateByTo(
            linkedMapOf(),
            StoryCacheEntry::storyId,
        ).toMutableMap()
        validEntries[storyId] = StoryCacheEntry(storyId, cachedAtMillis)
        val evictionCount = validEntries.size - maximumEntries.coerceAtLeast(0)
        val evicted = if (evictionCount > 0) {
            val evictionOrder = validEntries.values.sortedWith(
                compareBy<StoryCacheEntry>(StoryCacheEntry::cachedAtMillis)
                    .thenBy(StoryCacheEntry::storyId),
            )
            ArrayList<Int>(evictionCount).apply {
                repeat(evictionCount) { index ->
                    val entry = evictionOrder[index]
                    validEntries.remove(entry.storyId)
                    add(entry.storyId)
                }
            }
        } else {
            emptyList()
        }
        return StoryCacheIndexUpdate(validEntries.values.mapTo(linkedSetOf(), ::encode), evicted)
    }

    fun remove(encodedEntries: Set<String>, storyId: Int): Set<String> =
        encodedEntries.mapNotNull(::parse)
            .filterNot { it.storyId == storyId }
            .mapTo(linkedSetOf(), ::encode)

    fun storyIds(encodedEntries: Set<String>): Set<Int> =
        encodedEntries.mapNotNullTo(linkedSetOf()) { parse(it)?.storyId }

    fun entries(encodedEntries: Set<String>): List<StoryCacheEntry> =
        encodedEntries.mapNotNull(::parse)

    fun recentEntries(
        encodedEntries: Set<String>,
        nowMillis: Long,
        maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    ): List<StoryCacheEntry> {
        val oldestAllowed = nowMillis - maxAgeMillis.coerceAtLeast(0L)
        val recent = ArrayList<StoryCacheEntry>(encodedEntries.size)
        encodedEntries.forEach { value ->
            val entry = parse(value) ?: return@forEach
            if (entry.cachedAtMillis >= oldestAllowed) recent += entry
        }
        recent.sortWith(compareBy(StoryCacheEntry::cachedAtMillis, StoryCacheEntry::storyId))
        return recent
    }

    private fun parse(value: String): StoryCacheEntry? {
        if (value.isEmpty()) return null

        var index = 0
        if (value[index] == '+') index++

        var id = 0
        var idDigits = 0
        while (index < value.length && value[index] != '-') {
            val digit = value[index] - '0'
            if (digit !in 0..9 || id > (Int.MAX_VALUE - digit) / 10) return null
            id = id * 10 + digit
            idDigits++
            index++
        }
        if (idDigits == 0 || id <= 0 || index >= value.lastIndex) return null
        index++

        if (value[index] == '+') index++

        var time = 0L
        var timeDigits = 0
        while (index < value.length) {
            val digit = value[index] - '0'
            if (digit !in 0..9 || time > (Long.MAX_VALUE - digit) / 10L) return null
            time = time * 10L + digit
            timeDigits++
            index++
        }
        if (timeDigits == 0) return null
        return StoryCacheEntry(id, time)
    }

    private fun encode(entry: StoryCacheEntry): String = "${entry.storyId}-${entry.cachedAtMillis}"
}

object ArticleSnapshotPolicy {
    const val MAX_BYTES: Long = 5L * 1_024L * 1_024L

    fun isValidSize(byteCount: Long): Boolean = byteCount in 1L..MAX_BYTES
}

object CacheFileNamePolicy {
    fun storyId(value: String, prefix: String = "", suffix: String = ""): Int? {
        if (!value.startsWith(prefix) || !value.endsWith(suffix)) return null
        val contentEnd = value.length - suffix.length
        if (contentEnd < prefix.length) return null
        return value.substring(prefix.length, contentEnd).toIntOrNull()?.takeIf { it > 0 }
    }
}

object StoryCachePayloadParser {
    fun externalArticleUrl(storyJson: String): String? {
        val value = JsonObject(storyJson)
        if (!value.has("url") || value.isNull("url")) return null
        return value.optString("url", "").takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }
    }
}
