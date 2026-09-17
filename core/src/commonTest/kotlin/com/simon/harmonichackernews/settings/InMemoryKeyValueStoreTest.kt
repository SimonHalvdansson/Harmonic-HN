package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryKeyValueStoreTest {
    @Test
    fun valuesAreCopiedAndClearRemovesEveryType() {
        val store = InMemoryKeyValueStore()
        val source = mutableSetOf("one")

        store.putString("name", "Harmonic")
        store.putBoolean("enabled", true)
        store.putInt("count", 3)
        store.putFloat("scale", 1.5f)
        store.putStringSet("tags", source)
        source += "later"

        assertEquals("Harmonic", store.getString("name"))
        assertTrue(store.getBoolean("enabled", false))
        assertEquals(3, store.getInt("count", 0))
        assertEquals(1.5f, store.getFloat("scale", 0f))
        assertEquals(setOf("one"), store.getStringSet("tags"))

        store.clear()

        assertFalse(store.contains("name"))
        assertEquals(emptySet(), store.getStringSet("tags"))
    }
}
