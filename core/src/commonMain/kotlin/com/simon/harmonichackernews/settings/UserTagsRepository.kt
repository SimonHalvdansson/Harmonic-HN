package com.simon.harmonichackernews.settings

import kotlin.concurrent.Volatile

object UserTagKeys {
    const val TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
}

/** Platform-neutral persistence and case-insensitive lookup for user labels. */
class UserTagsRepository(private val store: KeyValueStore) {
    @Volatile
    private var lookupSnapshot: LookupSnapshot? = null

    fun tags(normalizeUsernames: Boolean = true): Map<String, String> =
        UserTagCodec.decode(store.getString(UserTagKeys.TAGS), normalizeUsernames)

    fun tagFor(username: String?): String {
        val normalized = username?.trim()?.takeIf(String::isNotEmpty)?.lowercase() ?: return ""
        val serialized = store.getString(UserTagKeys.TAGS)
        // Recheck the stored value so imports and other repository instances are visible too.
        val snapshot = lookupSnapshot?.takeIf { it.serialized == serialized }
            ?: LookupSnapshot(serialized, UserTagCodec.decode(serialized, normalizeUsernames = true))
                .also { lookupSnapshot = it }
        return snapshot.tags[normalized].orEmpty()
    }

    fun setTag(username: String?, tag: String?) {
        val serialized = UserTagCodec.update(store.getString(UserTagKeys.TAGS), username, tag)
            ?: return
        store.putString(UserTagKeys.TAGS, serialized)
    }

    private data class LookupSnapshot(val serialized: String?, val tags: Map<String, String>)
}
