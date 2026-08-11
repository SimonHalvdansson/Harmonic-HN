package com.simon.harmonichackernews.settings

object UserTagKeys {
    const val TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
}

/** Platform-neutral persistence and case-insensitive lookup for user labels. */
class UserTagsRepository(private val store: KeyValueStore) {
    fun tags(normalizeUsernames: Boolean = true): Map<String, String> =
        UserTagCodec.decode(store.getString(UserTagKeys.TAGS), normalizeUsernames)

    fun tagFor(username: String?): String =
        UserTagCodec.tagFor(store.getString(UserTagKeys.TAGS), username)

    fun setTag(username: String?, tag: String?) {
        val serialized = UserTagCodec.update(store.getString(UserTagKeys.TAGS), username, tag)
            ?: return
        store.putString(UserTagKeys.TAGS, serialized)
    }
}
