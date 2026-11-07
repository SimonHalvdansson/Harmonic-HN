package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Preparation status; the UI never needs to display model signatures. */
data class NativeEmbeddingProgress(
    val checked: Boolean = false,
    val model: NativeEmbeddingModel? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val paused: Boolean = false,
    val preparing: Boolean = false,
    val error: String? = null,
    val revision: Int = 0,
) {
    val incomplete: Boolean get() = model != null && completed < total
}

/** Foreground-only, serialized inference. Every successful vector is durable before the next call. */
class NativeArticleCategories(
    private val engine: NativeArticleEmbeddingEngine,
    private val cache: ArticleEmbeddingCache,
    private val training: ArticleCategoryTraining,
    initiallyActive: Boolean = true,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(NativeEmbeddingProgress())
    val state = mutableState.asStateFlow()
    private val vectors = mutableMapOf<String, FloatArray>()
    private var pending = emptyList<String>()
    private var trainingInputs = emptySet<String>()
    private val recentVectors = linkedMapOf<String, FloatArray>()
    private var initialAttempted = false
    private var active = initiallyActive
    private var space: NativeCategorySpace? = null

    suspend fun refresh() = mutex.withLock {
        val model = try { engine.model() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        val previous = state.value.model
        if (previous != model) {
            vectors.clear()
            recentVectors.clear()
            pending = emptyList()
            initialAttempted = false
            mutableState.value = state.value.copy(completed = 0, total = 0, paused = false)
            space = null
            if (active) training.setNativeSpace(null)
        }
        mutableState.value = state.value.copy(checked = true, model = model, error = null)
        if (model != null) {
            try {
                cache.select("${model.signature}:${model.dimensions}:${model.tokenLimit}:natural-preview-v1")
                rebuild()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                space = null
                if (active) training.setNativeSpace(null)
                vectors.clear()
                recentVectors.clear()
                mutableState.value = state.value.copy(model = null, error = "System embeddings couldn't be prepared. Try reopening the app.")
            }
        }
    }

    /** Round robin across categories, then across headline and preview views. */
    private suspend fun rebuild() {
        val groups = training.categories.map { training.examples(it.id) }
        val ordered = buildList {
            repeat(groups.maxOfOrNull { it.size } ?: 0) { index ->
                // Give every category a headline before spending another call on richer previews.
                for (titleOnly in listOf(true, false)) groups.forEach { examples -> examples.getOrNull(index)?.let { story ->
                    add(input(story.title, if (titleOnly) null else story.description))
                } }
            }
        }.distinct()
        trainingInputs = ordered.toSet()
        vectors.keys.retainAll(trainingInputs)
        ordered.forEach { lookup(it) }
        pending = ordered.filterNot { it in vectors }
        updateSpace()
        mutableState.value = state.value.copy(completed = ordered.size - pending.size, total = ordered.size,
            revision = state.value.revision + 1)
    }

    /** Selection changes retain the cached native space for an immediate switch back. */
    suspend fun activate(active: Boolean) = mutex.withLock {
        this.active = active
        training.setNativeSpace(if (active) space else null)
    }

    suspend fun prepareInitial(force: Boolean = false, shouldContinue: () -> Boolean) {
        val start = mutex.withLock {
            if (state.value.model == null || state.value.preparing || (!force && initialAttempted) || pending.isEmpty() || !shouldContinue()) false else {
                initialAttempted = true
                mutableState.value = state.value.copy(preparing = true, paused = false, error = null)
                true
            }
        }
        if (!start) return
        try {
            while (shouldContinue()) {
                val more = mutex.withLock { pending.isNotEmpty() && prepareOne() && pending.isNotEmpty() }
                if (!more) break
                yield() // Foreground requests can acquire the mutex between preparation calls.
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock {
                if (!shouldContinue() && !state.value.paused) initialAttempted = false
                mutableState.value = state.value.copy(preparing = false)
            } }
        }
    }

    suspend fun close() = mutex.withLock { engine.close() }

    /** No retry loop on quota: the next successful normal request buys one more preparation call. */
    private suspend fun prepareOne(): Boolean {
        val next = pending.firstOrNull() ?: return true
        return try {
            embedCached(next)
            pending = pending.drop(1)
            updateSpace()
            mutableState.value = state.value.copy(completed = state.value.total - pending.size,
                paused = false, error = null, revision = state.value.revision + 1)
            true
        } catch (e: CancellationException) { throw e }
        catch (_: EmbeddingQuotaException) {
            mutableState.value = state.value.copy(paused = true)
            false
        } catch (_: Exception) {
            mutableState.value = state.value.copy(paused = true, error = "Preparation will retry as you browse.")
            false
        }
    }

    suspend fun scores(title: String, description: String?, canContinue: () -> Boolean = { true }): List<ArticleCategoryScore> = mutex.withLock {
        if (!canContinue()) return@withLock emptyList()
        val text = input(title, description)
        val cached = lookup(text)
        val vector = cached ?: embedCached(text)
        // Always serve the foreground request first; quota exhaustion here cannot discard its result.
        currentCoroutineContext().ensureActive()
        if (cached == null && canContinue()) prepareOne()
        val titleOnly = text == input(title, null)
        val current = checkNotNull(space)
        val origin = if (titleOnly) current.titleOrigin else current.origin
        val projected = runCatching { ArticleCategories.project(vector, origin) }.getOrElse { vector }
        ArticleCategories.scores(projected, if (titleOnly) current.titleCenters else current.centers)
    }

    suspend fun embedStory(story: ArticleTrainingStory, canContinue: () -> Boolean = { true }): EmbeddedTrainingStory = mutex.withLock {
        check(canContinue()) { "Adding examples is paused" }
        val headline = embedCached(input(story.title, null))
        currentCoroutineContext().ensureActive()
        check(canContinue()) { "Adding examples is paused" }
        val preview = embedCached(input(story.title, story.description))
        currentCoroutineContext().ensureActive()
        if (canContinue()) prepareOne()
        EmbeddedTrainingStory(story, preview.toList(), headline.toList(), checkNotNull(state.value.model).signature)
    }

    suspend fun trainingChanged() = mutex.withLock { if (state.value.model != null) rebuild() }

    private suspend fun lookup(text: String): FloatArray? {
        vectors[text]?.let { return it }
        recentVectors.remove(text)?.let { remember(text, it); return it }
        val model = state.value.model ?: return null
        val stored = cache.get(text) ?: return null
        if (stored.size != model.dimensions) return null
        val vector = runCatching { ArticleCategories.normalize(stored) }.getOrNull() ?: return null
        remember(text, vector)
        return vector
    }

    private suspend fun embedCached(text: String): FloatArray {
        lookup(text)?.let { return it }
        val model = checkNotNull(state.value.model)
        // Once inference begins, save its result even if the requesting row scrolls away.
        return withContext(NonCancellable) {
            val vector = try { ArticleCategories.normalize(engine.embed(text)) }
            catch (error: EmbeddingQuotaException) {
                mutableState.value = state.value.copy(paused = true)
                throw error
            }
            require(vector.size == model.dimensions) { "System embedding dimensions changed" }
            cache.put(text, vector)
            remember(text, vector)
            vector
        }
    }

    private fun remember(text: String, vector: FloatArray) {
        if (text in trainingInputs) vectors[text] = vector else {
            recentVectors[text] = vector
            while (recentVectors.size > 512) recentVectors.remove(recentVectors.keys.first())
        }
    }

    private suspend fun updateSpace() {
        val model = state.value.model ?: return
        val groups = training.categories.associate { it.id to training.examples(it.id) }
        fun view(titleOnly: Boolean): Pair<FloatArray, Map<String, FloatArray>> {
            val byCategory = groups.mapValues { (_, stories) -> stories.mapNotNull { story ->
                vectors[input(story.title, if (titleOnly) null else story.description)]
                    ?: vectors[input(story.title, null)]
            } }
            val all = byCategory.values.flatten()
            val origin = FloatArray(model.dimensions)
            if (all.size > 1) all.forEach { v -> v.indices.forEach { origin[it] += v[it] / all.size } }
            val centers = byCategory.mapNotNull { (id, values) ->
                if (values.isEmpty()) null else {
                    val sum = FloatArray(model.dimensions)
                    values.forEach { v ->
                        val centered = runCatching { ArticleCategories.project(v, origin) }.getOrElse { v }
                        sum.indices.forEach { sum[it] += centered[it] }
                    }
                    runCatching { id to ArticleCategories.normalize(sum) }.getOrNull()
                }
            }.toMap()
            return origin to centers
        }
        val (origin, centers) = view(false)
        val (titleOrigin, titleCenters) = view(true)
        space = NativeCategorySpace(model.signature, origin, titleOrigin, centers, titleCenters, vectors.toMap())
        if (active) training.setNativeSpace(space)
    }

    companion object {
        fun input(title: String, description: String?): String = ArticleEmbeddingInput.headline(title).let { headline ->
            val details = ArticleEmbeddingInput.details(headline, description)
            if (details.isBlank()) headline else "$headline\n\n$details"
        }
    }
}

internal data class NativeCategorySpace(
    val signature: String,
    val origin: FloatArray,
    val titleOrigin: FloatArray,
    val centers: Map<String, FloatArray>,
    val titleCenters: Map<String, FloatArray>,
    val vectors: Map<String, FloatArray>,
)
