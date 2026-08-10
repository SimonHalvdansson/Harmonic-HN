package com.simon.harmonichackernews.serialization

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray as KotlinxJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject as KotlinxJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Mutable, source-set-portable JSON values backed by kotlinx.serialization's JSON tree.
 *
 * The app historically used Android's legacy JSON API throughout its callback-based networking
 * code. These small adapters retain that API's permissive access behavior while removing the
 * Android/JVM dependency. They can move to commonMain unchanged when the project becomes KMP.
 */
class JsonObject internal constructor(
    private val values: MutableMap<String, JsonElement>,
) {
    constructor() : this(linkedMapOf())

    constructor(source: String?) : this(
        parseElement(source).asObject().toMutableMap(),
    )

    fun get(key: String): Any? = required(key).toJsonValue()

    fun opt(key: String): Any? = values[key]?.toJsonValue()

    fun getString(key: String): String = required(key).requiredString(key)

    fun optString(key: String): String = optString(key, "")

    @Suppress("UNCHECKED_CAST")
    fun <T : String?> optString(key: String, fallback: T): T =
        (values[key].optionalString() ?: fallback) as T

    fun getInt(key: String): Int = required(key).requiredInt(key)

    fun optInt(key: String, fallback: Int = 0): Int = values[key].optionalInt() ?: fallback

    fun getLong(key: String): Long = required(key).requiredLong(key)

    fun optLong(key: String, fallback: Long = 0L): Long = values[key].optionalLong() ?: fallback

    fun getBoolean(key: String): Boolean = required(key).requiredBoolean(key)

    fun optBoolean(key: String, fallback: Boolean = false): Boolean =
        values[key].optionalBoolean() ?: fallback

    fun getJSONObject(key: String): JsonObject =
        values[key].asObjectOrNull()?.let { JsonObject(it.toMutableMap()) }
            ?: throw typeError(key, "object")

    fun optJSONObject(key: String): JsonObject? =
        values[key].asObjectOrNull()?.let { JsonObject(it.toMutableMap()) }

    fun getJSONArray(key: String): JsonArray =
        values[key].asArrayOrNull()?.let { JsonArray(it.toMutableList()) }
            ?: throw typeError(key, "array")

    fun optJSONArray(key: String): JsonArray? =
        values[key].asArrayOrNull()?.let { JsonArray(it.toMutableList()) }

    fun has(key: String): Boolean = values.containsKey(key)

    fun isNull(key: String): Boolean = values[key] == null || values[key] === JsonNull

    fun length(): Int = values.size

    fun keys(): Iterator<String> = values.keys.iterator()

    fun put(key: String, value: Any?): JsonObject = apply {
        values[key] = value.toJsonElement()
    }

    fun remove(key: String): Any? = values.remove(key)?.toJsonValue()

    internal fun toJsonElement(): KotlinxJsonObject = KotlinxJsonObject(values)

    override fun toString(): String = toJsonElement().toString()

    private fun required(key: String): JsonElement =
        values[key]?.takeUnless { it === JsonNull } ?: throw JsonException("Missing value for '$key'")

    private companion object {
        fun typeError(key: String, expected: String) =
            JsonException("Value for '$key' is not a JSON $expected")
    }
}

