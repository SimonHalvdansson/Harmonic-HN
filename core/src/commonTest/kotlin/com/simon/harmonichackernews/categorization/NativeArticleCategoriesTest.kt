package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.*
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeArticleCategoriesTest {
    private class Engine : NativeArticleEmbeddingEngine {
        var signature = "revision-1"
        var available = true
        var quota = Int.MAX_VALUE
        val inputs = mutableListOf<String>()
        var beforeEmbed: suspend () -> Unit = {}
        override suspend fun model() = if (available) NativeEmbeddingModel(signature, "Test system", 4, 256) else null
        override suspend fun embed(text: String): FloatArray {
            if (quota-- <= 0) throw EmbeddingQuotaException()
            beforeEmbed()
            inputs += text
            return FloatArray(4) { i -> ((text.hashCode() ushr (i * 7)) and 127).toFloat() + 1f }
        }
    }

    @Test fun quotaPreservesRoundRobinCoverageAndEachNormalRequestAddsOne() = runTest {
        val engine = Engine().apply { quota = 2 }
        val training = training()
        val provider = NativeArticleCategories(engine, InMemoryArticleEmbeddingCache(), training)
        provider.refresh()
        provider.prepareInitial { true }
        assertEquals(listOf("AI first", "Space first"), engine.inputs)
        assertEquals(2, provider.state.value.completed)
        assertTrue(provider.state.value.paused)
        assertEquals(setOf("ai", "space"), training.centers(true).keys)
        engine.quota = Int.MAX_VALUE
        val results = provider.scores("New story about space exploration", null)
        assertTrue(results.isNotEmpty())
        assertEquals(3, provider.state.value.completed)
        assertEquals(4, engine.inputs.size, "One normal inference, then exactly one preparation inference")
        provider.scores("New story about space exploration", null)
        assertEquals(4, engine.inputs.size, "Reading a cached result consumes no quota")
        provider.scores("Another new story", null)
        assertFalse(provider.state.value.incomplete)
        assertFalse(provider.state.value.paused)
    }

    @Test fun manualResumeRetriesOnlyMissingExamplesAndStopsAgainAtQuota() = runTest {
        val engine = Engine().apply { quota = 1 }
        val provider = NativeArticleCategories(engine, InMemoryArticleEmbeddingCache(), training())
        provider.refresh(); provider.prepareInitial { true }
        assertEquals(1, provider.state.value.completed)
        assertTrue(provider.state.value.paused)
        provider.prepareInitial { true }
        assertEquals(1, engine.inputs.size, "Automatic preparation must not retry a quota pause")
        engine.quota = 1
        provider.prepareInitial(force = true) { true }
        assertEquals(listOf("AI first", "Space first"), engine.inputs)
        assertEquals(2, provider.state.value.completed)
        assertTrue(provider.state.value.paused)
        engine.quota = Int.MAX_VALUE
        provider.prepareInitial(force = true) { true }
        assertEquals(4, engine.inputs.size)
        assertFalse(provider.state.value.incomplete)
        assertFalse(provider.state.value.preparing)
    }

    @Test fun runtimeCanManuallyPrepareWhileCategorizationIsOffButNeverInBackground() = runTest {
        val engine = Engine().apply { quota = 1 }
        val repository = training().apply { loadDefaults() }
        val settings = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow(), { repository.categories })
        val runtime = ArticleCategoryRuntime(settings, null, null, backgroundScope,
            training = repository, nativeEngine = engine, nativeCache = InMemoryArticleEmbeddingCache())
        runtime.start(); runCurrent()
        assertTrue(engine.inputs.isEmpty())
        runtime.resumeNativePreparation(); runCurrent()
        assertEquals(1, runtime.state.value.native.completed)
        assertTrue(runtime.state.value.native.paused)
        runtime.setForeground(false); runCurrent()
        engine.quota = Int.MAX_VALUE
        runtime.resumeNativePreparation(); runCurrent()
        assertEquals(1, engine.inputs.size)
        runtime.setForeground(true); runCurrent()
        assertEquals(1, engine.inputs.size)
        runtime.resumeNativePreparation(); runCurrent()
        assertEquals(4, runtime.state.value.native.completed)
        assertFalse(runtime.state.value.native.incomplete)
        assertFalse(settings.snapshot().enabled)
    }

    @Test fun providerChoicePersistsWithoutChangingTheGemmaTokenLimit() {
        val store = InMemoryKeyValueStore()
        val settings = ArticleCategorySettings(store, emptyFlow())
        assertTrue(settings.snapshot().preferSystemEmbeddings)
        settings.setMaxInputTokens(512)
        settings.setPreferSystemEmbeddings(false)
        val restarted = ArticleCategorySettings(store, emptyFlow())
        assertFalse(restarted.snapshot().preferSystemEmbeddings)
        assertEquals(512, restarted.snapshot().maxInputTokens)
        restarted.setPreferSystemEmbeddings(true)
        assertEquals(512, restarted.snapshot().maxInputTokens)
    }

    @Test fun completedInferenceIsSavedEvenWhenTheRequestingRowIsCancelled() = runTest {
        val cache = InMemoryArticleEmbeddingCache()
        val engine = Engine()
        val provider = NativeArticleCategories(engine, cache, training())
        provider.refresh(); provider.prepareInitial { true }
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        engine.beforeEmbed = { started.complete(Unit); finish.await() }
        val request = launch { provider.scores("A disappearing story", null) }
        started.await()
        request.cancel()
        finish.complete(Unit)
        request.join()
        assertEquals(5, engine.inputs.size)
        val restartedEngine = Engine()
        val restarted = NativeArticleCategories(restartedEngine, cache, training())
        restarted.refresh()
        assertTrue(restarted.scores("A disappearing story", null).isNotEmpty())
        assertTrue(restartedEngine.inputs.isEmpty(), "A completed result must survive cancellation and app restart")
    }

    @Test fun restartReusesSavedVectorsAndAChangedModelRegeneratesThem() = runTest {
        val cache = InMemoryArticleEmbeddingCache()
        val first = Engine().apply { quota = 2 }
        NativeArticleCategories(first, cache, training()).apply { refresh(); prepareInitial { true } }
        val second = Engine()
        val provider = NativeArticleCategories(second, cache, training())
        provider.refresh()
        assertEquals(2, provider.state.value.completed)
        provider.prepareInitial { true }
        assertEquals(listOf("AI second", "Space second"), second.inputs)
        provider.refresh()
        provider.prepareInitial { true }
        assertEquals(2, second.inputs.size)
        second.signature = "revision-2"
        provider.refresh()
        assertEquals(0, provider.state.value.completed)
        provider.prepareInitial { true }
        assertEquals(6, second.inputs.size)
        assertFalse(provider.state.value.incomplete)
    }

    @Test fun failedExtraRequestDoesNotDiscardNormalResult() = runTest {
        val engine = Engine().apply { quota = 2 }
        val provider = NativeArticleCategories(engine, InMemoryArticleEmbeddingCache(), training())
        provider.refresh(); provider.prepareInitial { true }
        engine.quota = 1
        assertEquals(2, provider.scores("A normal story", null).size)
        assertTrue(provider.state.value.paused)
        assertEquals(2, provider.state.value.completed)
        assertEquals(2, provider.scores("A normal story", null).size)
    }

    @Test fun backgroundStopsPreparationAndForegroundCanContinue() = runTest {
        val engine = Engine()
        val provider = NativeArticleCategories(engine, InMemoryArticleEmbeddingCache(), training())
        provider.refresh()
        provider.prepareInitial { engine.inputs.size < 1 }
        assertEquals(1, provider.state.value.completed)
        provider.prepareInitial { true }
        assertFalse(provider.state.value.incomplete)
    }

    @Test fun nativeRuntimeWorksWithoutDownloadedModelAndRechecksOnForeground() = runTest {
        val engine = Engine()
        val repository = training()
        repository.loadDefaults()
        val settings = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow(), { repository.categories })
        settings.setEnabled(true)
        val runtime = ArticleCategoryRuntime(settings, null, null, backgroundScope,
            training = repository, nativeEngine = engine, nativeCache = InMemoryArticleEmbeddingCache())
        runtime.start(); runCurrent()
        assertTrue(runtime.modelReady)
        assertTrue(runtime.usingNative)
        assertEquals(ArticleCategoryStatus.READY, runtime.state.value.status)
        assertEquals(2, runtime.scoresForStory(123, "A new story", null, null).size)
        runCurrent()
        assertNotNull(runtime.cachedScores(123, "A new story", null))
        val rich = runtime.scoresForStory(124, "Another story", null, "A detailed discussion of the new discoveries in astronomy and space exploration.")
        val calls = engine.inputs.size
        assertEquals(rich, runtime.scoresForStory(124, "Another story", null, null))
        assertEquals(calls, engine.inputs.size, "A title-only caller must reuse the richer cached result")
        assertEquals(ArticleCategoryInputSource.PREVIEW_TEXT, runtime.cachedResult(124, "Another story", null)?.inputSource)
        runtime.setForeground(false); runCurrent()
        assertEquals(ArticleCategoryStatus.PAUSED, runtime.state.value.status)
        engine.available = false
        runtime.setForeground(true); runCurrent()
        assertFalse(runtime.usingNative)
        assertNull(runtime.cachedScores(123, "A new story", null))
        assertNull(repository.nativeSpace)
        assertEquals(ArticleCategoryStatus.UNAVAILABLE, runtime.state.value.status)
    }

    @Test fun nativeCustomExamplesCannotLeakIntoGemmaComparisons() = runTest {
        val repository = training()
        val custom = repository.create("Custom")
        repository.addExamples(custom, listOf(EmbeddedTrainingStory(ArticleTrainingStory(99, "Native example"),
            listOf(1f, 0f, 0f, 0f), embeddingSignature = "native")))
        assertFalse(custom in repository.centers())
        assertTrue(repository.exampleSimilarities(custom).isEmpty())
        val provider = NativeArticleCategories(Engine(), InMemoryArticleEmbeddingCache(), repository)
        provider.refresh(); provider.prepareInitial { true }
        assertTrue(custom in repository.centers())
        assertEquals(1, repository.exampleSimilarities(custom).size)
    }

    private fun training() = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { seed() } }
    private fun seed(): ByteArray {
        val stories = listOf("AI first", "AI second", "Space first", "Space second").mapIndexed { i, title -> ArticleTrainingStory(i + 1, title) }
        val header = Json.encodeToString(ArticleTrainingSeed(stories, mapOf("ai" to listOf(1, 2), "space" to listOf(3, 4)),
            ArticleCategoryTraining.MODEL_SHA256, ArticleEmbeddingInput.VERSION, ArticleCategories.REPRESENTATION_VERSION)).encodeToByteArray()
        val bytes = ArrayList<Byte>()
        fun int(value: Int) { repeat(4) { bytes.add((value ushr (it * 8)).toByte()) } }
        bytes.addAll("HNC2".encodeToByteArray().toList()); int(header.size); bytes.addAll(header.toList())
        stories.forEach { repeat(2) { repeat(768) { int((if (it == 0) 1f else 0f).toBits()) } } }
        return bytes.toByteArray()
    }
}
