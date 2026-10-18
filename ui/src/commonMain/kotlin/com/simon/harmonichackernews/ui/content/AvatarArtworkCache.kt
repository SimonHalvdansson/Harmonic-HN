package com.simon.harmonichackernews.ui.content

import com.simon.harmonichackernews.settings.UserAvatarStyle

/** UI-owned bounded cache. Artwork paths are read-only after construction and survive row reuse. */
internal class AvatarArtworkCache<T : Any>(
    private val capacity: Int = 128,
    private val create: (String, UserAvatarStyle) -> T,
) {
    private data class Key(val author: String, val style: UserAvatarStyle)
    private val entries = CommentContentLruEntries<Key, T>()

    init {
        require(capacity > 0)
    }

    fun get(author: String, style: UserAvatarStyle): T {
        val key = Key(author, style)
        entries[key]?.let { return it }
        val artwork = create(author, style)
        if (entries.size == capacity) entries.remove(entries.oldestKey())
        entries.install(key, artwork)
        return artwork
    }
}
