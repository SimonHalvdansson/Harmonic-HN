package com.simon.harmonichackernews.settings

import kotlin.concurrent.Volatile

object UserTagKeys {
    // Preserve the original raw sources while overlaying exact-identity edits.
    const val TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS"
    const val COLORS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAG_COLORS"
    const val EXACT_TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS_EXACT_V1"
    const val EXACT_COLORS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAG_COLORS_EXACT_V1"
    // Existing reviewed markers also serve as tombstones for removed archived labels.
    const val REVIEWED_LEGACY_TAGS = "com.simon.harmonichackernews.KEY_SHARED_PREFERENCES_USER_TAGS_LEGACY_REVIEWED_V1"
}

data class LegacyUserTag(
    val username: String,
    val tag: String,
    val color: UserTagColor = UserTagColor.Theme,
)

enum class LegacyTagResolution { RESOLVED, NOT_FOUND, INVALID_USERNAME, TARGET_EXISTS, INVALID_DATA }

/** Stored username spelling is authoritative; different case spellings remain separate identities. */
class UserTagsRepository(private val store: KeyValueStore) {
    @Volatile
    private var lookupSnapshot: LookupSnapshot? = null

    fun tags(): Map<String, String> = snapshot().tags.toMap()

    fun tagFor(username: String?): String {
        val exact = username?.trim()?.takeIf(String::isNotEmpty) ?: return ""
        return snapshot().tags[exact].orEmpty()
    }

    fun colorFor(username: String?): UserTagColor {
        val exact = username?.trim()?.takeIf(String::isNotEmpty) ?: return UserTagColor.Theme
        return snapshot().colors[exact] ?: UserTagColor.Theme
    }

    fun colors(): Map<String, UserTagColor> = snapshot().colors.toMap()

    // Kept compatible with older callers; stored spellings no longer require a review workflow.
    fun legacyTags(): List<LegacyUserTag> = emptyList()
    fun resolveLegacyTag(legacyUsername: String, exactUsername: String): LegacyTagResolution =
        LegacyTagResolution.NOT_FOUND
    fun discardLegacyTag(legacyUsername: String): Boolean = false

    /** Text-only edits retain this exact identity's color; deletion suppresses its archive too. */
    fun setTag(username: String?, tag: String?, color: UserTagColor? = null): Boolean {
        val exact = username?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val source = sources()
        val exactTags = UserTagCodec.decodeOrNull(source.exactTags)?.toMutableMap() ?: return false
        val exactColors = UserTagCodec.decodeOrNull(source.exactColors)?.toMutableMap() ?: return false
        val previous = snapshot(source)
        val value = tag?.trim()?.takeIf(String::isNotEmpty)
        var removed = source.removed
        if (value == null) {
            exactTags.remove(exact)
            exactColors.remove(exact)
            if (exact in UserTagCodec.decode(source.legacyTags)) removed = removed + exact
        } else {
            exactTags[exact] = value
            val selectedColor = color ?: previous.colors[exact] ?: UserTagColor.Theme
            if (selectedColor == UserTagColor.Theme) exactColors.remove(exact)
            else exactColors[exact] = selectedColor.storageValue
        }
        store.update {
            putString(UserTagKeys.EXACT_TAGS, UserTagCodec.encode(exactTags))
            putString(UserTagKeys.EXACT_COLORS, UserTagCodec.encode(exactColors.filter { (key, savedColor) ->
                exactTags[key]?.isNotBlank() == true && UserTagColor.fromStorage(savedColor) != UserTagColor.Theme
            }))
            if (removed != source.removed) putStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS, removed)
        }
        return true
    }

    private fun sources() = Sources(
        exactTags = store.getString(UserTagKeys.EXACT_TAGS),
        exactColors = store.getString(UserTagKeys.EXACT_COLORS),
        legacyTags = store.getString(UserTagKeys.TAGS),
        legacyColors = store.getString(UserTagKeys.COLORS),
        removed = store.getStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS),
    )

    private fun snapshot(source: Sources = sources()): LookupSnapshot =
        lookupSnapshot?.takeIf { it.source == source } ?: run {
            val exactTags = UserTagCodec.decode(source.exactTags)
            val exactColors = UserTagCodec.decode(source.exactColors)
            val legacyColors = UserTagCodec.decode(source.legacyColors)
            val tags = UserTagCodec.decode(source.legacyTags)
                .filterKeys { it !in source.removed } + exactTags
            val colors = tags.mapValues { (username, tag) ->
                if (tag.isBlank()) UserTagColor.Theme else UserTagColor.fromStorage(
                    // An exact tag and its color form one override; absent means Theme.
                    if (username in exactTags) exactColors[username] else legacyColors[username],
                )
            }
            LookupSnapshot(source, tags, colors).also { lookupSnapshot = it }
        }

    private data class Sources(
        val exactTags: String?,
        val exactColors: String?,
        val legacyTags: String?,
        val legacyColors: String?,
        val removed: Set<String>,
    )

    private data class LookupSnapshot(
        val source: Sources,
        val tags: Map<String, String>,
        val colors: Map<String, UserTagColor>,
    )
}
