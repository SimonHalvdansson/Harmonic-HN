package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.network.NitterInstance
import com.simon.harmonichackernews.utils.ArchiveRedirectPolicy
import kotlinx.serialization.json.*

sealed interface SettingsImportResult {
    data class Imported(val count: Int, val skipped: Int) : SettingsImportResult
    data object Invalid : SettingsImportResult
    data object UnsupportedVersion : SettingsImportResult
}

/**
 * Version 1 is an allowlisted settings document, never a dump of host preferences.
 * Wire IDs are a compatibility contract: retain them (or add an explicit migration) when storage
 * keys or UI labels change. Additive fields do not need a new version; incompatible shapes do.
 * Null means restore the default. Missing, unknown and invalid entries leave the device unchanged.
 * Credentials, AI endpoints/model selections, notifications, debug faults and user content stay local.
 */
class SettingsTransfer(private val store: KeyValueStore, private val scheduleStore: KeyValueStore) {
    fun export(): String = json.encodeToString(JsonObject.serializer(), buildJsonObject {
        put("format", FORMAT)
        put("version", VERSION)
        put("settings", exportValues(store, rules))
        put("nighttime", exportValues(scheduleStore, scheduleRules))
    })

    fun import(content: String): SettingsImportResult {
        if (content.length > MAX_CHARS || !hasBoundedNesting(content)) return SettingsImportResult.Invalid
        val root = try { json.parseToJsonElement(content) as? JsonObject } catch (_: Exception) { null }
            ?: return SettingsImportResult.Invalid
        if (root["format"] != JsonPrimitive(FORMAT)) return SettingsImportResult.Invalid
        val version = root["version"] as? JsonPrimitive ?: return SettingsImportResult.Invalid
        if (version.isString || version.intOrNull == null) return SettingsImportResult.Invalid
        if (version.intOrNull != VERSION) return SettingsImportResult.UnsupportedVersion
        val values = root["settings"] as? JsonObject ?: return SettingsImportResult.Invalid
        val nighttime = root["nighttime"]?.let { it as? JsonObject ?: return SettingsImportResult.Invalid }
            ?: JsonObject(emptyMap())
        // Stage every value first. No untrusted data reaches a store editor.
        val staged = validate(values, rules)
        val stagedNighttime = validate(nighttime, scheduleRules)
        apply(scheduleStore, stagedNighttime)
        apply(store, staged)
        return SettingsImportResult.Imported(staged.size + stagedNighttime.size,
            values.size + nighttime.size - staged.size - stagedNighttime.size)
    }

    private fun exportValues(source: KeyValueStore, schema: Map<String, Rule>) = buildJsonObject {
        schema.forEach { (id, rule) ->
            val value = if (!source.contains(id)) JsonNull else
                runCatching { rule.read(source, id) }.getOrNull()?.let(rule.validate)
            // Do not export corrupt persisted values as a request to reset another device.
            if (value != null) put(id, value)
        }
        if (source === store) {
            // Resolve read-through theme migration so backups do not depend on legacy key names.
            val appearance = StoredUserSettings(store, kotlinx.coroutines.flow.emptyFlow()).appearance
            put(ThemePreferences.FOLLOW_SYSTEM_KEY, appearance.followSystem)
            put(ThemePreferences.MANUAL_DARK_KEY, appearance.manualDark)
            val schemes = appearance.colorSchemes
            put(ColorSchemePreferences.LIGHT_KEY, schemes.light)
            put(ColorSchemePreferences.DARK_KEY, schemes.dark)
            put(ColorSchemePreferences.NIGHTTIME_KEY, schemes.nighttime)
            put(ColorSchemePreferences.COUPLED_KEY, schemes.coupled)
            put(ColorSchemePreferences.LIGHT_STYLE_KEY, schemes.lightStyle.storedValue)
            put(ColorSchemePreferences.DARK_STYLE_KEY, schemes.darkStyle.storedValue)
            put(ColorSchemePreferences.NIGHTTIME_STYLE_KEY, schemes.nighttimeStyle.storedValue)
        }
    }

