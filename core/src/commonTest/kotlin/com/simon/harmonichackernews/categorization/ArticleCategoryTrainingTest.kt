package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.*

class ArticleCategoryTrainingTest {
    @Test
    fun storedExamplesAreComparedWithTheCurrentCenterWithoutInference() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { seedBytes() } }
        val category = training.create("Custom")
        training.addExamples(category, listOf(
            EmbeddedTrainingStory(ArticleTrainingStory(10, "First"), unit(0).toList()),
            EmbeddedTrainingStory(ArticleTrainingStory(11, "Second"), unit(1).toList()),
        ))
        val scores = training.exampleSimilarities(category)
        assertEquals(0.70710677f, scores.getValue(10), 0.00001f)
        assertEquals(scores.getValue(10), scores.getValue(11))
        training.removeExample(category, 11)
        assertEquals(mapOf(10 to 1f), training.exampleSimilarities(category))
        training.resetExamples(category)
        assertTrue(training.exampleSimilarities(category).isEmpty())
        training.removeExample("ai", 2)
        assertEquals(mapOf(1 to 1f), training.exampleSimilarities("ai"))
    }

    @Test
    fun removingAndRestoringDefaultExamplesChangesTheMean() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { seedBytes() } }
        assertEquals(listOf(1, 2), training.examples("ai").map { it.id })
        training.removeExample("ai", 2)
        assertEquals(listOf(1), training.examples("ai").map { it.id })
        assertEquals(1f, training.centers().getValue("ai")[0])
        training.removeExample("ai", 1)
        assertFalse("ai" in training.centers(), "An empty category must abstain, not silently reuse its shipped mean")
        training.resetExamples("ai")
        assertEquals(listOf(1, 2), training.examples("ai").map { it.id })
    }

    @Test
    fun customTrainingSurvivesRestartAndDeletionNeverChangesHistory() = runTest {
        val store = InMemoryKeyValueStore()
        val training = ArticleCategoryTraining(store).apply { setDefaultLoader { seedBytes() } }
        val category = training.create("Robotics")
        val preferences = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow(), { training.categories })
        preferences.setSelected(category, true)
        training.addExamples(category, listOf(EmbeddedTrainingStory(ArticleTrainingStory(99, "A robot", "https://example.com/robot"), unit(2).toList())))
        val reloaded = ArticleCategoryTraining(store).apply { setDefaultLoader { seedBytes() } }
        assertEquals("Robotics", reloaded.categories.last().name)
        assertEquals(listOf(99), reloaded.examples(category).map { it.id })
        assertEquals(1f, reloaded.centers().getValue(category)[2])
        reloaded.delete(category)
        assertFalse(reloaded.categories.any { it.id == category })
        assertTrue(reloaded.examples(category).isEmpty())
    }

    @Test
    fun duplicateAdditionsAreNotDoubleWeightedAndInvalidVectorsCannotBeSaved() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { seedBytes() } }
        val category = training.create("Custom")
        val example = EmbeddedTrainingStory(ArticleTrainingStory(10, "Example"), unit(0).toList())
        training.addExamples(category, listOf(example, example))
        training.addExamples(category, listOf(example))
        assertEquals(1, training.examples(category).size)
        assertFailsWith<IllegalArgumentException> { training.addExamples(category, listOf(example.copy(embedding = listOf(1f)))) }
        assertFailsWith<IllegalArgumentException> { training.addExamples(category, listOf(example.copy(embedding = List(768) { Float.NaN }))) }
    }

    @Test
    fun labelsShowOnlyTheStrongestSelectedTopicsAndHonorTheMaximum() {
        val preferences = ArticleCategoryPreferences(enabled = true, cutoff = 0.5f, maxLabels = 2)
        val scores = listOf(ArticleCategoryScore("world", 0.7f), ArticleCategoryScore("ai", 0.95f),
            ArticleCategoryScore("unknown", 1f), ArticleCategoryScore("security", 0.85f))
        assertEquals(listOf("AI", "Security"), ArticleCategories.labels(scores, preferences))
        assertEquals(listOf("AI"), ArticleCategories.labels(scores, preferences.copy(maxLabels = 1)))
        assertEquals(3, ArticleCategories.labels(scores, preferences.copy(maxLabels = 10)).size)
    }

    @Test
    fun brokenResourceIsRejectedWithoutPublishingPartialDefaults() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { seedBytes().dropLast(4).toByteArray() } }
        assertFailsWith<IllegalArgumentException> { training.loadDefaults() }
    }

    companion object {
        fun unit(index: Int) = FloatArray(768).apply { this[index] = 1f }
        fun seedBytes(): ByteArray {
            val header = Json.encodeToString(ArticleTrainingSeed(
                listOf(ArticleTrainingStory(1, "First", source = "default"), ArticleTrainingStory(2, "Second", source = "default")),
                mapOf("ai" to listOf(1, 2)), ArticleCategoryTraining.MODEL_SHA256,
            )).encodeToByteArray()
            val bytes = ArrayList<Byte>()
            fun int(value: Int) { repeat(4) { bytes.add((value ushr (it * 8)).toByte()) } }
            bytes.addAll("HNC1".encodeToByteArray().toList())
            int(header.size)
            bytes.addAll(header.toList())
            listOf(unit(0), unit(1)).forEach { vector -> vector.forEach { int(it.toBits()) } }
            return bytes.toByteArray()
        }
    }
}
