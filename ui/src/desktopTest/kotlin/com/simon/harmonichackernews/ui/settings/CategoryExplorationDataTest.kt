package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryTraining
import com.simon.harmonichackernews.categorization.ArticleTrainingStory
import com.simon.harmonichackernews.categorization.EmbeddedTrainingStory
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.resources.BundledHarmonicResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

class CategoryExplorationDataTest {
    @Test
    fun projectionsCoverEveryBundledExampleAndItsActualCategory() = runBlocking {
        val data = CategoryExplorationResources.load()
        val training = BundledHarmonicResources.articleCategoryTraining()
        val headerSize = ByteBuffer.wrap(training, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val header = Json.parseToJsonElement(training.copyOfRange(8, 8 + headerSize).decodeToString()).jsonObject
        val ids = header.getValue("stories").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.int }.toSet()
        assertEquals(ids, data.points.map { it.id }.toSet())
        assertEquals(675, data.points.size)
        assertEquals(ArticleCategories.defaults.map { it.id }.toSet(), data.points.map { it.category }.toSet())
        data.points.forEach { point ->
            assertTrue(header.getValue("assignments").jsonObject.getValue(point.category).jsonArray.any { it.jsonPrimitive.int == point.id })
        }
        assertEquals(listOf("umap", "tsne", "pca"), data.methods.map { it.id })
        assertEquals(3, data.methods.map { it.positions }.distinct().size)
        data.methods.forEach { projection ->
            assertEquals(data.points.size, projection.positions.size)
            assertTrue(projection.positions.all { xy -> xy.size == 2 && xy.all { it.isFinite() && it in 0f..1f } })
        }
        assertEquals(4, data.version)
        val repository = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { training } }
        val centers = repository.centers(titleOnly = true)
        val buffer = ByteBuffer.wrap(training, 8 + headerSize, training.size - 8 - headerSize).order(ByteOrder.LITTLE_ENDIAN)
        val raw = ids.associateWith {
            FloatArray(768) { buffer.float } to FloatArray(768) { buffer.float }
        }
        val vectors = raw.mapValues { it.value.second }
        data.points.forEach { point ->
            val expected = ArticleCategories.scores(repository.project(vectors.getValue(point.id), titleOnly = true), centers).take(3)
            assertEquals(expected.map { it.categoryId }, point.matches.map { it.category })
            expected.zip(point.matches).forEach { (score, match) -> assertTrue(abs(score.similarity - match.similarity) < 0.00001f) }
        }
        val previewCenters = repository.centers(false)
        val id = header.getValue("assignments").jsonObject.getValue("ai").jsonArray.first().jsonPrimitive.int
        val story = Json.decodeFromJsonElement<ArticleTrainingStory>(header.getValue("stories").jsonArray.first {
            it.jsonObject.getValue("id").jsonPrimitive.int == id
        })
        // Overriding a reference with its unchanged stored vectors forces both editable paths.
        repository.addExamples("ai", listOf(EmbeddedTrainingStory(story, raw.getValue(id).first.toList(), raw.getValue(id).second.toList())))
        for (titleOnly in listOf(false, true)) {
            val original = if (titleOnly) centers else previewCenters
            val rebuilt = repository.centers(titleOnly)
            assertEquals(original.keys, rebuilt.keys)
            original.forEach { (category, vector) ->
                vector.indices.forEach { i -> assertTrue(abs(vector[i] - rebuilt.getValue(category)[i]) < 0.00001f) }
            }
        }
    }
}
