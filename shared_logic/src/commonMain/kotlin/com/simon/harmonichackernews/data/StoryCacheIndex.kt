package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.serialization.JsonArray
import com.simon.harmonichackernews.serialization.JsonObject

data class StoryCacheIndexUpdate(
    val encodedEntries: Set<String>,
    val evictedStoryIds: List<Int>,
)

data class StoryCacheEntry(val storyId: Int, val cachedAtMillis: Long)

object StoryCacheIndex {
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
        val evicted = mutableListOf<Int>()
        while (validEntries.size > maximumEntries.coerceAtLeast(0)) {
            val oldest = validEntries.values.minWithOrNull(
                compareBy<StoryCacheEntry>(StoryCacheEntry::cachedAtMillis)
                    .thenBy(StoryCacheEntry::storyId),
            ) ?: break
            validEntries.remove(oldest.storyId)
            evicted += oldest.storyId
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

    private fun parse(value: String): StoryCacheEntry? {
        val separator = value.indexOf('-')
        if (separator <= 0 || separator == value.lastIndex || value.indexOf('-', separator + 1) >= 0) {
            return null
        }
        val id = value.substring(0, separator).toIntOrNull()?.takeIf { it > 0 } ?: return null
        val time = value.substring(separator + 1).toLongOrNull()?.takeIf { it >= 0 } ?: return null
        return StoryCacheEntry(id, time)
    }

    private fun encode(entry: StoryCacheEntry): String = "${entry.storyId}-${entry.cachedAtMillis}"
}

object StoryCachePayloadParser {
    fun storyIds(response: String?, maximumCount: Int): List<Int> {
        val values = JsonArray(response.orEmpty())
        val count = minOf(values.length(), maximumCount.coerceAtLeast(0))
        return List(count, values::getInt)
    }

    fun externalArticleUrl(storyJson: String): String? {
        val value = JsonObject(storyJson)
        if (!value.has("url") || value.isNull("url")) return null
        return value.optString("url", "").takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }
    }
}
