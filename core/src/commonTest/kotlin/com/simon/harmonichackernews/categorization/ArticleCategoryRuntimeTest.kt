package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.ArticleEmbeddingEngine
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.summary.*
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFailsWith
import kotlin.test.fail

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ArticleCategoryRuntimeTest {
    @Test
    fun cancelledHistoryPreviewReleasesTheModelWithoutWritingExamples() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        training.removeExample("ai", 2)
        val fixture = fixture(training)
        fixture.engine.vector = ArticleCategoryTrainingTest.unit(0)
        fixture.models.preload()
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        fixture.engine.beforeEmbed = { started.complete(Unit); finished.await() }
        val job = launch {
            fixture.runtime.previewSimilarities("ai", listOf(ArticleTrainingStory(99, "History"))) { _, _ -> fail("Cancelled preview must not publish") }
        }
        started.await()
        job.cancel()
        finished.complete(Unit)
        runCurrent()
        job.join()
        assertEquals(1, fixture.engine.closes)
        assertEquals(listOf(1), training.examples("ai").map { it.id })
        assertEquals(ArticleCategoryStatus.OFF, fixture.runtime.state.value.status)
    }

    @Test
    fun historyPreviewDoesNotTrainAndReusesInferenceWhenTheStoryIsAdded() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        training.removeExample("ai", 2)
        val fixture = fixture(training)
        fixture.engine.vector = ArticleCategoryTrainingTest.unit(0)
        fixture.models.preload()
        fixture.runtime.start()
        runCurrent()
        val story = ArticleTrainingStory(99, "History example")
        val scores = mutableMapOf<Int, Float>()
        fixture.runtime.previewSimilarities("ai", listOf(story)) { id, score -> scores[id] = score }
        assertEquals(mapOf(99 to 1f), scores)
        assertEquals(listOf(1), training.examples("ai").map { it.id })
        assertEquals(1, fixture.engine.embeddings)
        assertEquals(1, fixture.engine.closes)
        assertEquals(ArticleCategoryStatus.OFF, fixture.runtime.state.value.status)
        assertFalse(fixture.settings.snapshot().enabled)
        fixture.runtime.train("ai", listOf(story)) { _, _ -> }
        assertEquals(1, fixture.engine.embeddings, "Adding should reuse the preview embedding")
        assertEquals(listOf(1, 99), training.examples("ai").map { it.id })
    }

    @Test
    fun emptyCategoryPreviewNeedsNoModelAndMemoryPressureStopsOtherPreviews() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        val category = training.create("Empty")
        val fixture = fixture(training, memoryAvailable = { false })
        fixture.models.preload()
        fixture.runtime.previewSimilarities(category, listOf(ArticleTrainingStory(99, "History"))) { _, _ -> fail("No center yet") }
        assertEquals(0, fixture.engine.loads)
        assertFailsWith<IllegalStateException> {
            fixture.runtime.previewSimilarities("ai", listOf(ArticleTrainingStory(99, "History"))) { _, _ -> }
        }
        assertEquals(0, fixture.engine.loads)
    }

    @Test
    fun staysUnloadedUntilEnabledCachesScoresAndClosesOnDisable() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.runtime.start()
        runCurrent()
        assertEquals(0, fixture.engine.loads)
        fixture.settings.setEnabled(true)
        runCurrent()
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
        assertEquals(1, fixture.engine.loads)
        fixture.runtime.scores("A model", "An explanation of the model architecture and its training process.")
        fixture.runtime.scores("A model", "An explanation of the model architecture and its training process.")
        assertEquals(1, fixture.engine.embeddings)
        fixture.settings.rename("ai", "Models")
        fixture.settings.setCutoff(0.8f)
        runCurrent()
        fixture.runtime.scores("A model", "An explanation of the model architecture and its training process.")
        assertEquals(1, fixture.engine.embeddings)
        fixture.runtime.scores("A model", "An updated explanation covering the model evaluation and limitations.")
        assertEquals(2, fixture.engine.embeddings)
        fixture.settings.setEnabled(false)
        runCurrent()
        assertEquals(1, fixture.engine.closes)
        assertEquals(ArticleCategoryStatus.OFF, fixture.runtime.state.value.status)
        assertTrue(fixture.runtime.scores("A model", null).isEmpty())
        assertFalse(fixture.models.select(LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2))
        assertEquals(null, fixture.models.firstReadyDownloadableModel())
    }

    @Test
    fun failedLoadCanBeRetriedWithoutRestartingTheApp() = runTest {
        val fixture = fixture()
        fixture.engine.failLoad = true
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        assertEquals(ArticleCategoryStatus.FAILED, fixture.runtime.state.value.status)
        assertEquals(0, fixture.engine.closes)
        fixture.engine.failLoad = false
        fixture.runtime.retry()
        runCurrent()
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
        assertEquals(2, fixture.engine.loads)
    }

    @Test
    fun trainsFromHistoryWhileOffAndReleasesTheTemporaryModel() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        val category = training.create("Robotics")
        val fixture = fixture(training)
        fixture.engine.vector = ArticleCategoryTrainingTest.unit(2)
        fixture.models.preload()
        fixture.runtime.start()
        runCurrent()
        fixture.runtime.train(category, listOf(ArticleTrainingStory(99, "Robot"))) { _, _ -> }
        assertEquals(1, fixture.engine.loads)
        assertEquals(1, fixture.engine.closes)
        assertEquals(ArticleCategoryStatus.OFF, fixture.runtime.state.value.status)
        assertEquals(listOf(99), training.examples(category).map { it.id })
        assertFalse(fixture.settings.snapshot().enabled)
    }

    @Test
    fun cancellingTrainingCommitsNoPartialBatchAndReleasesTheModel() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        val category = training.create("Robotics")
        val fixture = fixture(training)
        fixture.engine.beforeEmbed = { awaitCancellation() }
        fixture.models.preload()
        val job = launch { fixture.runtime.train(category, listOf(ArticleTrainingStory(99, "Robot"))) { _, _ -> } }
        runCurrent()
        job.cancel()
        job.join()
        assertTrue(training.examples(category).isEmpty())
        assertEquals(1, fixture.engine.closes)
    }

    @Test
    fun rejectsStoriesOutsideHistoryBeforeLoadingTheModel() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply { setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() } }
        val fixture = fixture(training)
        fixture.models.preload()
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            fixture.runtime.train("ai", listOf(ArticleTrainingStory(100, "Not in history"))) { _, _ -> }
        }
        assertEquals(0, fixture.engine.loads)
    }

    @Test
    fun memoryPressureReleasesTheEmbedderAndExplicitRetryReloadsIt() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        fixture.runtime.trimMemory()
        runCurrent()
        assertEquals(ArticleCategoryStatus.PAUSED, fixture.runtime.state.value.status)
        assertEquals(1, fixture.engine.closes)
        assertTrue(fixture.settings.snapshot().enabled)
        assertTrue(fixture.runtime.scores("A story", null).isEmpty())
        fixture.runtime.retry()
        runCurrent()
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
        assertEquals(2, fixture.engine.loads)
    }

    @Test
    fun backgroundReleasesTheModelAndForegroundResumesIt() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        fixture.runtime.setForeground(false)
        runCurrent()
        assertEquals(ArticleCategoryStatus.PAUSED, fixture.runtime.state.value.status)
        assertEquals(1, fixture.engine.closes)
        fixture.runtime.setForeground(true)
        runCurrent()
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
        assertEquals(2, fixture.engine.loads)
    }

    @Test
    fun lowSystemMemoryPreventsLoadingUntilMemoryIsAvailable() = runTest {
        var available = false
        val fixture = fixture(memoryAvailable = { available })
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        assertEquals(ArticleCategoryStatus.PAUSED, fixture.runtime.state.value.status)
        assertEquals(0, fixture.engine.loads)
        available = true
        fixture.runtime.retry()
        runCurrent()
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
    }

    @Test
    fun pressureOrBackgroundDuringInferenceDoesNotCommitTrainingExamples() = runTest {
        for (background in listOf(false, true)) {
            val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply {
                setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() }
            }
            val category = training.create("Robotics")
            val fixture = fixture(training)
            fixture.models.preload()
            fixture.engine.beforeEmbed = {
                if (background) fixture.runtime.setForeground(false) else fixture.runtime.trimMemory()
            }
            kotlin.test.assertFailsWith<IllegalStateException> {
                fixture.runtime.train(category, listOf(ArticleTrainingStory(99, "Robot"))) { _, _ -> }
            }
            assertTrue(training.examples(category).isEmpty())
            assertEquals(1, fixture.engine.closes)
        }
    }

    @Test
    fun storyResultsAreImmediatelyReusableAndNeverDowngradeToTitleOnly() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        val scores = fixture.runtime.scoresForStory(42, "A model", "https://example.com", "An explanation of the model architecture and its training process.")
        assertSame(scores, fixture.runtime.cachedScores(42, "A model", "https://example.com"))
        assertSame(scores, fixture.runtime.scoresForStory(42, "A model", "https://example.com", null))
        assertSame(scores, fixture.runtime.scoresForStory(42, "A model", "https://example.com", "An explanation of the model architecture and its training process."))
        assertEquals(1, fixture.engine.embeddings)
        assertEquals(ArticleCategoryInputSource.PREVIEW_TEXT,
            fixture.runtime.cachedResult(42, "A model", "https://example.com")?.inputSource)
        assertNull(fixture.runtime.cachedScores(43, "A model", "https://example.com"))
        assertNull(fixture.runtime.cachedScores(42, "Different title", "https://example.com"))
        assertNull(fixture.runtime.cachedScores(42, "A model", "https://different.example"))
        fixture.runtime.setForeground(false)
        runCurrent()
        assertEquals(1, fixture.engine.closes)
        assertSame(scores, fixture.runtime.cachedScores(42, "A model", "https://example.com"))
        fixture.runtime.setForeground(true)
        runCurrent()
        assertSame(scores, fixture.runtime.scoresForStory(42, "A model", "https://example.com", null))
        assertEquals(1, fixture.engine.embeddings)
    }

    @Test
    fun cachedResultsAndObserversKeepScoresWithTheirActualInputProvenance() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        val observed = mutableListOf<ArticleCategoryResult?>()
        backgroundScope.launch { fixture.runtime.observeResult(42, "A model", "url").collect { observed += it } }
        runCurrent()
        assertEquals(listOf<ArticleCategoryResult?>(null), observed)
        val titleOnly = fixture.runtime.scoresForStory(42, "A model", "url", null)
        runCurrent()
        assertEquals(ArticleCategoryInputSource.TITLE_ONLY, observed.last()?.inputSource)
        assertSame(titleOnly, observed.last()?.scores)
        assertEquals(ArticleCategoryInputSource.TITLE_ONLY, fixture.runtime.cachedResult(42, "A model", "url")?.inputSource)
        fixture.runtime.scoresForStory(42, "A model", "url", "A model")
        assertEquals(1, fixture.engine.embeddings, "A repeated title is not preview evidence")
        val withPreview = fixture.runtime.scoresForStory(42, "A model", "url",
            "An explanation of the model architecture and its training process.")
        runCurrent()
        assertEquals(ArticleCategoryInputSource.PREVIEW_TEXT, observed.last()?.inputSource)
        assertSame(withPreview, observed.last()?.scores)
        fixture.runtime.scoresForStory(42, "A model", "url", null)
        assertEquals(ArticleCategoryInputSource.PREVIEW_TEXT, fixture.runtime.cachedResult(42, "A model", "url")?.inputSource)
        assertEquals(2, fixture.engine.embeddings, "Inspecting cached results must not repeat inference")
    }

    @Test
    fun richerMetadataRefreshKeepsThePreviousResultUntilReplacementIsReady() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        val initial = fixture.runtime.scoresForStory(42, "Story", "url", null)
        val release = CompletableDeferred<Unit>()
        fixture.engine.beforeEmbed = { release.await() }
        val refresh = launch { fixture.runtime.scoresForStory(42, "Story", "url", "New metadata explaining the article topic and its important details.") }
        runCurrent()
        assertSame(initial, fixture.runtime.cachedScores(42, "Story", "url"))
        fixture.engine.vector = floatArrayOf(0f, 1f)
        release.complete(Unit)
        refresh.join()
        assertEquals(0f, fixture.runtime.cachedScores(42, "Story", "url")!!.single().similarity)
        assertEquals(2, fixture.engine.embeddings)
    }

    @Test
    fun eachInputUsesReferencesFromTheSameViewWithOneInference() = runTest {
        val fixture = fixture(prototypes = { titleOnly ->
            mapOf((if (titleOnly) "software" else "science") to floatArrayOf(1f, 0f))
        })
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        assertEquals("software", fixture.runtime.scores("Story", null).single().categoryId)
        assertEquals(1, fixture.engine.embeddings)
        val preview = "A useful preview describing the scientific subject of this article."
        assertEquals("science", fixture.runtime.scores("Story", preview).single().categoryId)
        assertEquals(2, fixture.engine.embeddings)
        fixture.runtime.scores("Story", preview)
        assertEquals(2, fixture.engine.embeddings)
    }

    @Test
    fun scrollingAwayDuringNativeInferenceKeepsTheCompletedResultForTheNextScreen() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        val release = CompletableDeferred<Unit>()
        fixture.engine.beforeEmbed = { release.await() }
        val row = launch { fixture.runtime.scoresForStory(42, "Story", null, null) }
        runCurrent()
        row.cancel()
        assertFalse(row.isCompleted)
        release.complete(Unit)
        row.join()
        assertEquals(1f, fixture.runtime.cachedScores(42, "Story", null)!!.single().similarity)
        fixture.runtime.scoresForStory(42, "Story", null, null)
        assertEquals(1, fixture.engine.embeddings)
        assertEquals(ArticleCategoryStatus.READY, fixture.runtime.state.value.status)
    }

    @Test
    fun simultaneousScreensShareOneInference() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        val release = CompletableDeferred<Unit>()
        fixture.engine.beforeEmbed = { release.await() }
        val rows = List(2) { launch { fixture.runtime.scoresForStory(42, "Story", null, null) } }
        runCurrent()
        assertEquals(1, fixture.engine.embeddings)
        release.complete(Unit)
        rows.forEach { it.join() }
        assertEquals(1, fixture.engine.embeddings)
    }

    @Test
    fun editedExamplesInvalidateScoresAndRescoreWithoutRepeatingEmbeddingInference() = runTest {
        val training = ArticleCategoryTraining(InMemoryKeyValueStore(), { FloatArray(768) }).apply {
            setDefaultLoader { ArticleCategoryTrainingTest.seedBytes() }
        }
        training.removeExample("ai", 1)
        val fixture = fixture(training)
        fixture.engine.vector = ArticleCategoryTrainingTest.unit(1)
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        fixture.runtime.scoresForStory(42, "Story", null, null)
        assertEquals(1f, fixture.runtime.cachedScores(42, "Story", null)!!.single().similarity)
        training.removeExample("ai", 2)
        assertNull(fixture.runtime.cachedScores(42, "Story", null))
        runCurrent()
        assertTrue(fixture.runtime.scoresForStory(42, "Story", null, null).isEmpty())
        assertEquals(emptyList(), fixture.runtime.cachedScores(42, "Story", null))
        assertEquals(1, fixture.engine.embeddings)
    }

    @Test
    fun resultCacheIsBoundedAndRemovingTheModelClearsIt() = runTest {
        val fixture = fixture()
        fixture.models.preload()
        fixture.settings.setEnabled(true)
        fixture.runtime.start()
        runCurrent()
        repeat(ArticleCategoryRuntime.MAX_CACHED_STORIES + 1) { id ->
            fixture.runtime.scoresForStory(id, "Story", null, null)
        }
        assertNull(fixture.runtime.cachedScores(0, "Story", null))
        assertTrue(fixture.runtime.cachedScores(ArticleCategoryRuntime.MAX_CACHED_STORIES, "Story", null)!!.isNotEmpty())
        assertEquals(1, fixture.engine.embeddings)
        fixture.runtime.removeModel()
        assertNull(fixture.runtime.cachedScores(ArticleCategoryRuntime.MAX_CACHED_STORIES, "Story", null))
    }

    private fun TestScope.fixture(training: ArticleCategoryTraining? = null, memoryAvailable: () -> Boolean = { true },
        prototypes: (Boolean) -> Map<String, FloatArray> = { mapOf("ai" to floatArrayOf(1f, 0f)) }): Fixture {
        val settings = ArticleCategorySettings(InMemoryKeyValueStore(), emptyFlow())
        val engine = FakeEngine()
        val storage = object : LocalModelStorage {
            override fun snapshot(model: LocalModelDefinition) = LocalModelStorageSnapshot(
                finalFileBytes = model.sizeBytes.takeIf { !model.supportsSummarization },
            )
            override fun prepareDownload(model: LocalModelDefinition) = LocalModelStoragePreparation.Ready(snapshot(model))
            override fun remove(model: LocalModelDefinition, includeFinalFile: Boolean) = Unit
            override fun installedPath(model: LocalModelDefinition) = "/models/embedding.litertlm"
            override fun storedBytes() = LocalModelCatalog.embeddingModel.sizeBytes
            override fun clearStoredModels() = true
        }
        val transfers = object : LocalModelTransferScheduler {
            override fun work(modelId: String): LocalModelWorkSnapshot? = null
            override fun isActive(modelId: String) = false
            override fun enqueue(model: LocalModelDefinition) = Unit
            override fun cancel(modelId: String, onCancelled: () -> Unit) = onCancelled()
        }
        val delivery = object : LocalModelRuntimeDelivery {
            override val included = true
            override fun status(runtime: LocalModelRuntime) = LocalRuntimeInstallStatus(LocalRuntimeInstallState.INSTALLED, runtime = runtime)
            override fun isInstalled(runtime: LocalModelRuntime) = true
            override suspend fun request(model: LocalModelDefinition): String? = null
            override fun cancel(runtime: LocalModelRuntime) = Unit
            override fun setObserver(observer: () -> Unit) = Unit
            override fun setModelDownloadStarter(starter: suspend (String) -> String?) = Unit
            override fun engineClassName(runtime: LocalModelRuntime): String? = null
            override fun runtimeLabel(runtime: LocalModelRuntime) = "LiteRT-LM"
        }
        val models = LocalModelService(
            InMemoryKeyValueStore(), storage, transfers, delivery, LocalModelDeviceCapabilities(true, true),
            models = listOf(LocalModelCatalog.models.first(), LocalModelCatalog.embeddingModel),
            storageDispatcher = StandardTestDispatcher(testScheduler), scope = backgroundScope,
        )
        return Fixture(settings, models, engine, ArticleCategoryRuntime(settings, models, engine, backgroundScope, prototypes = prototypes,
            training = training, isHistoryStory = { it == 99 }, memoryAvailable = memoryAvailable,
            scoringDispatcher = StandardTestDispatcher(testScheduler), embeddingOrigin = { FloatArray(2) }))
    }

    private class FakeEngine : ArticleEmbeddingEngine {
        var loads = 0
        var embeddings = 0
        var closes = 0
        var failLoad = false
        var vector = floatArrayOf(1f, 0f)
        var beforeEmbed: suspend () -> Unit = {}
        override suspend fun load(modelPath: String) {
            loads++
            if (failLoad) error("Failed to initialize")
        }
        override suspend fun embed(text: String): FloatArray {
            embeddings++
            beforeEmbed()
            return vector
        }
        override suspend fun close() { closes++ }
    }
    private data class Fixture(
        val settings: ArticleCategorySettings,
        val models: LocalModelService,
        val engine: FakeEngine,
        val runtime: ArticleCategoryRuntime,
    )
}
