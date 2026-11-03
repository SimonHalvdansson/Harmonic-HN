package com.simon.harmonichackernews.settings

import kotlin.concurrent.Volatile

object ContentFilterKeys {
    const val WORDS = "pref_filter"
    const val DOMAINS = "pref_filter_domains"
    const val TITLE_RULES = "pref_filter_title_rules_v1"
    const val DOMAIN_RULES = "pref_filter_domain_rules_v1"
    const val USERS = "pref_filter_users"
    // Released versions lowercased USERS. Keep that raw source for explicit identity review.
    const val EXACT_USERS = "pref_filter_users_exact_v1"
    const val REVIEWED_LEGACY_USERS = "pref_filter_users_reviewed_legacy_v1"
}

data class ContentFilters(
    val words: List<String> = emptyList(),
    val domains: List<String> = emptyList(),
    val users: Set<String> = emptySet(),
    val titleRules: List<TitleFilterRule> = words.map {
        TitleFilterRule(it, TitleFilterMatchMode.CONTAINS_TEXT)
    },
    val domainRules: List<DomainFilterRule> = domains.map {
        DomainFilterRule(it, legacyContainsText = true)
    },
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
    @Volatile
    private var userLookupSnapshot: UserLookupSnapshot? = null

    init { importLegacyUsers() }

    private fun importLegacyUsers() {
        // Released entries lost case. Use their stored spelling, retaining raw archives and
        // prior removals; new exact entries remain independent of differently cased accounts.
        val reviewed = store.getStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS)
        val legacy = parseList(store.getString(ContentFilterKeys.USERS), lowercase = false).distinct()
        val pending = legacy.filterNot { it in reviewed }
        if (pending.isNotEmpty()) store.update {
            putString(ContentFilterKeys.EXACT_USERS, (storedUsernames() + pending).distinct().joinToString(","))
            putStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS, reviewed + pending)
        }
    }

    fun load(): ContentFilters {
        importLegacyUsers()
        val titles = titleRules()
        val domains = domainRules()
        return ContentFilters(
            words = titles.map(TitleFilterRule::text),
            domains = domains.map(DomainFilterRule::domain),
            users = storedUsernames().toSet(),
            titleRules = titles,
            domainRules = domains,
        )
    }

    // v3.2 shipped comma-separated substring filters. Adapt those lazily and retain each rule's
    // old mode when another rule is edited; merely opening settings must not rewrite preferences.
    fun titleRules(): List<TitleFilterRule> {
        val current = store.getString(ContentFilterKeys.TITLE_RULES)
        if (current != null) return ContentFilterRuleCodec.decodeTitleRules(current).orEmpty()
        return parseList(store.getString(ContentFilterKeys.WORDS), lowercase = false).map {
            TitleFilterRule(it, TitleFilterMatchMode.CONTAINS_TEXT)
        }
    }

    fun domainRules(): List<DomainFilterRule> {
        val current = store.getString(ContentFilterKeys.DOMAIN_RULES)
        if (current != null) return ContentFilterRuleCodec.decodeDomainRules(current).orEmpty()
        return parseList(store.getString(ContentFilterKeys.DOMAINS), lowercase = false).map {
            DomainFilterRule(it, legacyContainsText = true)
        }
    }

    /** Unknown or damaged structured data stays intact; do not revive stale legacy preferences. */
    fun canEditTitleRules(): Boolean = store.getString(ContentFilterKeys.TITLE_RULES)?.let {
        ContentFilterRuleCodec.decodeTitleRules(it) != null
    } ?: true

    fun canEditDomainRules(): Boolean = store.getString(ContentFilterKeys.DOMAIN_RULES)?.let {
        ContentFilterRuleCodec.decodeDomainRules(it) != null
    } ?: true

    fun setTitleRules(rules: List<TitleFilterRule>): Boolean {
        if (!canEditTitleRules()) return false
        store.putString(ContentFilterKeys.TITLE_RULES, ContentFilterRuleCodec.encodeTitleRules(rules))
        return true
    }

    fun setDomainRules(rules: List<DomainFilterRule>): Boolean {
        if (!canEditDomainRules()) return false
        store.putString(ContentFilterKeys.DOMAIN_RULES, ContentFilterRuleCodec.encodeDomainRules(rules))
        return true
    }

    fun containsUser(username: String?): Boolean {
        val normalized = normalizeUsername(username) ?: return false
        val serialized = store.getString(ContentFilterKeys.EXACT_USERS)
        // Cache only private lookup data; load() still returns independent editable collections.
        val snapshot = userLookupSnapshot?.takeIf { it.serialized == serialized }
            ?: UserLookupSnapshot(serialized, parseList(serialized, lowercase = false).toSet())
                .also { userLookupSnapshot = it }
        return normalized in snapshot.users
    }

    fun items(type: ContentFilterType): List<String> = when (type) {
        ContentFilterType.STORY_TITLE -> load().words
        ContentFilterType.DOMAIN -> load().domains
        // HN IDs are case-sensitive; the same exact ID is used for blocking and profile URLs.
        ContentFilterType.USER -> storedUsernames()
    }

    fun setItems(type: ContentFilterType, items: List<String>) {
        val normalized = items.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        // This compatibility entry point retains the old list editor's substring semantics.
        // New rule editors use the typed setters and choose their matching mode explicitly.
        if (type == ContentFilterType.STORY_TITLE) {
            setTitleRules(normalized.map { TitleFilterRule(it, TitleFilterMatchMode.CONTAINS_TEXT) })
            return
        }
        if (type == ContentFilterType.DOMAIN) {
            setDomainRules(normalized.map { DomainFilterRule(it, legacyContainsText = true) })
            return
        }
        store.putString(type.key, normalized.joinToString(","))
    }

    fun toggleUser(username: String?): UserBlockUpdate? {
        val normalized = normalizeUsername(username) ?: return null
        val wasBlocked = containsUser(normalized)
        val changed = if (wasBlocked) removeUser(normalized) else addUser(username)
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
        val users = storedUsernames()
        if (normalized !in users) {
            saveUsers(users + normalized)
        }
        return true
    }

    fun removeUser(username: String?): Boolean {
        val normalized = normalizeUsername(username) ?: return false
        saveUsers(storedUsernames().filterNot { it == normalized })
        return true
    }

    private fun storedUsernames(): List<String> =
        parseList(store.getString(ContentFilterKeys.EXACT_USERS), lowercase = false).distinct()

    private fun saveUsers(users: List<String>) {
        store.putString(ContentFilterKeys.EXACT_USERS, users.joinToString(","))
    }

    /** Compatibility for older callers: stored spellings are activated automatically. */
    fun legacyUsers(): List<String> {
        importLegacyUsers()
        return emptyList()
    }

    fun resolveLegacyUser(legacyUsername: String, exactUsername: String): Boolean {
        val legacy = legacyUsername.trim()
        val exact = normalizeUsername(exactUsername) ?: return false
        if (',' in exact || legacy !in legacyUsers()) return false
        val users = (storedUsernames() + exact).distinct()
        val reviewed = store.getStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS) + legacy
        store.update {
            putString(ContentFilterKeys.EXACT_USERS, users.joinToString(","))
            putStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS, reviewed)
        }
        return true
    }

    fun discardLegacyUser(username: String): Boolean {
        val legacy = username.trim()
        if (legacy !in legacyUsers()) return false
        store.putStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS,
            store.getStringSet(ContentFilterKeys.REVIEWED_LEGACY_USERS) + legacy)
        return true
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
        ?.takeIf { it.isNotEmpty() && ',' !in it }

    private val ContentFilterType.key: String
        get() = when (this) {
            ContentFilterType.STORY_TITLE -> ContentFilterKeys.WORDS
            ContentFilterType.DOMAIN -> ContentFilterKeys.DOMAINS
            ContentFilterType.USER -> ContentFilterKeys.EXACT_USERS
        }

    private data class UserLookupSnapshot(val serialized: String?, val users: Set<String>)
}
