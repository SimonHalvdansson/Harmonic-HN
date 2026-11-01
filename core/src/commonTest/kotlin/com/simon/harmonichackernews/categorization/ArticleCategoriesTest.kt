package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArticleCategoriesTest {
    @Test
    fun supportsMultipleMatchesAbstentionAndRenamedSelectedTopics() {
        val scores = ArticleCategories.scores(
            floatArrayOf(3f, 4f),
            mapOf("ai" to floatArrayOf(0.6f, 0.8f), "science" to floatArrayOf(0f, 2f), "world" to floatArrayOf(-1f, 0f)),
        )
        val preferences = ArticleCategoryPreferences(enabled = true, cutoff = 0.79f, names = mapOf("ai" to "Models"))
        assertEquals(listOf("Models", "Science"), ArticleCategories.labels(scores, preferences))
        assertEquals(listOf("Science"), ArticleCategories.labels(scores, preferences.copy(selected = setOf("science"))))
        assertTrue(ArticleCategories.labels(scores, preferences.copy(enabled = false)).isEmpty())
        assertTrue(ArticleCategories.labels(scores.drop(1), preferences.copy(cutoff = 0.95f)).isEmpty())
    }

    @Test
    fun rejectsInvalidVectorsAndMismatchedDimensions() {
        assertFailsWith<IllegalArgumentException> { ArticleCategories.normalize(floatArrayOf(Float.NaN)) }
        assertFailsWith<IllegalArgumentException> { ArticleCategories.normalize(floatArrayOf(0f, 0f)) }
        assertFailsWith<IllegalArgumentException> { ArticleCategories.scores(floatArrayOf(1f), mapOf("ai" to floatArrayOf(1f, 2f))) }
    }

    @Test
    fun shippedPrototypesCoverExactlyTheFixedCategoriesAndAreUnitVectors() {
        assertEquals(ArticleCategories.defaults.map { it.id }.toSet(), ArticleCategoryPrototypes.vectors.keys)
        ArticleCategoryPrototypes.vectors.values.forEach { vector ->
            assertEquals(ArticleCategories.DIMENSIONS, vector.size)
            assertTrue(abs(vector.sumOf { it.toDouble() * it } - 1.0) < 0.00001)
        }
    }

    @Test
    fun metadataInputIsBoundedStableAndHandlesMissingDescription() {
        assertEquals("title: Hello world | text: Hello world", ArticleCategories.input(" Hello\nworld ", null))
        assertEquals("title: Title | text: Page description", ArticleCategories.input("Title", " Page\t description "))
        assertTrue(ArticleCategories.input("T".repeat(1000), "D".repeat(5000)).length < 1220)
    }

    @Test
    fun preferencesStayOptInAndEmptySelectionsSurviveRestart() {
        val store = InMemoryKeyValueStore()
        val repository = ArticleCategorySettings(store, emptyFlow())
        assertFalse(repository.snapshot().enabled)
        ArticleCategories.defaults.forEach { repository.setSelected(it.id, false) }
        repository.rename("ai", "My models")
        repository.setEnabled(true)
        val reloaded = ArticleCategorySettings(store, emptyFlow()).snapshot()
        assertTrue(reloaded.enabled)
        assertTrue(reloaded.selected.isEmpty())
        assertEquals("My models", reloaded.name(ArticleCategories.defaults.first()))
        repository.rename("ai", " ")
        assertEquals("AI", repository.snapshot().name(ArticleCategories.defaults.first()))
        repository.setCutoff(Float.NaN)
        assertEquals(ArticleCategories.DEFAULT_CUTOFF, repository.snapshot().cutoff)
        repository.setCutoff(0.3f)
        assertEquals(0.75f, repository.snapshot().cutoff)
        repository.setCutoff(1f)
        assertEquals(0.90f, repository.snapshot().cutoff)
        repository.setCutoff(ArticleCategories.DEFAULT_CUTOFF)
        assertEquals(0.78f, repository.snapshot().cutoff)
        assertFailsWith<IllegalArgumentException> { repository.setSelected("custom", true) }
        assertFailsWith<IllegalArgumentException> { repository.rename("custom", "User category") }
    }
    @Test
    fun unshippedDefaultsNeedNoMigrationAndExplicitChoicesSurvive() {
        val fresh = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow())
        assertEquals(0.78f, fresh.snapshot().cutoff)
        val store = InMemoryKeyValueStore().apply { putFloat(ArticleCategorySettings.CUTOFF, 0.84f) }
        assertEquals(0.84f, ArticleCategorySettings(store, emptyFlow()).snapshot().cutoff)
    }
}
