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
}
