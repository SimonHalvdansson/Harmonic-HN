package com.simon.harmonichackernews.serialization

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class JsonValuesTest {
    @Test
    fun integerAccessorsPreserveNumericCoercions() {
        val random = Random(42)
        val values = listOf(
            "0", "-0", "42", "-42", "2147483647", "-2147483648", "2147483648",
            "9223372036854775807", "-9223372036854775808", "9223372036854775808",
            "9007199254740993", "1e2", "1.0", "1.5", "1e-2", "1e100", "null", "true",
            "false", "{}", "[]", "\"42\"", "\"-42\"", "\"00042\"", "\"\"", "\"-\"",
            "\"+42\"", "\"+9007199254740993\"", "\" 42 \"", "\"1e2\"", "\"1.5\"",
            "\"NaN\"", "\"Infinity\"", "\"٤٢\"", "\"４２\"", "\"\\u0034\\u0032\"",
        ) + List(500) { random.nextLong().toString() }.flatMap { listOf(it, "\"$it\"") }
        for (value in values) {
            val primitive = Json.parseToJsonElement(value) as? JsonPrimitive
            // Freeze the original conversions, including overflow, rounding, and null fallbacks.
            val expectedInt = primitive?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() }
            val expectedLong = primitive?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
            val obj = JsonObject("""{"value":$value}""")
            val array = JsonArray("[$value]")
            assertEquals(expectedInt ?: 17, obj.optInt("value", 17), value)
            assertEquals(expectedInt ?: 17, array.optInt(0, 17), value)
            assertEquals(expectedLong ?: 17L, obj.optLong("value", 17L), value)
            if (expectedInt == null) {
                assertFailsWith<JsonException>(value) { obj.getInt("value") }
                assertFailsWith<JsonException>(value) { array.getInt(0) }
            } else {
                assertEquals(expectedInt, obj.getInt("value"), value)
                assertEquals(expectedInt, array.getInt(0), value)
            }
            if (expectedLong == null) {
                assertFailsWith<JsonException>(value) { obj.getLong("value") }
            } else {
                assertEquals(expectedLong, obj.getLong("value"), value)
            }
        }
    }

    @Test
    fun containerAccessorsRetainOptionalAndRequiredBehavior() {
        val obj = JsonObject("""{"object":{"id":1},"array":[2],"null":null,"number":3,"string":"s","bool":false}""")
        for (key in listOf("array", "null", "number", "string", "bool", "missing")) {
            assertNull(obj.optJSONObject(key), key)
            assertFailsWith<JsonException>(key) { obj.getJSONObject(key) }
        }
        for (key in listOf("object", "null", "number", "string", "bool", "missing")) {
            assertNull(obj.optJSONArray(key), key)
            assertFailsWith<JsonException>(key) { obj.getJSONArray(key) }
        }
        assertEquals(1, obj.getJSONObject("object").getInt("id"))
        assertEquals(2, obj.getJSONArray("array").getInt(0))
        val array = JsonArray("""[{},[],null,3,"s",false]""")
        for (index in listOf(-1, 1, 2, 3, 4, 5, 6)) {
            assertNull(array.optJSONObject(index))
            assertFailsWith<JsonException> { array.getJSONObject(index) }
        }
        for (index in listOf(-1, 0, 2, 3, 4, 5, 6)) {
            assertNull(array.optJSONArray(index))
            assertFailsWith<JsonException> { array.getJSONArray(index) }
        }
        assertEquals(0, array.getJSONObject(0).length())
        assertEquals(0, array.getJSONArray(1).length())
        assertFailsWith<JsonException> { JsonObject("[]") }
        assertFailsWith<JsonException> { JsonArray("{}") }
    }

    @Test
    fun genericGettersKeepScalarTypesAndValues() {
        val cases = listOf(
            "true" to true, "false" to false, "42" to 42L, "1e2" to 100L,
            "9223372036854775807" to Long.MAX_VALUE, "1.5" to 1.5,
            "1.0" to 1.0, "\"42\"" to "42", "\"true\"" to "true",
        )
        for ((value, expected) in cases) {
            val obj = JsonObject("""{"value":$value}""")
            assertEquals(expected, obj.get("value"), value)
            assertEquals(expected, obj.opt("value"), value)
            assertEquals(expected, JsonArray("[$value]").get(0), value)
            assertEquals(expected, obj.remove("value"), value)
        }
        val obj = JsonObject("""{"object":{},"array":[],"null":null}""")
        assertIs<JsonObject>(obj.get("object"))
        assertIs<JsonArray>(obj.get("array"))
        assertNull(obj.opt("null"))
        assertNull(obj.opt("missing"))
        assertFailsWith<JsonException> { obj.get("null") }
    }
}
