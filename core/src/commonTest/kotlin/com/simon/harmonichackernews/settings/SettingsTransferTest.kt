package com.simon.harmonichackernews.settings

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.*
import kotlin.test.*

class SettingsTransferTest {
    private val store = InMemoryKeyValueStore()
    private val schedule = InMemoryKeyValueStore()
    private val transfer = SettingsTransfer(store, schedule)

    private fun document(values: String, version: String = "1", nighttime: String = "{}") =
        """{"format":"harmonic-settings","version":$version,"settings":$values,"nighttime":$nighttime}"""

    @Test
    fun roundTripRestoresTypedValuesAndDefaultsWithoutTouchingPrivateData() {
        store.putBoolean(UserPreferenceKeys.SHOW_POINTS, false)
        store.putString(UserPreferenceKeys.FONT, "georgia")
        store.putInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, 22)
        store.putFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 0.6f)
        store.putStringSet(UserPreferenceKeys.ADDITIONAL_FRONTPAGES, setOf("Classic"))
        schedule.putString(NighttimeScheduleKeys.FROM_HOUR, "20")
        store.putString("api_key", "secret")
        store.putString(AiSummaryPreferenceKeys.BASE_URL, "https://private.example/v1")
        store.putString(ContentFilterKeys.USERS, "someone")
        val backup = transfer.export()
        assertFalse(backup.contains("secret"))
        assertFalse(backup.contains("private.example"))
        assertFalse(backup.contains("someone"))
        store.putBoolean(UserPreferenceKeys.SHOW_POINTS, true)
        store.putBoolean(UserPreferenceKeys.HIDE_JOBS, true)
        store.putString(UserPreferenceKeys.FONT, "verdana")
        store.putInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, 14)
        store.putFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 0.3f)
        assertIs<SettingsImportResult.Imported>(transfer.import(backup))
        assertFalse(store.getBoolean(UserPreferenceKeys.SHOW_POINTS, true))
        assertFalse(store.contains(UserPreferenceKeys.HIDE_JOBS))
        assertEquals("georgia", store.getString(UserPreferenceKeys.FONT))
        assertEquals(22, store.getInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, 0))
        assertEquals(0.6f, store.getFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 0f))
        assertEquals(setOf("Classic"), store.getStringSet(UserPreferenceKeys.ADDITIONAL_FRONTPAGES))
        assertEquals("20", schedule.getString(NighttimeScheduleKeys.FROM_HOUR))
        assertEquals("secret", store.getString("api_key"))
        assertEquals("someone", store.getString(ContentFilterKeys.USERS))
        AppSettingsRepository(store, emptyFlow()).snapshot()
    }

    @Test
    fun invalidAndUnknownValuesLeaveExistingPreferencesUntouched() {
        store.putInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, 18)
        store.putString(UserPreferenceKeys.FONT, "georgia")
        store.putFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 0.5f)
        val result = transfer.import(document("""{
            "pref_webview_reader_mode_font_size":2147483647,
            "pref_font":"future-font",
            "pref_split_ratio_portrait":1e100,
            "pref_show_points":"false",
            "pref_color_scheme_light":{},
            "pref_show_index":false,
            "unknown_future_setting":true,
            "api_key":null,
            "pref_ai_summary_base_url":"https://unexpected.example"
        }"""))
        assertEquals(SettingsImportResult.Imported(1, 8), result)
        assertEquals(18, store.getInt(UserPreferenceKeys.READER_MODE_FONT_SIZE, 0))
        assertEquals("georgia", store.getString(UserPreferenceKeys.FONT))
        assertEquals(0.5f, store.getFloat(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, 0f))
        assertFalse(store.getBoolean(UserPreferenceKeys.SHOW_INDEX, true))
        assertFalse(store.contains(UserPreferenceKeys.SHOW_POINTS))
        assertFalse(store.contains("unknown_future_setting"))
        assertFalse(store.contains(AiSummaryPreferenceKeys.BASE_URL))
    }

    @Test
    fun malformedWrongVersionAndOversizedDocumentsDoNotWriteAnything() {
        val validSetting = """{"pref_show_points":false}"""
        val invalid = listOf(
            "", "[]", "{}", document(validSetting).dropLast(1),
            document(validSetting, "\"1\""), document(validSetting, nighttime = "[]"),
            " ".repeat(SettingsTransfer.MAX_CHARS + 1),
            document("""{"unknown":${"[".repeat(20)}0${"]".repeat(20)}}"""),
        )
        invalid.forEach { assertEquals(SettingsImportResult.Invalid, transfer.import(it)) }
        assertEquals(SettingsImportResult.UnsupportedVersion, transfer.import(document(validSetting, "2")))
        assertTrue(store.keys().isEmpty())
        assertTrue(schedule.keys().isEmpty())
    }

    @Test
    fun everyKnownKeyRejectsStructuredValuesWithoutBreakingAppStartup() {
        val exported = Json.parseToJsonElement(transfer.export()).jsonObject["settings"]!!.jsonObject
        val corrupted = JsonObject(exported.mapValues { buildJsonObject { put("unexpected", true) } })
        val result = transfer.import(document(corrupted.toString()))
        assertEquals(SettingsImportResult.Imported(0, exported.size), result)
        assertTrue(store.keys().isEmpty())
        AppSettingsRepository(store, emptyFlow()).snapshot()
    }

    @Test
    fun nonfiniteShaderValuesInvalidEnumsAndWrongPrimitiveTypesCannotPersist() {
        val invalid = listOf("\"NaN\"", "\"Infinity\"", "1e100", "-1e100", "true", "[]", "{}").flatMap { value ->
            listOf(UserPreferenceKeys.SPLIT_RATIO_PORTRAIT, GlassParameter.BlurRadius.storageKey,
                UserPreferenceKeys.COMMENT_TEXT_SIZE, UserPreferenceKeys.READER_MODE_FONT_SIZE).map { key -> key to value }
        }
        invalid.forEach { (key, value) ->
            assertEquals(SettingsImportResult.Imported(0, 1), transfer.import(document("""{"$key":$value}""")))
        }
        assertTrue(store.keys().isEmpty())
    }

    @Test
    fun legacyThemeIsExportedAsCurrentPortableTheme() {
        store.putString(ThemePreferences.KEY, "amoledwhite_daynight")
        val backup = transfer.export()
        val restored = InMemoryKeyValueStore()
        SettingsTransfer(restored, InMemoryKeyValueStore()).import(backup)
        val appearance = StoredUserSettings(restored, emptyFlow()).appearance
        assertTrue(appearance.followSystem)
        assertEquals("pure", appearance.colorSchemes.light)
        assertEquals("pure", appearance.colorSchemes.dark)
    }

    @Test
    fun scheduleValuesAreValidatedAndJsonStringsMayContainBrackets() {
        val prompt = "Explain {this} and [that], using \"quotes\"."
        val values = buildJsonObject { put(AiSummaryPreferenceKeys.SYSTEM_PROMPT, prompt) }
        val nighttime = buildJsonObject {
            put(NighttimeScheduleKeys.FROM_HOUR, "99")
            put(NighttimeScheduleKeys.TO_MINUTE, "45")
        }
        assertEquals(SettingsImportResult.Imported(2, 1), transfer.import(document(values.toString(), nighttime = nighttime.toString())))
        assertEquals(prompt, store.getString(AiSummaryPreferenceKeys.SYSTEM_PROMPT))
        assertFalse(schedule.contains(NighttimeScheduleKeys.FROM_HOUR))
        assertEquals("45", schedule.getString(NighttimeScheduleKeys.TO_MINUTE))
    }
}
