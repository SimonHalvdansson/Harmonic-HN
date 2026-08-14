package com.simon.harmonichackernews.settings

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFileKeyValueStoreTest {
    @Test
    fun valuesSurviveReloadAndSetsPreserveArbitraryText() {
        val directory = createTempDirectory("harmonic-desktop-settings-")
        try {
            val file = directory.resolve("settings.properties")
            DesktopFileKeyValueStore(file).apply {
                putString("string", "line one\nline two")
                putBoolean("boolean", true)
                putInt("int", 42)
                putLong("long", 9_000_000_000L)
                putFloat("float", 1.25f)
                putStringSet("set", setOf("one,two", "norsk: blåbær", ""))
            }

            val restored = DesktopFileKeyValueStore(file)
            assertEquals("line one\nline two", restored.getString("string"))
            assertTrue(restored.getBoolean("boolean", false))
            assertEquals(42, restored.getInt("int", 0))
            assertEquals(9_000_000_000L, restored.getLong("long", 0L))
            assertEquals(1.25f, restored.getFloat("float", 0f))
            assertEquals(setOf("one,two", "norsk: blåbær", ""), restored.getStringSet("set"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun removalAndClearArePersisted() {
        val directory = createTempDirectory("harmonic-desktop-settings-")
        try {
            val file = directory.resolve("settings.properties")
            val store = DesktopFileKeyValueStore(file)
            store.putString("first", "value")
            store.putInt("second", 2)
            store.remove("first")

            DesktopFileKeyValueStore(file).let { restored ->
                assertNull(restored.getString("first"))
                assertFalse(restored.contains("first"))
                assertEquals(setOf("second"), restored.keys())
                restored.clear()
            }

            assertTrue(DesktopFileKeyValueStore(file).keys().isEmpty())
            assertTrue(Files.isRegularFile(file))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
