package com.simon.harmonichackernews

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategory
import com.simon.harmonichackernews.categorization.ArticleCategoryTraining
import com.simon.harmonichackernews.categorization.ArticleTrainingConfiguration
import com.simon.harmonichackernews.categorization.ArticleTrainingStory
import com.simon.harmonichackernews.categorization.EmbeddedTrainingStory
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import java.util.Locale
import kotlin.random.Random
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryTrainingTimingTest {
    @Test
    fun trainedReaderMetadataAndFirstVectorDecode() {
        val random = Random(42)
        fun vector() = ArticleCategories.normalize(FloatArray(ArticleCategories.DIMENSIONS) {
            random.nextFloat() * 2f - 1f
        }).toList()
        val category = ArticleCategory("custom_benchmark", "Robotics", "Training fixture")
        val examples = List(200) { index ->
            val story = ArticleTrainingStory(index + 1, "Robot controller design ${index + 1}",
                "https://example.org/articles/${index + 1}", "Measurements from a robot controller under load.")
            EmbeddedTrainingStory(story, vector(), vector())
        }
        val store = InMemoryKeyValueStore()
        val saved = Json.encodeToString(ArticleTrainingConfiguration.serializer(),
            ArticleTrainingConfiguration(customCategories = listOf(category), added = mapOf(category.id to examples)))
        store.putString(ArticleCategoryTraining.STORAGE_KEY, saved)

        val metadataTimes = mutableListOf<Long>()
        val decodeTimes = mutableListOf<Long>()
        repeat(6) { run ->
            val start = SystemClock.elapsedRealtimeNanos()
            val training = ArticleCategoryTraining(store)
            val names = training.categories
            val metadataReady = SystemClock.elapsedRealtimeNanos()
            val decoded = training.state.value.added.getValue(category.id)
            val vectorsReady = SystemClock.elapsedRealtimeNanos()
            assertEquals(category, names.last())
            assertEquals(200, decoded.size)
            assertEquals(examples.last().titleEmbedding, decoded.last().titleEmbedding)
            if (run > 0) {
                metadataTimes += metadataReady - start
                decodeTimes += vectorsReady - metadataReady
            }
        }
        val report = String.format(Locale.ROOT,
            "200 examples, two 768-float vectors each, %d JSON bytes, 5 warm iterations: metadata median %.2f ms, first full decode median %.2f ms",
            saved.length, metadataTimes.sorted()[2] / 1_000_000.0, decodeTimes.sorted()[2] / 1_000_000.0)
        Log.i("CategoryTrainingTiming", report)
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply { putString("stream", "\n$report\n") })
    }
}