class JsonArray internal constructor(
    private val values: MutableList<JsonElement>,
) {
    constructor() : this(mutableListOf())

    constructor(source: String?) : this(
        parseElement(source).asArray().toMutableList(),
    )

    fun get(index: Int): Any? = required(index).toJsonValue()

    fun getString(index: Int): String = required(index).requiredString(index.toString())

    fun optString(index: Int, fallback: String = ""): String =
        values.getOrNull(index).optionalString() ?: fallback

    fun getInt(index: Int): Int = required(index).requiredInt(index.toString())

    fun optInt(index: Int, fallback: Int = 0): Int =
        values.getOrNull(index).optionalInt() ?: fallback

    fun getJSONObject(index: Int): JsonObject =
        values.getOrNull(index).asObjectOrNull()?.let { JsonObject(it.toMutableMap()) }
            ?: throw JsonException("Value at $index is not a JSON object")

    fun optJSONObject(index: Int): JsonObject? =
        values.getOrNull(index).asObjectOrNull()?.let { JsonObject(it.toMutableMap()) }

    fun getJSONArray(index: Int): JsonArray =
        values.getOrNull(index).asArrayOrNull()?.let { JsonArray(it.toMutableList()) }
            ?: throw JsonException("Value at $index is not a JSON array")

    fun optJSONArray(index: Int): JsonArray? =
        values.getOrNull(index).asArrayOrNull()?.let { JsonArray(it.toMutableList()) }

    fun put(value: Any?): JsonArray = apply {
        values += value.toJsonElement()
    }

    fun length(): Int = values.size

    internal fun toJsonElement(): KotlinxJsonArray = KotlinxJsonArray(values)

    override fun toString(): String = toJsonElement().toString()

    private fun required(index: Int): JsonElement =
        values.getOrNull(index)?.takeUnless { it === JsonNull }
            ?: throw JsonException("Missing value at $index")
}

class JsonException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object JsonStringCodec {
    /** Decodes the JSON string literal returned by WebView evaluateJavascript. */
    fun decodeJavascriptString(value: String?): String? {
        if (value.isNullOrEmpty() || value == "null") return null
        return runCatching { JsonArray("[$value]").getString(0) }.getOrNull()
    }
}

private fun parseElement(source: String?): JsonElement = try {
    Json.parseToJsonElement(source ?: throw JsonException("JSON source is null"))
} catch (error: SerializationException) {
    throw JsonException("Invalid JSON", error)
} catch (error: IllegalArgumentException) {
    throw JsonException("Invalid JSON", error)
}

private fun JsonElement.asObject(): KotlinxJsonObject =
    asObjectOrNull() ?: throw JsonException("JSON value is not an object")

private fun JsonElement.asArray(): KotlinxJsonArray =
    asArrayOrNull() ?: throw JsonException("JSON value is not an array")

private fun JsonElement?.asObjectOrNull(): KotlinxJsonObject? =
    runCatching { this?.jsonObject }.getOrNull()

private fun JsonElement?.asArrayOrNull(): KotlinxJsonArray? =
    runCatching { this?.jsonArray }.getOrNull()

private fun JsonElement?.optionalString(): String? {
    val primitive = this as? JsonPrimitive ?: return null
    if (primitive === JsonNull) return null
    return primitive.content
}

private fun JsonElement.requiredString(label: String): String =
    optionalString() ?: throw JsonException("Value for '$label' is not a string")

private fun JsonElement?.optionalInt(): Int? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.intOrNull ?: primitive.content.toDoubleOrNull()?.toInt()
}

private fun JsonElement.requiredInt(label: String): Int =
    optionalInt() ?: throw JsonException("Value for '$label' is not an integer")

private fun JsonElement?.optionalLong(): Long? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.longOrNull ?: primitive.doubleOrNull?.toLong()
}

private fun JsonElement.requiredLong(label: String): Long =
    optionalLong() ?: throw JsonException("Value for '$label' is not a long")

private fun JsonElement?.optionalBoolean(): Boolean? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.booleanOrNull ?: when (primitive.content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private fun JsonElement.requiredBoolean(label: String): Boolean =
    optionalBoolean() ?: throw JsonException("Value for '$label' is not a boolean")

private fun JsonElement.toJsonValue(): Any? = when (this) {
    JsonNull -> null
    is KotlinxJsonObject -> JsonObject(toMutableMap())
    is KotlinxJsonArray -> JsonArray(toMutableList())
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is JsonObject -> toJsonElement()
    is JsonArray -> toJsonElement()
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Byte -> JsonPrimitive(this)
    is Short -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Number -> JsonPrimitive(toString())
    else -> JsonPrimitive(toString())
}
