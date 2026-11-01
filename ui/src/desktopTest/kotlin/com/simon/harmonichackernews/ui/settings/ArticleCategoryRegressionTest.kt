package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryTraining
import com.simon.harmonichackernews.categorization.ArticleEmbeddingInput
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real vectors from the pinned mobile model; held-out stories never enter the references. */
class ArticleCategoryRegressionTest {
    @Test
    fun realStoryInputsKeepTheirTopicsWithoutWeakHardwareAndAiNeighbors() = runBlocking {
        val text = checkNotNull(javaClass.getResourceAsStream("/article_category_regressions.json"))
            .bufferedReader().use { it.readText() }
        val fixture = Json.parseToJsonElement(text).jsonObject
        assertEquals(ArticleCategoryTraining.MODEL_SHA256, fixture.getValue("modelSha256").jsonPrimitive.content)
        assertEquals(ArticleEmbeddingInput.VERSION, fixture.getValue("inputVersion").jsonPrimitive.content)
        assertEquals(ArticleCategories.REPRESENTATION_VERSION, fixture.getValue("representationVersion").jsonPrimitive.content)
        val training = ArticleCategoryTraining(InMemoryKeyValueStore())
        val preferences = ArticleCategoryPreferences(enabled = true)
        for (element in fixture.getValue("stories").jsonArray) {
            val row = element.jsonObject
            val title = row.getValue("title").jsonPrimitive.content
            val titleOnly = !row.getValue("hasPreview").jsonPrimitive.boolean
            val vector = row.getValue(if (titleOnly) "titleEmbedding" else "previewEmbedding").jsonArray
                .map { it.jsonPrimitive.float }.toFloatArray()
            assertEquals(ArticleCategories.DIMENSIONS, vector.size)
            assertTrue(vector.all(Float::isFinite))
            val scores = ArticleCategories.scores(training.project(vector, titleOnly), training.centers(titleOnly))
            assertEquals(row.getValue("expectedPreviewLabels").jsonArray.map { it.jsonPrimitive.content },
                ArticleCategories.labels(scores, preferences), title)
            row["expectedTitleLabels"]?.jsonArray?.let { expected ->
                val headline = row.getValue("titleEmbedding").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()
                val titleScores = ArticleCategories.scores(training.project(headline, true), training.centers(true))
                assertEquals(expected.map { it.jsonPrimitive.content }, ArticleCategories.labels(titleScores, preferences), "$title (headline only)")
            }
        }
    }
}
