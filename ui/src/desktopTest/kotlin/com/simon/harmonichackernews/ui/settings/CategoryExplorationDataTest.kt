package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryTraining
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
        assertEquals(490, data.points.size)
        assertEquals(ArticleCategories.defaults.map { it.id }.toSet(), data.points.map { it.category }.toSet())
        data.points.forEach { point ->
            assertTrue(header.getValue("assignments").jsonObject.getValue(point.category).jsonArray.any { it.jsonPrimitive.int == point.id })
        }
        assertEquals(listOf("umap", "tsne", "pca"), data.methods.map { it.id })
        assertEquals(3, data.methods.map { it.positions }.distinct().size)
        data.methods.forEach { projection ->
            assertEquals(490, projection.positions.size)
            assertTrue(projection.positions.all { xy -> xy.size == 2 && xy.all { it.isFinite() && it in 0f..1f } })
        }
        assertEquals(101, data.density.size)
        assertEquals(1f, data.density.max())
        assertTrue(data.density.indexOf(data.density.max()) in 35..60)
        val repository = ArticleCategoryTraining(InMemoryKeyValueStore()).apply { setDefaultLoader { training } }
        val centers = repository.centers()
        val buffer = ByteBuffer.wrap(training, 8 + headerSize, training.size - 8 - headerSize).order(ByteOrder.LITTLE_ENDIAN)
        val vectors = ids.associateWith { FloatArray(768) { buffer.float } }
        data.points.forEach { point ->
            val expected = ArticleCategories.scores(vectors.getValue(point.id), centers).take(3)
            assertEquals(expected.map { it.categoryId }, point.matches.map { it.category })
            expected.zip(point.matches).forEach { (score, match) -> assertTrue(abs(score.similarity - match.similarity) < 0.00001f) }
        }
    }
}
