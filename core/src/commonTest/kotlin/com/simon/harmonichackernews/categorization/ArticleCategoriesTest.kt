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
        assertEquals(ArticleCategories.defaults.map { it.id }.toSet(), ArticleCategoryPrototypes.titleVectors.keys)
        (ArticleCategoryPrototypes.vectors.values + ArticleCategoryPrototypes.titleVectors.values).forEach { vector ->
            assertEquals(ArticleCategories.DIMENSIONS, vector.size)
            assertTrue(abs(vector.sumOf { it.toDouble() * it } - 1.0) < 0.00001)
        }
        assertEquals(ArticleCategories.DIMENSIONS, ArticleCategoryPrototypes.origin.size)
        assertTrue(ArticleCategoryPrototypes.origin.all(Float::isFinite))
        assertTrue(ArticleCategoryPrototypes.origin.sumOf { it.toDouble() * it } in 0.0..1.0)
        assertEquals(ArticleCategories.DIMENSIONS, ArticleCategoryPrototypes.titleOrigin.size)
        assertEquals(listOf("History", "Culture", "Energy", "Health"), ArticleCategories.defaults.takeLast(4).map { it.name })
    }

    @Test
    fun metadataInputIsBoundedStableAndHandlesMissingDescription() {
        assertEquals("title: Hello world | text: Hello world", ArticleCategories.input(" Hello\nworld ", null))
        val description = "A useful page description with enough detail about its actual subject."
        assertEquals("title: Title | text: $description", ArticleCategories.input("Title", " $description "))
        assertEquals("title: Title | text: Title", ArticleCategories.input("Title", "Log in or sign up to personalize your feed and join conversations."))
        assertEquals("title: $description | text: $description", ArticleCategories.input(description, description))
        assertTrue(ArticleCategories.input("T".repeat(1000), "D".repeat(5000)).length < 1220)
    }

    @Test
    fun removesSharedBackgroundAndRetainsOppositeTopicDirections() {
        val first = floatArrayOf(99f, 1f)
        val second = floatArrayOf(99f, -1f)
        assertTrue(ArticleCategories.scores(first, mapOf("other" to second)).single().similarity > 0.99f)
        val origin = floatArrayOf(0.99995f, 0f)
        val projected = ArticleCategories.project(first, origin)
        val other = ArticleCategories.project(second, origin)
        assertTrue(ArticleCategories.scores(projected, mapOf("other" to other)).single().similarity < -0.99f)
        assertFailsWith<IllegalArgumentException> { ArticleCategories.project(first, floatArrayOf(0f)) }
    }

    @Test
    fun weakNeighborsDoNotBecomeLabelsAndStrongOverlappingTopicsRemainAvailable() {
        val settings = ArticleCategoryPreferences(enabled = true)
        val scores = listOf(ArticleCategoryScore("ai", 0.6f), ArticleCategoryScore("software", 0.35f), ArticleCategoryScore("hardware", 0.3f))
        assertEquals(listOf("AI"), ArticleCategories.labels(scores, settings))
        assertTrue(ArticleCategories.labels(scores, settings.copy(selected = setOf("hardware"))).isEmpty())
        assertEquals(listOf("Energy", "Climate"), ArticleCategories.labels(listOf(
            ArticleCategoryScore("energy", 0.6f), ArticleCategoryScore("climate", 0.5f)), settings))
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
        repository.setCutoff(-1f)
        assertEquals(ArticleCategories.MIN_CUTOFF, repository.snapshot().cutoff)
        repository.setCutoff(1f)
        assertEquals(0.45f, repository.snapshot().cutoff)
        repository.setCutoff(ArticleCategories.DEFAULT_CUTOFF)
        assertEquals(ArticleCategories.DEFAULT_CUTOFF, repository.snapshot().cutoff)
        assertFailsWith<IllegalArgumentException> { repository.setSelected("custom", true) }
        assertFailsWith<IllegalArgumentException> { repository.rename("custom", "User category") }
    }
    @Test
    fun unshippedDefaultsNeedNoMigrationAndExplicitChoicesSurvive() {
        val fresh = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow())
        assertEquals(0.24f, fresh.snapshot().cutoff)
        val store = InMemoryKeyValueStore().apply { putFloat(ArticleCategorySettings.CUTOFF, 0.34f) }
        assertEquals(0.34f, ArticleCategorySettings(store, emptyFlow()).snapshot().cutoff)
        store.putFloat(ArticleCategorySettings.CUTOFF, 0.84f)
        assertEquals(ArticleCategories.MAX_CUTOFF, ArticleCategorySettings(store, emptyFlow()).snapshot().cutoff)
    }

    @Test
    fun inputTokenLimitDefaultsTo256AndPersistsOnlySupportedChoices() {
        val store = InMemoryKeyValueStore()
        val settings = ArticleCategorySettings(store, emptyFlow())
        assertEquals(256, settings.snapshot().maxInputTokens)
        for (limit in listOf(128, 256, 512, 1024)) {
            settings.setMaxInputTokens(limit)
            assertEquals(limit, ArticleCategorySettings(store, emptyFlow()).snapshot().maxInputTokens)
        }
        assertFailsWith<IllegalArgumentException> { settings.setMaxInputTokens(2048) }
        assertEquals(1024, settings.snapshot().maxInputTokens)
        store.putInt(ArticleCategorySettings.MAX_INPUT_TOKENS, -1)
        assertEquals(256, settings.snapshot().maxInputTokens)
    }
}
