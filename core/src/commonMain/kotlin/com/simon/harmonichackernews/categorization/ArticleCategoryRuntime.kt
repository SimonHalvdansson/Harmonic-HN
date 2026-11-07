package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.ArticleEmbeddingEngine
import com.simon.harmonichackernews.platform.NativeArticleEmbeddingEngine
import com.simon.harmonichackernews.platform.EmbeddingQuotaException
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
import kotlinx.coroutines.flow.map
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
    val native: NativeEmbeddingProgress = NativeEmbeddingProgress(checked = true),
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
    nativeEngine: NativeArticleEmbeddingEngine? = null,
    nativeCache: ArticleEmbeddingCache? = null,
    private val embeddingOrigin: (Boolean) -> FloatArray = { titleOnly ->
        if (titleOnly) ArticleCategoryPrototypes.titleOrigin else ArticleCategoryPrototypes.origin
    },
) {
    private val native = if (nativeEngine != null && nativeCache != null && training != null)
        NativeArticleCategories(nativeEngine, nativeCache, training, initiallyActive = false) else null
    val usingNative: Boolean get() = settings.snapshot().preferSystemEmbeddings && native?.state?.value?.model != null
    private var nativeJob: Job? = null
    private val lock = Mutex()
    private val cache = linkedMapOf<String, FloatArray>()
    private data class StoryKey(val id: Int, val title: String, val url: String)
    private data class StoryScores(
        val input: String,
        val hasDescription: Boolean,
        val configuration: ArticleTrainingConfiguration?,
        val maxInputTokens: Int,
        val scores: List<ArticleCategoryScore>,
        val space: String? = null,
    ) {
        fun matches(configuration: ArticleTrainingConfiguration?, maxInputTokens: Int, space: String? = null) =
            this.configuration === configuration && this.maxInputTokens == maxInputTokens && this.space == space
        fun covers(input: String, hasDescription: Boolean) = this.input == input || (this.hasDescription && !hasDescription)
        fun result() = ArticleCategoryResult(scores,
            if (hasDescription) ArticleCategoryInputSource.PREVIEW_TEXT else ArticleCategoryInputSource.TITLE_ONLY)
    }
    // Small, immutable snapshots survive row disposal and native-engine unloading. Readers never
    // acquire the inference mutex or touch disk. Each screen observes only its own story's result.
    private val storyScores = MutableStateFlow<Map<StoryKey, StoryScores>>(emptyMap())

    fun cachedScores(storyId: Int, title: String, url: String?): List<ArticleCategoryScore>? =
        cachedStory(storyId, title, url)?.scores

    fun observeScores(storyId: Int, title: String, url: String?): Flow<List<ArticleCategoryScore>?> =
        observeResult(storyId, title, url).map { it?.scores }.distinctUntilChanged()

    fun cachedResult(storyId: Int, title: String, url: String?): ArticleCategoryResult? =
        cachedStory(storyId, title, url)?.result()

    fun observeResult(storyId: Int, title: String, url: String?): Flow<ArticleCategoryResult?> =
        combine(storyScores, training?.state ?: flowOf<ArticleTrainingConfiguration?>(null), settings.updates) { entries, configuration, preferences ->
            entries[StoryKey(storyId, title, url.orEmpty())]?.takeIf { it.matches(configuration, preferences.maxInputTokens, nativeSpaceKey()) }?.result()
        }.distinctUntilChanged()

    private fun cachedStory(storyId: Int, title: String, url: String?): StoryScores? =
        storyScores.value[StoryKey(storyId, title, url.orEmpty())]?.takeIf { it.matches(training?.state?.value, settings.snapshot().maxInputTokens, nativeSpaceKey()) }
    private fun nativeSpaceKey(): String? = native?.state?.value?.takeIf { usingNative }?.let { progress ->
        progress.model?.let { "${it.signature}:${it.dimensions}:${progress.revision}" }
    }
    private val mutableState = MutableStateFlow(ArticleCategoryRuntimeState(native = NativeEmbeddingProgress(checked = native == null)))
    val state = mutableState.asStateFlow()
    val supported: Boolean get() = usingNative || (engine != null && models?.isIncluded == true)
    val modelReady: Boolean get() = usingNative || models?.let { service ->
        val model = LocalModelCatalog.embeddingModel
        supported && service.isSupported(model) && service.isDownloaded(model) && service.isRuntimeInstalled(model.runtime)
    } == true
    private var job: Job? = null
    private var loaded = false
    private var loadedInputTokens: Int? = null
    private var cacheInputTokens: Int? = null
    private val retries = MutableStateFlow(0)
    private data class Resources(val foreground: Boolean = true, val paused: Boolean = false)
    private val resources = MutableStateFlow(Resources())
    fun retry() {
        resources.update { it.copy(paused = !memoryAvailable()) }
        retries.value += 1
    }
    fun resumeNativePreparation() {
        scope.launch {
            if (!usingNative || !resources.value.foreground) return@launch
            resources.update { it.copy(paused = !memoryAvailable()) }
            native?.prepareInitial(force = true) { usingNative && resources.value.foreground && !resources.value.paused }
        }
    }

    fun setForeground(foreground: Boolean) {
        resources.update { it.copy(foreground = foreground, paused = if (foreground) !memoryAvailable() else it.paused) }
    }
    fun trimMemory() {
        resources.update { it.copy(paused = true) }
        if (settings.snapshot().enabled) publish(ArticleCategoryStatus.PAUSED)
    }

    fun start() {
        if (job != null || nativeJob != null) return
        native?.let { provider ->
            nativeJob = scope.launch {
                try {
                    launch {
                        var previousRevision = -1
                        var previousModel: String? = null
                        provider.state.collect { progress ->
                            if (previousRevision != progress.revision || previousModel != progress.model?.signature) {
                                if (settings.snapshot().preferSystemEmbeddings) storyScores.value = emptyMap()
                                previousRevision = progress.revision
                                previousModel = progress.model?.signature
                            }
                            mutableState.update { it.copy(native = progress, trainingVersion = it.trainingVersion + 1) }
                        }
                    }
                    launch {
                        combine(resources.map { it.foreground }.distinctUntilChanged(), retries) { foreground, _ -> foreground }
                            .collect { foreground -> if (foreground) lock.withLock { provider.refresh() } }
                    }
                    combine(settings.updates, resources, training!!.state,
                        provider.state.map { it.checked to it.model?.signature }.distinctUntilChanged()) { preferences, resource, configuration, model ->
                        Triple(preferences.enabled && resource.foreground && !resource.paused, configuration, model)
                    }.collect { (active, _, model) ->
                        if (!model.first) return@collect
                        if (usingNative) {
                            provider.trainingChanged()
                            lock.withLock {
                                if (usingNative) {
                                    release()
                                    cache.clear()
                                    provider.activate(true)
                                    publish(if (active) ArticleCategoryStatus.READY else if (!settings.snapshot().enabled) ArticleCategoryStatus.OFF else ArticleCategoryStatus.PAUSED)
                                }
                            }
                            if (active) provider.prepareInitial { usingNative && settings.snapshot().enabled && resources.value.foreground && !resources.value.paused }
                        } else {
                            lock.withLock { if (!usingNative) provider.activate(false) }
                            if (engine == null || models?.isIncluded != true) publish(ArticleCategoryStatus.UNAVAILABLE)
                        }
                    }
                } finally { withContext(NonCancellable) { provider.close() } }
            }
        }
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
            combine(settings.updates, service.cachedState,
                combine(retries, native?.state?.map { it.checked to it.model?.signature }?.distinctUntilChanged() ?: flowOf(true to null)) { retry, nativeState -> retry to nativeState },
                training?.state ?: flowOf(ArticleTrainingConfiguration()), resources) { preferences, manager, retry, configuration, resourceState ->
                val available = modelReady && manager.statuses[LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2]?.state == LocalModelTransferState.DOWNLOADED
                (Triple(preferences.enabled to preferences.maxInputTokens, available, retry to preferences.preferSystemEmbeddings) to configuration) to resourceState
            }.distinctUntilChanged().collect { (configuration, resourceState) ->
                if (native?.state?.value?.checked == false || usingNative) return@collect
                val currentTraining = training?.state?.value
                val maxInputTokens = settings.snapshot().maxInputTokens
                storyScores.update { entries ->
                    if (entries.values.any { !it.matches(currentTraining, maxInputTokens) }) entries.filterValues { it.matches(currentTraining, maxInputTokens) }
                    else entries
                }
                val lifecycle = configuration.first
                val (enabledAndLimit, available) = lifecycle
                val enabled = enabledAndLimit.first
                val paused = !resourceState.foreground || resourceState.paused || !memoryAvailable()
                if (!enabled || !available || paused) publish(when {
                    !enabled -> ArticleCategoryStatus.OFF
                    !available -> ArticleCategoryStatus.NEEDS_MODEL
                    else -> ArticleCategoryStatus.PAUSED
                })
                lock.withLock {
                    if (usingNative) return@withLock
                    native?.activate(false)
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
        if (usingNative && title.isNotBlank() && settings.snapshot().enabled && resources.value.foreground && !resources.value.paused) {
            val input = ArticleCategories.input(title, description)
            val hasDescription = input != ArticleCategories.input(title, null)
            if (storyId != null) cachedStory(storyId, title, url)?.takeIf { it.covers(input, hasDescription) }?.let { return it.scores }
            val configuration = training?.state?.value
            val signature = native!!.state.value.model?.signature
            return try {
                val scores = native.scores(title, description) { usingNative && settings.snapshot().enabled && resources.value.foreground && !resources.value.paused }
                if (!usingNative || configuration !== training?.state?.value || signature != native.state.value.model?.signature ||
                    !settings.snapshot().enabled || !resources.value.foreground || resources.value.paused) return emptyList()
                if (storyId != null) {
                    val entry = StoryScores(input, hasDescription, configuration, settings.snapshot().maxInputTokens, scores, nativeSpaceKey())
                    storyScores.update { old -> LinkedHashMap(old).apply {
                        val key = StoryKey(storyId, title, url.orEmpty())
                        remove(key)
                        put(key, entry)
                        while (size > MAX_CACHED_STORIES) remove(keys.first())
                    } }
                }
                scores
            } catch (e: CancellationException) { throw e }
            catch (_: EmbeddingQuotaException) { emptyList() }
            catch (_: Exception) { emptyList() }
        }
        if (usingNative || title.isBlank() || !settings.snapshot().enabled || state.value.status != ArticleCategoryStatus.READY) return emptyList()
        val input = ArticleCategories.input(title, description)
        val hasDescription = input != ArticleCategories.input(title, null)
        if (storyId != null) cachedStory(storyId, title, url)?.takeIf { it.covers(input, hasDescription) }?.let { return it.scores }
        return lock.withLock {
            currentCoroutineContext().ensureActive()
            if (usingNative || !loaded || !settings.snapshot().enabled || state.value.status != ArticleCategoryStatus.READY) return@withLock emptyList()
            if (storyId != null) cachedStory(storyId, title, url)?.takeIf { it.covers(input, hasDescription) }?.let { return@withLock it.scores }
            try {
                ensureLoaded()
                val maxInputTokens = checkNotNull(loadedInputTokens)
                // Once native inference starts it cannot be interrupted. Keep its completed result
                // even if the row scrolls away, rather than repeating the same work on its return.
                withContext(NonCancellable + scoringDispatcher) {
                    val configuration = training?.state?.value
                    val embedding = cachedEmbedding(input)
                    if (usingNative || resources.value.paused || !resources.value.foreground || !settings.snapshot().enabled) return@withContext emptyList()
                    val titleOnly = !hasDescription
                    val scores = ArticleCategories.scores(project(embedding, titleOnly), training?.centers(titleOnly) ?: prototypes(titleOnly))
                    if (configuration !== training?.state?.value || maxInputTokens != settings.snapshot().maxInputTokens) return@withContext emptyList()
                    if (storyId != null) storyScores.update { entries ->
                        LinkedHashMap(entries.filterValues { it.matches(configuration, maxInputTokens) }).apply {
                            val key = StoryKey(storyId, title, url.orEmpty())
                            remove(key)
                            put(key, StoryScores(input, hasDescription, configuration, maxInputTokens, scores))
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
        if (usingNative) {
            require(stories.isNotEmpty() && stories.size <= ArticleCategoryTraining.MAX_USER_EXAMPLES)
            check(resources.value.foreground && !resources.value.paused) { "Adding examples is paused" }
            require(stories.all { isHistoryStory(it.id) }) { "Choose stories from your history" }
            val examples = stories.distinctBy { it.id }.mapIndexed { index, story ->
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Adding examples is paused" }
                onProgress(index, stories.size)
                native!!.embedStory(story) { usingNative && resources.value.foreground && !resources.value.paused }
            }
            currentCoroutineContext().ensureActive()
            require(stories.all { isHistoryStory(it.id) }) { "A selected story is no longer in your history" }
            checkNotNull(training).addExamples(categoryId, examples)
            native!!.trainingChanged()
            onProgress(stories.size, stories.size)
            return@withLock
        }
        require(modelReady) { "Download embedding model" }
        check(resources.value.foreground && !resources.value.paused && memoryAvailable()) { "Not enough available memory. Try again later." }
        val repository = checkNotNull(training)
        require(stories.isNotEmpty() && stories.size <= ArticleCategoryTraining.MAX_USER_EXAMPLES)
        require(stories.all { isHistoryStory(it.id) }) { "Choose stories from your history" }
        val keepLoaded = settings.snapshot().enabled
        try {
            ensureLoaded()
            val maxInputTokens = checkNotNull(loadedInputTokens)
            val examples = stories.distinctBy { it.id }.mapIndexed { index, story ->
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Adding examples stopped to free memory. Try again later." }
                check(settings.snapshot().maxInputTokens == maxInputTokens) { "Input token limit changed. Try adding the stories again." }
                onProgress(index, stories.size)
                val headline = cachedEmbedding(ArticleCategories.input(story.title, null))
                val preview = cachedEmbedding(ArticleCategories.input(story.title, story.description))
                EmbeddedTrainingStory(story, preview.toList(), headline.toList())
            }
            currentCoroutineContext().ensureActive()
            check(resources.value.foreground && !resources.value.paused) { "Adding examples stopped to free memory. Try again later." }
            require(stories.all { isHistoryStory(it.id) }) { "A selected story is no longer in your history" }
            check(settings.snapshot().maxInputTokens == maxInputTokens) { "Input token limit changed. Try adding the stories again." }
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
        if (usingNative) {
            for (story in stories) {
                currentCoroutineContext().ensureActive()
                check(resources.value.foreground && !resources.value.paused) { "Similarities are paused" }
                require(isHistoryStory(story.id))
                native!!.scores(story.title, story.description) { usingNative && resources.value.foreground && !resources.value.paused }.firstOrNull { it.categoryId == categoryId }?.let { onResult(story.id, it.similarity) }
            }
            return@withLock
        }
        native?.activate(false)
        val repository = checkNotNull(training)
        val previewCenter = repository.centers(false)[categoryId]
        val titleCenter = repository.centers(true)[categoryId]
        if (previewCenter == null && titleCenter == null) return@withLock
        require(modelReady) { "Download embedding model" }
        check(resources.value.foreground && !resources.value.paused && memoryAvailable()) { "Similarities unavailable. Try again later." }
        val keepLoaded = settings.snapshot().enabled
        try {
            ensureLoaded()
            val maxInputTokens = checkNotNull(loadedInputTokens)
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
                if (settings.snapshot().maxInputTokens != maxInputTokens) return@withLock
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
        if (!usingNative) { settings.setEnabled(false); publish(ArticleCategoryStatus.OFF) }
        lock.withLock {
            release()
            cache.clear()
            storyScores.value = emptyMap()
            models?.remove(LocalModelCatalog.MODEL_EMBEDDING_GEMMA_2)
        }
    }

    private suspend fun ensureLoaded() {
        native?.activate(false)
        val maxInputTokens = settings.snapshot().maxInputTokens
        if (loaded && loadedInputTokens == maxInputTokens) { prepareFallbackExamples(); return }
        release()
        if (cacheInputTokens != maxInputTokens) {
            cache.clear()
            cacheInputTokens = maxInputTokens
        }
        check(memoryAvailable()) { "Not enough available memory for local AI" }
        publish(ArticleCategoryStatus.LOADING)
        // Native initialization cannot be interrupted. Record ownership even when the caller is
        // cancelled, so training's finally block can release the engine after initialization.
        withContext(kotlinx.coroutines.NonCancellable) {
            checkNotNull(engine).load(checkNotNull(models).installedPath(LocalModelCatalog.embeddingModel), maxInputTokens)
            loaded = true
            loadedInputTokens = maxInputTokens
        }
        prepareFallbackExamples()
    }

    private suspend fun prepareFallbackExamples() {
        val repository = training ?: return
        repository.state.value.added.forEach { (category, examples) ->
            val missing = examples.filter { it.embeddingSignature != null }
            if (missing.isNotEmpty()) repository.addExamples(category, missing.map { example ->
                val story = example.story
                EmbeddedTrainingStory(story, cachedEmbedding(ArticleCategories.input(story.title, story.description)).toList(),
                    cachedEmbedding(ArticleCategories.input(story.title, null)).toList())
            })
        }
    }

    private suspend fun release() {
        if (!loaded) return
        loaded = false
        loadedInputTokens = null
        engine?.close()
    }

    private fun project(embedding: FloatArray, titleOnly: Boolean): FloatArray =
        training?.project(embedding, titleOnly) ?: ArticleCategories.project(embedding, embeddingOrigin(titleOnly))

    private suspend fun cachedEmbedding(input: String): FloatArray {
        cache.remove(input)?.let { cache[input] = it; return it }
        val embedding = ArticleEmbeddingInput.embedWithinLimit(input, checkNotNull(loadedInputTokens), checkNotNull(engine)::embed)
        currentCoroutineContext().ensureActive()
        cache[input] = embedding
        while (cache.size > 512) cache.remove(cache.keys.first())
        return embedding
    }

    private fun publish(status: ArticleCategoryStatus, error: String? = null) {
        mutableState.update { it.copy(status = status, error = error, trainingVersion = it.trainingVersion + 1) }
    }

    internal companion object { const val MAX_CACHED_STORIES = 1024 }
}