    private fun validate(values: JsonObject, schema: Map<String, Rule>): Map<String, Pair<Rule, JsonElement>> =
        buildMap {
            values.forEach { (id, value) ->
                val rule = schema[id] ?: return@forEach
                val safe = if (value == JsonNull) JsonNull else rule.validate(value) ?: return@forEach
                put(id, rule to safe)
            }
        }

    private fun apply(target: KeyValueStore, values: Map<String, Pair<Rule, JsonElement>>) {
        if (values.isEmpty()) return
        target.update {
            values.forEach { (id, entry) ->
                if (entry.second == JsonNull) remove(id) else entry.first.write(this, id, entry.second)
            }
        }
    }

    companion object {
        const val MAX_CHARS = 256 * 1024
        private const val FORMAT = "harmonic-settings"
        private const val VERSION = 1
        private val json = Json { prettyPrint = true }

        // Bound parser stack use even for malformed or deeply nested unknown fields.
        private fun hasBoundedNesting(text: String): Boolean {
            var depth = 0
            var quoted = false
            var escaped = false
            for (char in text) {
                if (quoted) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') quoted = false
                } else when (char) {
                    '"' -> quoted = true
                    '{', '[' -> if (++depth > 12) return false
                    '}', ']' -> if (--depth < 0) return false
                }
            }
            return depth == 0 && !quoted
        }

        private val scheduleRules = mapOf(
            NighttimeScheduleKeys.FROM_HOUR to numberString(0f..23f, integer = true),
            NighttimeScheduleKeys.FROM_MINUTE to numberString(0f..59f, integer = true),
            NighttimeScheduleKeys.TO_HOUR to numberString(0f..23f, integer = true),
            NighttimeScheduleKeys.TO_MINUTE to numberString(0f..59f, integer = true),
        )

