package com.simon.harmonichackernews.settings

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real SharedPreferences XML -> DataStore files, isolated from the installed app's data. */
@RunWith(AndroidJUnit4::class)
class AndroidPreferenceMigrationTest {
    @Test
    fun namedHandlesAreLazyAndAwaitingLoadsTheSharedSnapshot() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = IsolatedContext(instrumentation.targetContext)
        val name = "lazy_preferences"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(legacy.edit().putString("saved_value", "before upgrade").commit())
        val readsBefore = context.legacyReads.get()
        lateinit var store: AndroidKeyValueStore
        try {
            instrumentation.runOnMainSync {
                store = AndroidKeyValueStore.named(context, name)
                assertSame(store, AndroidKeyValueStore.named(context, name))
                assertEquals(readsBefore, context.legacyReads.get())
            }
            store.awaitLoaded()
            assertEquals("before upgrade", store.getString("saved_value"))
            store.putString("saved_value", "updated")
            store.awaitPersisted()
            assertEquals("updated", AndroidKeyValueStore.named(context, name).getString("saved_value"))
            assertTrue(legacy.all.isEmpty())
        } finally { context.filesDir.deleteRecursively(); context.cleanup() }
    }

    @Test
    fun version31StoresPreserveEveryTypeUnknownKeysAndSecrets() = runBlocking {
        val context = isolatedContext()
        // Names and representative keys from the v3.1 release, deliberately independent of
        // today's constants so a renamed key or store cannot silently weaken upgrade coverage.
        val prefix = "com.simon.harmonichackernews."
        val fixtures = mapOf(
            context.packageName + "_preferences" to mapOf(
                "pref_show_points" to false,
                "pref_story_text_size" to "19.0",
                "legacy_unknown_int" to 19,
                "pref_font" to "system",
                "pref_webview" to false,
                "pref_ai_summary_system_prompt" to "Keep my custom prompt\nblåbær",
                "pref_ai_summary_base_url" to "https://example.test/v1",
                "pref_ai_summary_model" to "custom-model",
                "pref_ai_summary_api_key" to "test-legacy-secret",
                "unknown_from_old_release" to setOf("a,b", "", "blåbær"),
            ),
            prefix + "GLOBAL_SHARED_PREFERENCES_KEY" to mapOf(
                prefix + "KEY_SHARED_PREFERENCES_BOOKMARKS" to "42q1700000000000-99q1700000001000",
                prefix + "KEY_SHARED_PREFERENCES_FAVORITES" to "42q1700000000000",
                prefix + "KEY_SHARED_PREFERENCES_UPVOTED" to "99q1700000001000",
                prefix + "KEY_SHARED_PREFERENCES_HISTORIES" to "42q1700000000000-99q1700000001000",
                prefix + "KEY_SHARED_PREFERENCES_FAVORITE_COMMENTS" to setOf("102", "103"),
                prefix + "KEY_SHARED_PREFERENCES_UPVOTED_COMMENTS" to setOf("102"),
                prefix + "KEY_SHARED_PREFERENCES_USER_TAGS" to "{\"alice\":\"friend\"}",
                prefix + "KEY_SHARED_PREFERENCES_WELCOME_DIALOG_SHOWN" to true,
                prefix + "KEY_SHARED_PREFERENCES_LAST_VERSION" to 301,
                prefix + "KEY_NIGHTTIME_FROM_HOUR" to 22,
                prefix + "KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL42" to "https://example.test/article",
            ),
            prefix + "PREVIEW_IMAGE_CACHE_PREFERENCES" to mapOf("preview42" to "metadata"),
            "file_access_times" to mapOf("story_42" to 1_800_000_000_000L),
            "widget_config" to mapOf("widget_7" to "saved configuration", "opacity_7" to 0.75f),
            "widget_stories_cache" to mapOf("stories" to "[42,99]", "updated" to 1_800_000_000_000L),
        )
        try {
            for ((name, values) in fixtures) {
                val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val editor = legacy.edit()
                values.forEach { (key, value) ->
                    when (value) {
                        is String -> editor.putString(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    }
                }
                assertTrue(editor.commit())
                val expected = values - "pref_ai_summary_api_key"
                useStore(context, name) { store ->
                    assertValues(expected, store)
                    if (name == context.packageName + "_preferences") {
                        val settings = StoredUserSettings(store, store.changes)
                        assertFalse(settings.story.showPoints)
                        assertEquals(19f, settings.story.storyTextSize, 0f)
                        assertFalse(settings.reading.integratedWebView)
                    }
                    if (name == prefix + "GLOBAL_SHARED_PREFERENCES_KEY") {
                        val bookmarks = com.simon.harmonichackernews.data.SavedItemCodec.decode(
                            store.getString(prefix + "KEY_SHARED_PREFERENCES_BOOKMARKS"),
                        )
                        assertEquals(listOf(42, 99), bookmarks.map { it.id })
                        assertEquals(listOf(1700000000000L, 1700000001000L), bookmarks.map { it.created })
                    }
                    store.putString("new_setting", "after upgrade")
                }
                useStore(context, name) { store ->
                    assertValues(expected, store)
                    assertEquals("after upgrade", store.getString("new_setting"))
                    assertFalse(store.contains("pref_ai_summary_api_key"))
                }
                if ("pref_ai_summary_api_key" in values) {
                    assertEquals(mapOf("pref_ai_summary_api_key" to "test-legacy-secret"), legacy.all)
                } else assertTrue(legacy.all.isEmpty())
            }
        } finally { context.filesDir.deleteRecursively(); context.cleanup() }
    }

    @Test
    fun interruptedMigrationKeepsLegacyDataAndRetryNeverOverwritesNewValues() = runBlocking {
        val context = isolatedContext()
        val name = context.packageName + "_preferences"
        val legacy = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        assertTrue(legacy.edit().putString("pref_font", "original").commit())
        val failure = object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) = true
            override suspend fun migrate(currentData: Preferences): Preferences = error("interrupted migration")
            override suspend fun cleanUp() = Unit
        }
        try {
            try {
                useStore(context, name, listOf(failure)) { fail("Migration should fail") }
                fail("Expected failed migration")
            } catch (expected: IllegalStateException) {
                assertEquals("interrupted migration", expected.message)
            }
            assertEquals("original", legacy.getString("pref_font", null))
            useStore(context, name) {
                assertEquals("original", it.getString("pref_font"))
                it.putString("pref_font", "new selection")
            }
            // Simulate a restored legacy XML file alongside a more recent DataStore backup.
            assertTrue(legacy.edit().putString("pref_font", "stale backup").putBoolean("old_extra", true).commit())
            useStore(context, name) {
                assertEquals("new selection", it.getString("pref_font"))
                assertTrue(it.getBoolean("old_extra", false))
            }
        } finally { context.filesDir.deleteRecursively(); context.cleanup() }
    }

    private suspend fun useStore(
        context: Context,
        name: String,
        extraMigrations: List<DataMigration<Preferences>> = emptyList(),
        block: suspend (DataStoreKeyValueStore) -> Unit,
    ) {
        val job = SupervisorJob()
        try {
            val store = DataStoreKeyValueStore.openFile(
                File(context.filesDir, "$name.preferences_pb").absolutePath,
                CoroutineScope(job + Dispatchers.IO),
                listOf(legacyPreferenceMigration(context, name)) + extraMigrations,
            )
            block(store)
            store.awaitPersisted()
        } finally { job.cancelAndJoin() }
    }

    private fun assertValues(expected: Map<String, Any>, store: KeyValueStore) {
        expected.forEach { (key, value) ->
            val actual = when (value) {
                is String -> store.getString(key)
                is Boolean -> store.getBoolean(key, !value)
                is Int -> store.getInt(key, 0)
                is Long -> store.getLong(key, 0)
                is Float -> store.getFloat(key, 0f)
                is Set<*> -> store.getStringSet(key)
                else -> error("Unsupported fixture")
            }
            assertEquals(key, value, actual)
        }
    }

    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "migration-${UUID.randomUUID()}-"
        private val names = mutableSetOf<String>()
        val legacyReads = AtomicInteger()
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(baseContext.cacheDir, prefix).apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int) =
            baseContext.getSharedPreferences(prefix + name, mode).also {
                legacyReads.incrementAndGet()
                names += prefix + name
            }
        fun cleanup() { names.forEach { baseContext.deleteSharedPreferences(it) } }
    }

    private fun isolatedContext() = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
}
