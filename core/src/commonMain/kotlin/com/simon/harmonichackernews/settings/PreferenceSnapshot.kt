package com.simon.harmonichackernews.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Lossless local test snapshots, deliberately separate from portable user backups. */
@Serializable
data class PreferenceSnapshot(
    val version: Int = 1,
    val settings: Map<String, StoredPreferenceValue>,
    val nighttime: Map<String, StoredPreferenceValue>,
) {
    fun encode(): String = codec.encodeToString(this)

    companion object {
        private val codec = Json { encodeDefaults = true }

        fun decode(content: String): PreferenceSnapshot {
            require(content.length <= 1_048_576) { "Snapshot is too large" }
            return codec.decodeFromString<PreferenceSnapshot>(content).also {
                require(it.version == 1) { "Unsupported snapshot version" }
                require(it.settings.keys.all(DevelopmentSettingsScope::isSetting)) { "Snapshot contains non-settings keys" }
                require(it.nighttime.keys.all(DevelopmentSettingsScope::isNighttime)) { "Snapshot contains non-schedule keys" }
                (it.settings.values + it.nighttime.values).forEach(StoredPreferenceValue::decoded)
            }
        }
    }
}

/** Retains DataStore's primitive types, including long values and empty string sets. */
@Serializable
data class StoredPreferenceValue(val type: String, val value: JsonElement) {
    fun decoded(): Any = when (type) {
        "boolean" -> primitive().also { require(!it.isString) }.boolean
        "int" -> primitive().also { require(!it.isString) }.int
        "long" -> primitive().also { require(!it.isString) }.long
        "float" -> primitive().also { require(!it.isString) }.float.also { require(it.isFinite()) }
        "string" -> primitive().also { require(it.isString) }.content
        "string-set" -> (value as? JsonArray ?: error("Expected string array")).map {
            (it as? JsonPrimitive ?: error("Expected string")).also { item -> require(item.isString) }.content
        }.toSet()
        else -> error("Unsupported preference type: $type")
    }

    private fun primitive() = value as? JsonPrimitive ?: error("Expected primitive value")

    companion object {
        fun from(value: Any): StoredPreferenceValue = when (value) {
            is Boolean -> StoredPreferenceValue("boolean", JsonPrimitive(value))
            is Int -> StoredPreferenceValue("int", JsonPrimitive(value))
            is Long -> StoredPreferenceValue("long", JsonPrimitive(value))
            is Float -> StoredPreferenceValue("float", JsonPrimitive(value))
            is String -> StoredPreferenceValue("string", JsonPrimitive(value))
            is Set<*> -> StoredPreferenceValue("string-set", JsonArray(value.map {
                JsonPrimitive(it as? String ?: error("Expected string set"))
            }.sortedBy { it.content }))
            else -> error("Unsupported stored preference value")
        }
    }
}

/** Only preference namespaces; never global account, history, cache or user-tag records. */
object DevelopmentSettingsScope {
    fun isSetting(key: String): Boolean =
        (key.startsWith("pref_") || key.startsWith("debug_") || key == "show_widget_debug_info") &&
            key != "pref_ai_summary_api_key"

    fun isNighttime(key: String): Boolean = key in setOf(
        NighttimeScheduleKeys.FROM_HOUR, NighttimeScheduleKeys.FROM_MINUTE,
        NighttimeScheduleKeys.TO_HOUR, NighttimeScheduleKeys.TO_MINUTE,
    )
}