        private val rules: Map<String, Rule> = buildMap {
            (StoryBooleanPreference.entries.map { it.storageKey } +
                CommentBooleanPreference.entries.map { it.storageKey } +
                ReadingBooleanPreference.entries.map { it.storageKey } +
                AppearanceBooleanPreference.entries.map { it.storageKey } +
                GeneralBooleanPreference.entries.map { it.storageKey } +
                LinkPreviewType.entries.map { it.preferenceKey } + listOf(
                    UserPreferenceKeys.EXPAND_COLLECTED_LINKS, UserPreferenceKeys.USER_AVATARS_ENABLED,
                    UserPreferenceKeys.MONOCHROME_COMMENT_DEPTH,
                    UserPreferenceKeys.PALETTE_TINT_AVOID_BACKGROUND_COLOR,
                    ThemePreferences.FOLLOW_SYSTEM_KEY, ThemePreferences.MANUAL_DARK_KEY,
                    ColorSchemePreferences.COUPLED_KEY,
                    AiSummaryPreferenceKeys.STREAM_RESPONSES, AiSummaryPreferenceKeys.AUTO_SUMMARIZE_ARTICLES,
                    AiSummaryPreferenceKeys.ENABLE_BOLD_FORMATTING, AiSummaryPreferenceKeys.SHOW_ADDITIONAL_INFO,
                )).forEach { put(it, booleanRule) }
            put(UserPreferenceKeys.FONT, choice(AppFont.entries.map { it.storedValue }))
            put(UserPreferenceKeys.READER_MODE_FONT, choice(AppFont.entries.map { it.storedValue }))
            put(UserPreferenceKeys.STORY_TEXT_SIZE, numberString(TextPreferences.MIN_STORY_TEXT_SIZE..TextPreferences.MAX_STORY_TEXT_SIZE))
            put(UserPreferenceKeys.COMMENT_TEXT_SIZE, numberString(TextPreferences.MIN_COMMENT_TEXT_SIZE..TextPreferences.MAX_COMMENT_TEXT_SIZE))
            put(UserPreferenceKeys.READER_MODE_FONT_SIZE, intRule(TextPreferences.MIN_READER_MODE_FONT_SIZE..TextPreferences.MAX_READER_MODE_FONT_SIZE))
            put(UserPreferenceKeys.PALETTE_TINT_STRENGTH, intRule(PaletteTintPreferences.MIN_STRENGTH..PaletteTintPreferences.MAX_STRENGTH))
            put(UserPreferenceKeys.PALETTE_TINT_COLORFULNESS, intRule(PaletteTintPreferences.MIN_COLORFULNESS..PaletteTintPreferences.MAX_COLORFULNESS))
            put(UserPreferenceKeys.PALETTE_TINT_TONE, intRule(PaletteTintPreferences.MIN_TONE..PaletteTintPreferences.MAX_TONE))
            put(UserPreferenceKeys.PALETTE_TINT_MODE, choice(listOf(PaletteTintPreferences.MUTED, PaletteTintPreferences.DOMINANT, PaletteTintPreferences.VIBRANT)))
            put(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, floatRule(SplitRatioPreferences.Range))
            put(UserPreferenceKeys.SPLIT_RATIO_LANDSCAPE, floatRule(SplitRatioPreferences.Range))
            put(UserPreferenceKeys.STORIES_TO_CACHE, intRule(StoryCachePreferences.MIN_COUNT..StoryCachePreferences.MAX_COUNT))
            put(UserPreferenceKeys.PRELOAD_WEBVIEW_MINIMUM_BATTERY, intRule(0..100))
            put(UserPreferenceKeys.PRELOAD_COMMENTS_MINIMUM_BATTERY, intRule(0..100))
            put(UserPreferenceKeys.PRELOAD_WEBVIEW, choice(WebViewPreloadMode.entries.map { it.storedValue }))
            put(UserPreferenceKeys.PRELOAD_COMMENTS_MODE, choice(WebViewPreloadMode.entries.map { it.storedValue }))
            put(UserPreferenceKeys.STORY_DISPLAY_STYLE, choice(DisplayStyle.entries.map { it.storedValue } + "card"))
            put(UserPreferenceKeys.COMMENT_DISPLAY_STYLE, choice(DisplayStyle.entries.map { it.storedValue } + "card"))
            put(UserPreferenceKeys.STORY_PREVIEW_IMAGE_MODE, choice(StoryPreviewMode.entries.map { it.storedValue }))
            put(UserPreferenceKeys.COMMENT_SORTING, choice(CommentSortingPreference.entries.map { it.storedValue }))
            put(UserPreferenceKeys.COMMENTS_PROVIDER, choice(CommentsProvider.entries.map { it.storedValue }))
            put(UserPreferenceKeys.COMMENTS_VOLUME_NAVIGATION, choice(CommentVolumeNavigationMode.entries.map { it.storedValue }))
            put(UserPreferenceKeys.COMMENT_INDICATOR_THICKNESS, choice(CommentIndicatorThickness.entries.map { it.storedValue }))
            put(UserPreferenceKeys.EXTRA_SIDE_PADDING, choice(ExtraSidePadding.entries.map { it.storedValue }))
            put(UserPreferenceKeys.STORY_LIST_SELECTOR, choice(StoryListSelector.entries.map { it.storedValue }))
            put(UserPreferenceKeys.FAVICON_PROVIDER, choice(FaviconProviderCatalog.options.map { it.value }))
            put(UserPreferenceKeys.HOTNESS, numberString(-1f..10000f, integer = true))
            put(UserPreferenceKeys.DEFAULT_STORY_TYPE, choice(StoryType.entries.filter { it != StoryType.UNKNOWN }.map { it.label }))
            put(UserPreferenceKeys.FRONTPAGE_ORDER, stringRule { StoryTypeMenuPolicy.sanitizeOrder(it.split(',')).joinToString(",") })
            put(UserPreferenceKeys.ADDITIONAL_FRONTPAGES, stringSetRule)
            listOf(UserPreferenceKeys.COMMENT_DEPTH_INDICATORS, UserPreferenceKeys.LAST_ENABLED_COMMENT_DEPTH_INDICATORS).forEach {
                put(it, choice(listOf("theme_default", "material_you", "colors", "author", "monochrome", "none")))
            }
            put(UserPreferenceKeys.USER_AVATAR_OPTIONS, stringRule { UserAvatarOptions.decode(it).encode() })
            put(UserPreferenceKeys.NITTER_INSTANCE_URL, stringRule { NitterInstance.normalize(it) })
            put(UserPreferenceKeys.ARCHIVE_REDIRECT_DOMAINS, stringRule { ArchiveRedirectPolicy.parseDomains(it).joinToString(",") })
            listOf(ColorSchemePreferences.LIGHT_KEY, ColorSchemePreferences.DARK_KEY, ColorSchemePreferences.NIGHTTIME_KEY).forEach {
                put(it, choice(ColorSchemePreferences.values))
            }
            listOf(ColorSchemePreferences.LIGHT_STYLE_KEY, ColorSchemePreferences.DARK_STYLE_KEY, ColorSchemePreferences.NIGHTTIME_STYLE_KEY).forEach {
                put(it, choice(ColorSchemeStyle.entries.map { style -> style.storedValue }))
            }
            put(SurfaceEffectMode.STORAGE_KEY, choice(SurfaceEffectMode.entries.map { it.storedValue }))
            GlassParameter.entries.forEach { put(it.storageKey, floatRule(it.range)) }
            GlassSwitch.entries.forEach { put(it.storageKey, booleanRule) }
            put(GlassSurfaceProfile.STORAGE_KEY, choice(GlassSurfaceProfile.entries.map { it.name }))
            put(AiSummaryPreferenceKeys.GEMINI_NANO_SUMMARY_MODE, choice(GeminiNanoSummaryMode.entries.map { it.storedValue }))
            put(AiSummaryPreferenceKeys.SYSTEM_PROMPT, stringRule(16000) { it.takeIf(String::isNotBlank) })
        }
    }
}

