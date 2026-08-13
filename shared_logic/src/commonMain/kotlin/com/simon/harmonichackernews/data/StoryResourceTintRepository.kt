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

/** Stores each field separately so URLs require no escaping and old cache formats stay untouched. */
class StoryResourceTintRepository(
    private val store: KeyValueStore,
) : StoryResourceTintStore {
    override fun read(
        storyId: Int,
        kind: StoryResourceTintKind,
        sourceUrl: String,
        baseColorArgb: Int,
        paletteConfigKey: String,
    ): StoryResourceTintState? {
        val prefix = prefix(storyId, kind)
        if (store.getString("$prefix.source") != sourceUrl ||
            store.getInt("$prefix.base", Int.MIN_VALUE) != baseColorArgb ||
            store.getString("$prefix.palette") != paletteConfigKey ||
            !store.contains("$prefix.tint")
        ) {
            return null
        }
        return StoryResourceTintState(
            sourceUrl = sourceUrl,
            baseColorArgb = baseColorArgb,
            paletteConfigKey = paletteConfigKey,
            tintColorArgb = store.getInt("$prefix.tint", 0),
        )
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
    }

    override fun count(): Int = store.getStringSet(INDEX_KEY).size

    override fun clear() {
        store.getStringSet(INDEX_KEY).forEach { key ->
            val prefix = "$PREFIX.$key"
            store.remove("$prefix.source")
            store.remove("$prefix.base")
            store.remove("$prefix.palette")
            store.remove("$prefix.tint")
        }
        store.remove(INDEX_KEY)
    }

    private fun prefix(storyId: Int, kind: StoryResourceTintKind): String =
        "$PREFIX.${entryKey(storyId, kind)}"

    private fun entryKey(storyId: Int, kind: StoryResourceTintKind): String =
        "${kind.name.lowercase()}.$storyId"

    private companion object {
        const val PREFIX = "story_resource_tint"
        const val INDEX_KEY = "$PREFIX.index"
    }
}
