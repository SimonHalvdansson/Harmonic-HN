package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.StoryResourceTintKind
import com.simon.harmonichackernews.network.StoryResourceTintState
import com.simon.harmonichackernews.settings.KeyValueStore

/** Portable persistence for palette results produced by shared Compose image rendering. */
interface StoryResourceTintStore {
    fun read(
        storyId: Int,
        kind: StoryResourceTintKind,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
    ): StoryResourceTintState?

    fun write(storyId: Int, kind: StoryResourceTintKind, tint: StoryResourceTintState)

    fun count(): Int = 0

    fun clear() = Unit

    data object None : StoryResourceTintStore {
        override fun read(
            storyId: Int,
            kind: StoryResourceTintKind,
            sourceUrl: String,
            baseColorArgb: Int,
            paletteConfigKey: String,
        ): StoryResourceTintState? = null

        override fun write(
            storyId: Int,
            kind: StoryResourceTintKind,
            tint: StoryResourceTintState,
        ) = Unit
    }
}

/**
 * Keeps one canonical palette result for a resource/configuration tuple. The same image may be
 * decoded at different sizes by list and detail surfaces, which can otherwise produce slightly
 * different palettes and make the last surface to finish overwrite the first one.
 */
fun StoryResourceTintStore.canonicalize(
    storyId: Int,
    kind: StoryResourceTintKind,
    candidate: StoryResourceTintState,
): StoryResourceTintState = read(
    storyId = storyId,
    kind = kind,
    sourceUrl = candidate.sourceUrl,
    baseColorArgb = candidate.baseColorArgb,
    paletteConfigKey = candidate.paletteConfigKey,
) ?: candidate.also { write(storyId, kind, it) }

/** Stores each field separately so URLs require no escaping and old cache formats stay untouched. */
class StoryResourceTintRepository(
    private val store: KeyValueStore,
) : StoryResourceTintStore {
    private val readCache = LinkedHashMap<ReadKey, CachedRead>()

    override fun read(
        storyId: Int,
        kind: StoryResourceTintKind,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
    ): StoryResourceTintState? {
        val key = ReadKey(storyId, kind, sourceUrl, baseColorArgb, paletteConfigKey)
        readCache[key]?.let { return it.value }

        val prefix = prefix(storyId, kind)
        val tint = if (store.getString("$prefix.source") != sourceUrl ||
            store.getInt("$prefix.base", Int.MIN_VALUE) != baseColorArgb ||
            store.getString("$prefix.palette") != paletteConfigKey ||
            !store.contains("$prefix.tint")
        ) {
            null
        } else {
            StoryResourceTintState(
                sourceUrl = sourceUrl,
                baseColorArgb = baseColorArgb,
                paletteConfigKey = paletteConfigKey,
                tintColorArgb = store.getInt("$prefix.tint", 0),
            )
        }
        cacheRead(key, tint)
        return tint
    }

    override fun write(
        storyId: Int,
        kind: StoryResourceTintKind,
        tint: StoryResourceTintState,
    ) {
        val prefix = prefix(storyId, kind)
        store.putString("$prefix.source", tint.sourceUrl)
        store.putInt("$prefix.base", tint.baseColorArgb)
        store.putString("$prefix.palette", tint.paletteConfigKey)
        store.putInt("$prefix.tint", tint.tintColorArgb)
        val key = entryKey(storyId, kind)
        store.putStringSet(INDEX_KEY, store.getStringSet(INDEX_KEY) + key)
        invalidateReadCache(storyId, kind)
        cacheRead(
            ReadKey(storyId, kind, tint.sourceUrl, tint.baseColorArgb, tint.paletteConfigKey),
            tint,
        )
    }

    override fun count(): Int = store.getStringSet(INDEX_KEY).size

    override fun clear() {
        val storedKeys = store.keys().filterTo(mutableSetOf()) { key ->
            key == INDEX_KEY || key.startsWith("$PREFIX.")
        }
        if (storedKeys.isNotEmpty()) {
            storedKeys.forEach(store::remove)
        } else {
            // Backward-compatible fallback for minimal stores that cannot enumerate keys.
            store.getStringSet(INDEX_KEY).forEach { key ->
                val prefix = "$PREFIX.$key"
                store.remove("$prefix.source")
                store.remove("$prefix.base")
                store.remove("$prefix.palette")
                store.remove("$prefix.tint")
            }
            store.remove(INDEX_KEY)
        }
        readCache.clear()
    }

    private fun cacheRead(key: ReadKey, value: StoryResourceTintState?) {
        if (!readCache.containsKey(key) && readCache.size >= MAX_READ_CACHE_ENTRIES) {
            readCache.entries.iterator().let { entries ->
                if (entries.hasNext()) {
                    entries.next()
                    entries.remove()
                }
            }
        }
        readCache[key] = CachedRead(value)
    }

    private fun invalidateReadCache(storyId: Int, kind: StoryResourceTintKind) {
        val entries = readCache.entries.iterator()
        while (entries.hasNext()) {
            val key = entries.next().key
            if (key.storyId == storyId && key.kind == kind) entries.remove()
        }
    }

    private fun prefix(storyId: Int, kind: StoryResourceTintKind): String =
        "$PREFIX.${entryKey(storyId, kind)}"

    private fun entryKey(storyId: Int, kind: StoryResourceTintKind): String =
        "${kind.name.lowercase()}.$storyId"

    private companion object {
        const val PREFIX = "story_resource_tint"
        const val INDEX_KEY = "$PREFIX.index"
        const val MAX_READ_CACHE_ENTRIES = 512

        data class ReadKey(
            val storyId: Int,
            val kind: StoryResourceTintKind,
            val sourceUrl: String,
            val baseColorArgb: Int,
            val paletteConfigKey: String,
        )

        data class CachedRead(val value: StoryResourceTintState?)
    }
}
