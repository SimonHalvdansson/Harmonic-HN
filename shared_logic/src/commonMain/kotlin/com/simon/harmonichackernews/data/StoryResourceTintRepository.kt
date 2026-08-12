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
    }

    private fun prefix(storyId: Int, kind: StoryResourceTintKind): String =
        "story_resource_tint.${kind.name.lowercase()}.$storyId"
}
