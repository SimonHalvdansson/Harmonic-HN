package com.simon.harmonichackernews.settings

import kotlin.concurrent.Volatile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged

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

data class UserTagsSnapshot(
    val tags: Map<String, String>,
    val colors: Map<String, UserTagColor>,
    internal val legacyFallbacks: Set<String> = emptySet(),
) {
    internal fun storedUsername(username: String?): String? {
        val exact = username?.trim() ?: return null
        if (exact in tags) return exact
        return if (legacyFallbacks.isEmpty()) null else exact.lowercase().takeIf { it in legacyFallbacks }
    }

    fun tagFor(username: String?): String = tags[storedUsername(username)].orEmpty()
    fun colorFor(username: String?): UserTagColor = colors[storedUsername(username)] ?: UserTagColor.Theme
}

/** Exact account tags take precedence over the compatibility lookup for old lowercase keys. */
class UserTagsRepository(private val store: KeyValueStore) {
    @Volatile
    private var lookupSnapshot: LookupSnapshot? = null

    private val revision = MutableStateFlow(0L)
    fun current(): UserTagsSnapshot {
        val view = snapshot().view
        return UserTagsSnapshot(view.tags.toMap(), view.colors.toMap(), view.legacyFallbacks.toSet())
    }
    val updates = revision.map { current() }.distinctUntilChanged()

    fun tags(): Map<String, String> = snapshot().view.tags.toMap()
    fun colors(): Map<String, UserTagColor> = snapshot().view.colors.toMap()
    fun tagFor(username: String?): String = snapshot().view.tagFor(username)
    fun colorFor(username: String?): UserTagColor = snapshot().view.colorFor(username)

    fun legacyTags(): List<LegacyUserTag> {
        val snapshot = snapshot()
        return snapshot.reviewable.sorted().map { username ->
            LegacyUserTag(username, snapshot.view.tags.getValue(username), snapshot.view.colors.getValue(username))
        }
    }

    fun resolveLegacyTag(legacyUsername: String, exactUsername: String): LegacyTagResolution {
        val exact = exactUsername.trim()
        if (exact.isEmpty() || !exact.equals(legacyUsername, ignoreCase = true)) {
            return LegacyTagResolution.INVALID_USERNAME
        }
        val source = sources()
        val previous = snapshot(source)
        if (legacyUsername !in previous.reviewable) return LegacyTagResolution.NOT_FOUND
        if (exact != legacyUsername && exact in previous.view.tags) return LegacyTagResolution.TARGET_EXISTS
        if (UserTagCodec.decodeOrNull(source.legacyColors) == null) return LegacyTagResolution.INVALID_DATA
        val saved = writeTag(source, exact, previous.view.tags.getValue(legacyUsername),
            previous.view.colors.getValue(legacyUsername), source.removed + legacyUsername)
        return if (saved) LegacyTagResolution.RESOLVED else LegacyTagResolution.INVALID_DATA
    }

    fun discardLegacyTag(legacyUsername: String): Boolean {
        val source = sources()
        if (legacyUsername !in snapshot(source).reviewable) return false
        return writeTag(source, legacyUsername, null, UserTagColor.Theme, source.removed + legacyUsername)
    }

    /** Text-only edits retain this exact identity's color; deletion suppresses its archive too. */
    fun setTag(username: String?, tag: String?, color: UserTagColor? = null): Boolean {
        val exact = username?.trim()?.takeIf(String::isNotEmpty) ?: return false
        val source = sources()
        val previous = snapshot(source)
        val value = tag?.trim()?.takeIf(String::isNotEmpty)
        val stored = previous.view.storedUsername(exact)
        val removed = when {
            // Editing or deleting a tag displayed through the fallback resolves the old key.
            stored != null && stored != exact -> source.removed + stored
            value == null && exact in UserTagCodec.decode(source.legacyTags) -> source.removed + exact
            value == null && exact.lowercase() in previous.reviewable -> source.removed + exact
            else -> source.removed
        }
        return writeTag(source, exact, value, color ?: previous.view.colorFor(exact), removed)
    }

    private fun writeTag(source: Sources, exact: String, value: String?, color: UserTagColor, removed: Set<String>): Boolean {
        val exactTags = UserTagCodec.decodeOrNull(source.exactTags) ?: return false
        val exactColors = UserTagCodec.decodeOrNull(source.exactColors) ?: return false
        if (value == null) exactTags.remove(exact) else exactTags[exact] = value
        if (value == null || color == UserTagColor.Theme) exactColors.remove(exact)
        else exactColors[exact] = color.storageValue
        store.update {
            putString(UserTagKeys.EXACT_TAGS, UserTagCodec.encode(exactTags))
            putString(UserTagKeys.EXACT_COLORS, UserTagCodec.encode(exactColors.filter { (key, savedColor) ->
                exactTags[key]?.isNotBlank() == true && UserTagColor.fromStorage(savedColor) != UserTagColor.Theme
            }))
            if (removed != source.removed) putStringSet(UserTagKeys.REVIEWED_LEGACY_TAGS, removed)
        }
        revision.update { it + 1 }
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
            val legacyTags = UserTagCodec.decode(source.legacyTags)
            val tags = legacyTags.filterKeys { it !in source.removed } + exactTags
            val colors = tags.mapValues { (username, tag) ->
                if (tag.isBlank()) UserTagColor.Theme else UserTagColor.fromStorage(
                    // An exact tag and its color form one override; absent means Theme.
                    if (username in exactTags) exactColors[username] else legacyColors[username],
                )
            }
            // Older writers folded every username to lowercase. Those keys have no provenance
            // until the user edits, confirms or removes the archived tag.
            val reviewable = tags.keys.filterTo(mutableSetOf()) { name ->
                name == name.lowercase() && name.any(Char::isLetter) && name !in exactTags && tags.getValue(name).isNotBlank()
            }
            // A second spelling in either archive, later edits, or deletions is evidence
            // of distinct identities. In that family only an exact match is safe.
            val fallbacks = reviewable.toMutableSet()
            for (name in legacyTags.keys + exactTags.keys + source.removed) {
                if (name != name.lowercase()) fallbacks.remove(name.lowercase())
            }
            LookupSnapshot(source, UserTagsSnapshot(tags, colors, fallbacks), reviewable).also { lookupSnapshot = it }
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
        val view: UserTagsSnapshot,
        val reviewable: Set<String>,
    )
}