private class Rule(
    val read: (KeyValueStore, String) -> JsonElement,
    val validate: (JsonElement) -> JsonElement?,
    val write: (KeyValueStore.Editor, String, JsonElement) -> Unit,
)

private val booleanRule = Rule(
    { store, key -> JsonPrimitive(store.getBoolean(key, false)) },
    { value -> (value as? JsonPrimitive)?.takeIf { !it.isString && it.booleanOrNull != null } },
    { editor, key, value -> editor.putBoolean(key, value.jsonPrimitive.boolean) },
)

private fun intRule(range: IntRange) = Rule(
    { store, key -> JsonPrimitive(store.getInt(key, 0)) },
    { value -> (value as? JsonPrimitive)?.takeIf { !it.isString && it.intOrNull?.let { n -> n in range } == true } },
    { editor, key, value -> editor.putInt(key, value.jsonPrimitive.int) },
)

private fun floatRule(range: ClosedFloatingPointRange<Float>) = Rule(
    { store, key -> JsonPrimitive(store.getFloat(key, 0f)) },
    { value -> (value as? JsonPrimitive)?.takeIf { !it.isString && it.floatOrNull?.let { n -> n.isFinite() && n in range } == true } },
    { editor, key, value -> editor.putFloat(key, value.jsonPrimitive.float) },
)

private fun stringRule(maxLength: Int = 4096, normalize: (String) -> String?) = Rule(
    { store, key -> JsonPrimitive(store.getString(key).orEmpty()) },
    { value -> (value as? JsonPrimitive)?.takeIf { it.isString && it.content.length <= maxLength }
        ?.content?.let(normalize)?.let(::JsonPrimitive) },
    { editor, key, value -> editor.putString(key, value.jsonPrimitive.content) },
)

private fun choice(values: List<String>) = stringRule { it.takeIf { value -> value in values } }

private fun numberString(range: ClosedFloatingPointRange<Float>, integer: Boolean = false) = stringRule {
    val number = it.toFloatOrNull()
    it.takeIf { number != null && number.isFinite() && number in range && (!integer || it.toIntOrNull() != null) }
}

private val stringSetRule = Rule(
    { store, key -> JsonArray(store.getStringSet(key).sorted().map(::JsonPrimitive)) },
    { value -> (value as? JsonArray)?.takeIf { array -> array.size <= 32 && array.all {
        it is JsonPrimitive && it.isString && it.content in AdditionalFrontpagePreferences.labels
    } } },
    { editor, key, value -> editor.putStringSet(key, value.jsonArray.map { it.jsonPrimitive.content }.toSet()) },
)
