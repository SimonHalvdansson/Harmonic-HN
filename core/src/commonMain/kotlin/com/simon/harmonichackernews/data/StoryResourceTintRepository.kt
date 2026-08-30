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
        val key = entryKey(storyId, kind)
        val index = store.getStringSet(INDEX_KEY).toMutableSet().apply { add(key) }
        val evicted = mutableSetOf<String>()
        val entries = index.iterator()
        while (index.size > MAX_STORED_ENTRIES && entries.hasNext()) {
            val candidate = entries.next()
            if (candidate != key) {
                entries.remove()
                evicted.add(candidate)
            }
        }
        store.update {
            evicted.forEach { removeEntry(it) }
            putString("$prefix.source", tint.sourceUrl)
            putInt("$prefix.base", tint.baseColorArgb)
            putString("$prefix.palette", tint.paletteConfigKey)
            putInt("$prefix.tint", tint.tintColorArgb)
            putStringSet(INDEX_KEY, index)
        }
        invalidateReadCache(evicted)
        invalidateReadCache(storyId, kind)
        cacheRead(
            ReadKey(storyId, kind, tint.sourceUrl, tint.baseColorArgb, tint.paletteConfigKey),
            tint,
        )
    }

    override fun count(): Int = store.getStringSet(INDEX_KEY).size

    override fun clear() {
        // Release cached URLs and tint objects before SharedPreferences prepares its editor update.
        readCache.clear()
        val storedKeys = store.keys().filterTo(mutableSetOf()) { key ->
            key == INDEX_KEY || key.startsWith("$PREFIX.")
        }
        if (storedKeys.isNotEmpty()) {
            store.update {
                storedKeys.forEach(::remove)
            }
        } else {
            // Backward-compatible fallback for minimal stores that cannot enumerate keys.
            val indexedEntries = store.getStringSet(INDEX_KEY)
            store.update {
                indexedEntries.forEach { removeEntry(it) }
                remove(INDEX_KEY)
            }
        }
    }

    private fun KeyValueStore.Editor.removeEntry(key: String) {
        val prefix = "$PREFIX.$key"
        remove("$prefix.source")
        remove("$prefix.base")
        remove("$prefix.palette")
        remove("$prefix.tint")
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

    private fun invalidateReadCache(entryKeys: Set<String>) {
        if (entryKeys.isEmpty()) return
        val entries = readCache.entries.iterator()
        while (entries.hasNext()) {
            val key = entries.next().key
            if (entryKey(key.storyId, key.kind) in entryKeys) entries.remove()
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
        const val MAX_STORED_ENTRIES = MAX_READ_CACHE_ENTRIES

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
