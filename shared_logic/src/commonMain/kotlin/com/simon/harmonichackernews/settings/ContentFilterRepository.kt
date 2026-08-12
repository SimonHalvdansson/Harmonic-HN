package com.simon.harmonichackernews.settings

object ContentFilterKeys {
    const val WORDS = "pref_filter"
    const val DOMAINS = "pref_filter_domains"
    const val USERS = "pref_filter_users"
}

data class ContentFilters(
    val words: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val users: Set<String> = emptySet(),
)

enum class ContentFilterType { STORY_TITLE, DOMAIN, USER }

data class UserBlockUpdate(
    val blocked: Boolean,
    val changed: Boolean,
    val message: String,
    val dismissProfile: Boolean,
)

/** Portable persistence and normalization for story and comment content filters. */
class ContentFilterRepository(
    private val store: KeyValueStore,
) {
    fun load(): ContentFilters = ContentFilters(
        words = parseList(store.getString(ContentFilterKeys.WORDS), lowercase = false),
        domains = parseList(store.getString(ContentFilterKeys.DOMAINS), lowercase = false),
        users = parseList(store.getString(ContentFilterKeys.USERS), lowercase = true).toSet(),
    )

    fun containsUser(username: String?): Boolean {
        val normalized = normalizeUsername(username) ?: return false
        return normalized in load().users
    }

    fun items(type: ContentFilterType): List<String> = when (type) {
        ContentFilterType.STORY_TITLE -> load().words
        ContentFilterType.DOMAIN -> load().domains
        ContentFilterType.USER -> load().users.toList()
    }

    fun setItems(type: ContentFilterType, items: List<String>) {
        val normalized = items.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { if (type == ContentFilterType.USER) it.lowercase() else it }
            .distinct()
            .toList()
        store.putString(type.key, normalized.joinToString(","))
    }

    fun toggleUser(username: String?): UserBlockUpdate? {
        val normalized = normalizeUsername(username) ?: return null
        val wasBlocked = containsUser(normalized)
        val changed = if (wasBlocked) removeUser(normalized) else addUser(normalized)
        return UserBlockUpdate(
            blocked = !wasBlocked && changed,
            changed = changed,
            message = if (wasBlocked) {
                "Unblocked $normalized"
            } else {
                "You will no longer see posts or comments from $normalized"
            },
            dismissProfile = !wasBlocked && changed,
        )
    }

    fun addUser(username: String?): Boolean {
        val normalized = normalizeUsername(username) ?: return false
        val users = load().users.toMutableSet()
        users += normalized
        saveUsers(users)
        return true
    }

    fun removeUser(username: String?): Boolean {
        val normalized = normalizeUsername(username) ?: return false
        val users = load().users.toMutableSet()
        users -= normalized
        saveUsers(users)
        return true
    }

    private fun saveUsers(users: Set<String>) {
        store.putString(ContentFilterKeys.USERS, users.joinToString(","))
    }

    private fun parseList(value: String?, lowercase: Boolean): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.splitToSequence(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { if (lowercase) it.lowercase() else it }
            .toList()
    }

    private fun normalizeUsername(username: String?): String? = username
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.lowercase()

    private val ContentFilterType.key: String
        get() = when (this) {
            ContentFilterType.STORY_TITLE -> ContentFilterKeys.WORDS
            ContentFilterType.DOMAIN -> ContentFilterKeys.DOMAINS
            ContentFilterType.USER -> ContentFilterKeys.USERS
        }
}
