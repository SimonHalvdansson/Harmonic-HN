package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class StringListEditorItemsTest {
    @Test
    fun legacyCaseDuplicatesCannotProduceDuplicateLazyRowKeys() {
        val items = uniqueEditorItems(listOf("AI", "ai", "Example.com", "example.com", "Kotlin"))

        assertEquals(listOf("AI", "Example.com", "Kotlin"), items)
        assertEquals(items.size, items.map(String::lowercase).toSet().size)
    }

    @Test
    fun addingValuesDeduplicatesExistingItemsAndTheSameBatch() {
        assertEquals(
            listOf("Kotlin", "Compose"),
            newEditorItems(listOf("AI"), listOf("ai", "Kotlin", "KOTLIN", "Compose", "compose")),
        )
    }

    @Test
    fun normalizationPreservesSpellingOrderAndTheSourceList() {
        val source = listOf("First", "second", "FIRST", "third")

        assertEquals(listOf("First", "second", "third"), uniqueEditorItems(source))
        assertEquals(listOf("First", "second", "FIRST", "third"), source)
        assertEquals(emptyList(), newEditorItems(source, listOf("first", "SECOND")))
    }
}
