package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.ArticleEmbeddingEngine
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.summary.LocalModelTransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class ArticleCategoryStatus { OFF, UNAVAILABLE, NEEDS_MODEL, LOADING, READY, PAUSED, FAILED }
data class ArticleCategoryRuntimeState(
    val status: ArticleCategoryStatus = ArticleCategoryStatus.OFF,
    val error: String? = null,
    val trainingVersion: Int = 0,
)

/** One serialized native engine and a bounded embedding cache shared by feed and training. */
class ArticleCategoryRuntime(
    private val settings: ArticleCategorySettings,
    private val models: LocalModelService?,
    private val engine: ArticleEmbeddingEngine?,
    private val scope: CoroutineScope,
    private val prototypes: (Boolean) -> Map<String, FloatArray> = { titleOnly ->
        if (titleOnly) ArticleCategoryPrototypes.titleVectors else ArticleCategoryPrototypes.vectors
    },
    private val training: ArticleCategoryTraining? = null,
    private val isHistoryStory: (Int) -> Boolean = { false },
    private val memoryAvailable: () -> Boolean = { true },
    private val scoringDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val embeddingOrigin: (Boolean) -> FloatArray = { titleOnly ->
        if (titleOnly) ArticleCategoryPrototypes.titleOrigin else ArticleCategoryPrototypes.origin
    },
) {
    private val lock = Mutex()
    private val cache = linkedMapOf<String, FloatArray>()
    private data class StoryKey(val id: Int, val title: String, val url: String)
    private data class StoryScores(
        val input: String,
        val hasDescription: Boolean,
        val configuration: ArticleTrainingConfiguration?,
        val scores: List<ArticleCategoryScore>,
    ) {
        fun matches(configuration: ArticleTrainingConfiguration?) = this.configuration === configuration
        fun covers(input: String, hasDescription: Boolean) = this.input == input || (this.hasDescription && !hasDescription)
    }
    // Small, immutable snapshots survive row disposal and native-engine unloading. Readers never
    // acquire the inference mutex or touch disk. Each screen observes only its own story's result.
    private val storyScores = MutableStateFlow<Map<StoryKey, StoryScores>>(emptyMap())

    fun cachedScores(storyId: Int, title: String, url: String?): List<ArticleCategoryScore>? =
        cachedStory(storyId, title, url)?.scores

    fun observeScores(storyId: Int, title: String, url: String?): Flow<List<ArticleCategoryScore>?> =
        combine(storyScores, training?.state ?: flowOf<ArticleTrainingConfiguration?>(null)) { entries, configuration ->
            entries[StoryKey(storyId, title, url.orEmpty())]?.takeIf { it.matches(configuration) }?.scores
        }.distinctUntilChanged()

    private fun cachedStory(storyId: Int, title: String, url: String?): StoryScores? =
        storyScores.value[StoryKey(storyId, title, url.orEmpty())]?.takeIf { it.matches(training?.state?.value) }
    private val mutableState = MutableStateFlow(ArticleCategoryRuntimeState())
    val state = mutableState.asStateFlow()
    val supported: Boolean get() = engine != null && models?.isIncluded == true
    val modelReady: Boolean get() = models?.let { service ->
        val model = LocalModelCatalog.embeddingModel
        supported && service.isSupported(model) && service.isDownloaded(model) && service.isRuntimeInstalled(model.runtime)
    } == true
    private var job: Job? = null
    private var loaded = false
    private val retries = MutableStateFlow(0)
    private data class Resources(val foreground: Boolean = true, val paused: Boolean = false)
    private val resources = MutableStateFlow(Resources())
    fun retry() {
        resources.update { it.copy(paused = !memoryAvailable()) }
        retries.value += 1
    }
    fun setForeground(foreground: Boolean) {
        resources.update { it.copy(foreground = foreground, paused = if (foreground) !memoryAvailable() else it.paused) }
    }
    fun trimMemory() {
        resources.update { it.copy(paused = true) }
        if (settings.snapshot().enabled) publish(ArticleCategoryStatus.PAUSED)
    }

    fun start() {
        if (job != null) return
        val service = models
        if (!supported || service == null) {
            publish(ArticleCategoryStatus.UNAVAILABLE)
            return
        }
        job = scope.launch {
            launch {
                var previous = service.cachedState.value.statuses[LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2]?.state
                service.cachedState.collect { manager ->
                    val current = manager.statuses[LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2]?.state
                    // Enable after a new download completes, while preserving an explicit off
                    // setting when an already-downloaded model is discovered at app startup.
                    if (previous != null && previous != LocalModelTransferState.DOWNLOADED &&
                        current == LocalModelTransferState.DOWNLOADED) {
                        settings.setEnabled(true)
                    }
                    previous = current
                }
            }
            combine(settings.updates, service.cachedState, retries,
                training?.state ?: flowOf(ArticleTrainingConfiguration()), resources) { preferences, manager, retry, configuration, resourceState ->
                val available = modelReady && manager.statuses[LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2]?.state == LocalModelTransferState.DOWNLOADED
                (Triple(preferences.enabled, available, retry) to configuration) to resourceState
            }.distinctUntilChanged().collect { (configuration, resourceState) ->
                val currentTraining = training?.state?.value
                storyScores.update { entries ->
                    if (entries.values.any { !it.matches(currentTraining) }) entries.filterValues { it.matches(currentTraining) }
                    else entries
                }
                val lifecycle = configuration.first
                val (enabled, available) = lifecycle
                val paused = !resourceState.foreground || resourceState.paused || !memoryAvailable()
                if (!enabled || !available || paused) publish(when {
                    !enabled -> ArticleCategoryStatus.OFF
                    !available -> ArticleCategoryStatus.NEEDS_MODEL
                    else -> ArticleCategoryStatus.PAUSED
                })
                lock.withLock {
                    try {
                        // A queued lifecycle update may be obsolete by the time inference finishes.
                        val current = resources.value
                        if (!settings.snapshot().enabled || !modelReady || !current.foreground || current.paused || !memoryAvailable()) {
                            release()
                            cache.clear()
                            publish(when {
                                !settings.snapshot().enabled -> ArticleCategoryStatus.OFF
                                !modelReady -> ArticleCategoryStatus.NEEDS_MODEL
                                else -> ArticleCategoryStatus.PAUSED
                            })
                        } else {
                            ensureLoaded()
                            val afterLoad = resources.value
                            if (!settings.snapshot().enabled || !afterLoad.foreground || afterLoad.paused) {
                                release()
                                cache.clear()
                                publish(if (settings.snapshot().enabled) ArticleCategoryStatus.PAUSED else ArticleCategoryStatus.OFF)
                            } else publish(ArticleCategoryStatus.READY)
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { publish(ArticleCategoryStatus.FAILED, error.message) }
                }
            }
        }
    }

    suspend fun scores(title: String, description: String?): List<ArticleCategoryScore> =
        computeScores(null, title, null, description)

    suspend fun scoresForStory(storyId: Int, title: String, url: String?, description: String?): List<ArticleCategoryScore> =
        computeScores(storyId, title, url, description)

    private suspend fun computeScores(storyId: Int?, title: String, url: String?, description: String?): List<ArticleCategoryScore> {
        if (title.isBlank() || !settings.snapshot().enabled || state.value.status != ArticleCategoryStatus.READY) return emptyList()
        val input = ArticleCategories.input(title, description)
        val hasDescription = input != ArticleCategories.input(title, null)
        if (storyId != null) cachedStory(storyId, title, url)?.takeIf { it.covers(input, hasDescription) }?.let { return it.scores }
        return lock.withLock {
            currentCoroutineContext().ensureActive()
            if (!loaded || !settings.snapshot().enabled || state.value.status != ArticleCategoryStatus.READY) return@withLock emptyList()
            if (storyId != null) cachedStory(storyId, title, url)?.takeIf { it.covers(input, hasDescription) }?.let { return@withLock it.scores }
            try {
                // Once native inference starts it cannot be interrupted. Keep its completed result
                // even if the row scrolls away, rather than repeating the same work on its return.
                withContext(NonCancellable + scoringDispatcher) {
                    val configuration = training?.state?.value
                    val embedding = cachedEmbedding(input)
                    if (resources.value.paused || !resources.value.foreground || !settings.snapshot().enabled) return@withContext emptyList()
                    val titleOnly = !hasDescription
                    val scores = ArticleCategories.scores(project(embedding, titleOnly), training?.centers(titleOnly) ?: prototypes(titleOnly))
                    if (configuration !== training?.state?.value) return@withContext emptyList()
                    if (storyId != null) storyScores.update { entries ->
                        LinkedHashMap(entries.filterValues { it.matches(configuration) }).apply {
                            val key = StoryKey(storyId, title, url.orEmpty())
                            remove(key)
                            put(key, StoryScores(input, hasDescription, configuration, scores))
                            while (size > MAX_CACHED_STORIES) remove(keys.first())
                        }
                    }
                    scores
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                withContext(NonCancellable) { runCatching { release() } }
                publish(ArticleCategoryStatus.FAILED, error.message)
                emptyList()
            }
        }
    }

    /** Atomic batch: cancelling or failing a batch never leaves half of its examples saved. */
    suspend fun train(categoryId: String, stories: List<ArticleTrainingStory>, onProgress: (Int, Int) -> Unit) = lock.withLock {
        require(modelReady) { "Download embedding model" }
        check(resources.value.foreground && !resources.value.paused && memoryAvailable()) { "Not enough available memory. Try again later." }
        val repository = checkNotNull(training)
        require(stories.isNotEmpty() && stories.size <= ArticleCategoryTraining.MAX_USER_EXAMPLES)
        require(stories.all { isHistoryStory(it.id) }) { "Choose stories from your history" }
        val keepLoaded = settings.snapshot().enabled
        try {
            ensureLoaded()
            val examples = stories.distinctBy { it.id }.mapIndexed { index, story ->
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Adding examples stopped to free memory. Try again later." }
                onProgress(index, stories.size)
                val headline = cachedEmbedding(ArticleCategories.input(story.title, null))
                val preview = cachedEmbedding(ArticleCategories.input(story.title, story.description))
                EmbeddedTrainingStory(story, preview.toList(), headline.toList())
            }
            currentCoroutineContext().ensureActive()
            check(resources.value.foreground && !resources.value.paused) { "Adding examples stopped to free memory. Try again later." }
            require(stories.all { isHistoryStory(it.id) }) { "A selected story is no longer in your history" }
            withContext(Dispatchers.Default) { repository.addExamples(categoryId, examples) }
            onProgress(stories.size, stories.size)
        } finally {
            if (!keepLoaded || !settings.snapshot().enabled || resources.value.paused || !resources.value.foreground) {
                withContext(kotlinx.coroutines.NonCancellable) { release() }
                publish(if (settings.snapshot().enabled) ArticleCategoryStatus.PAUSED else ArticleCategoryStatus.OFF)
            } else publish(if (loaded) ArticleCategoryStatus.READY else ArticleCategoryStatus.FAILED)
        }
    }

    /** Read-only preview for visible history candidates. Completed inference is reused by Add. */
    suspend fun previewSimilarities(categoryId: String, stories: List<ArticleTrainingStory>, onResult: (Int, Float) -> Unit) = lock.withLock {
        val repository = checkNotNull(training)
        val previewCenter = repository.centers(false)[categoryId]
        val titleCenter = repository.centers(true)[categoryId]
        if (previewCenter == null && titleCenter == null) return@withLock
        require(modelReady) { "Download embedding model" }
        check(resources.value.foreground && !resources.value.paused && memoryAvailable()) { "Similarities unavailable. Try again later." }
        val keepLoaded = settings.snapshot().enabled
        try {
            ensureLoaded()
            stories.distinctBy { it.id }.forEach { story ->
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Similarities paused to free memory." }
                require(isHistoryStory(story.id)) { "Choose stories from your history" }
                val score = withContext(NonCancellable + scoringDispatcher) {
                    val input = ArticleCategories.input(story.title, story.description)
                    val titleOnly = input == ArticleCategories.input(story.title, null)
                    val center = checkNotNull(if (titleOnly) titleCenter else previewCenter)
                    val vector = cachedEmbedding(input)
                    ArticleCategories.scores(project(vector, titleOnly), mapOf(categoryId to center)).single().similarity
                }
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Similarities paused to free memory." }
                onResult(story.id, score)
            }
        } finally {
            if (!keepLoaded || !settings.snapshot().enabled || resources.value.paused || !resources.value.foreground) {
                withContext(NonCancellable) { release() }
                publish(if (settings.snapshot().enabled) ArticleCategoryStatus.PAUSED else ArticleCategoryStatus.OFF)
            } else publish(if (loaded) ArticleCategoryStatus.READY else ArticleCategoryStatus.FAILED)
        }
    }

    suspend fun removeModel() {
        settings.setEnabled(false)
        publish(ArticleCategoryStatus.OFF)
        lock.withLock {
            release()
            cache.clear()
            storyScores.value = emptyMap()
            models?.remove(LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2)
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        check(memoryAvailable()) { "Not enough available memory for local AI" }
        publish(ArticleCategoryStatus.LOADING)
        // Native initialization cannot be interrupted. Record ownership even when the caller is
        // cancelled, so training's finally block can release the engine after initialization.
        withContext(kotlinx.coroutines.NonCancellable) {
            checkNotNull(engine).load(checkNotNull(models).installedPath(LocalModelCatalog.embeddingModel))
            loaded = true
        }
    }

    private suspend fun release() {
        if (!loaded) return
        loaded = false
        engine?.close()
    }

    private fun project(embedding: FloatArray, titleOnly: Boolean): FloatArray =
        training?.project(embedding, titleOnly) ?: ArticleCategories.project(embedding, embeddingOrigin(titleOnly))

    private suspend fun cachedEmbedding(input: String): FloatArray {
        cache.remove(input)?.let { cache[input] = it; return it }
        val embedding = checkNotNull(engine).embed(input)
        currentCoroutineContext().ensureActive()
        cache[input] = embedding
        while (cache.size > 512) cache.remove(cache.keys.first())
        return embedding
    }

    private fun publish(status: ArticleCategoryStatus, error: String? = null) {
        mutableState.update { ArticleCategoryRuntimeState(status, error, it.trainingVersion + 1) }
    }

    internal companion object { const val MAX_CACHED_STORIES = 1024 }
}
