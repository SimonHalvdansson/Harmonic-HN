package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Serializable
data class ArticleTrainingStory(
    val id: Int,
    val title: String,
    val url: String = "",
    val description: String = "",
    val source: String = "history",
    val viewedAtEpochSeconds: Long? = null,
)

@Serializable
data class EmbeddedTrainingStory(
    val story: ArticleTrainingStory,
    val embedding: List<Float>,
    val titleEmbedding: List<Float> = embedding,
)

@Serializable
data class ArticleTrainingConfiguration(
    val customCategories: List<ArticleCategory> = emptyList(),
    val excluded: Map<String, Set<Int>> = emptyMap(),
    val added: Map<String, List<EmbeddedTrainingStory>> = emptyMap(),
)

@Serializable
internal data class ArticleTrainingSeed(
    val stories: List<ArticleTrainingStory>,
    val assignments: Map<String, List<Int>>,
    val modelSha256: String,
    val inputVersion: String,
    val representationVersion: String,
)

/** User examples are local content. Removing model weights never deletes this training data. */
class ArticleCategoryTraining(
    private val store: KeyValueStore,
    private val embeddingOrigin: (Boolean) -> FloatArray = { titleOnly ->
        if (titleOnly) ArticleCategoryPrototypes.titleOrigin else ArticleCategoryPrototypes.origin
    },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(
        runCatching { store.getString(STORAGE_KEY)?.let { json.decodeFromString<ArticleTrainingConfiguration>(it) } }
            .getOrNull() ?: ArticleTrainingConfiguration(),
    )
    val state = mutableState.asStateFlow()
    val categories: List<ArticleCategory> get() = ArticleCategories.defaults + state.value.customCategories
    private val loader = CompletableDeferred<suspend () -> ByteArray>()
    private val defaultsLock = Mutex()
    private var seed: ArticleTrainingSeed? = null
    private var defaultVectors: Map<Int, FloatArray> = emptyMap()
    private var defaultTitleVectors: Map<Int, FloatArray> = emptyMap()
    private var cachedCenters: Pair<ArticleTrainingConfiguration, Map<String, FloatArray>>? = null
    private var cachedTitleCenters: Pair<ArticleTrainingConfiguration, Map<String, FloatArray>>? = null

    /** Registers a resource provider without reading the file at app startup. */
    fun setDefaultLoader(load: suspend () -> ByteArray) { loader.complete(load) }

    suspend fun loadDefaults() = defaultsLock.withLock {
        if (seed != null) return@withLock
        val bytes = loader.await().invoke()
        withContext(Dispatchers.Default) {
            require(bytes.size >= 8 && bytes.copyOfRange(0, 4).decodeToString() == "HNC2") { "Invalid category training data" }
            var offset = 4
            fun readInt(): Int {
                require(offset + 4 <= bytes.size)
                var result = 0
                repeat(4) { result = result or ((bytes[offset++].toInt() and 255) shl (it * 8)) }
                return result
            }
            val headerSize = readInt()
            require(headerSize in 1..bytes.size - 8)
            val decoded = json.decodeFromString<ArticleTrainingSeed>(bytes.copyOfRange(offset, offset + headerSize).decodeToString())
            offset += headerSize
            require(decoded.modelSha256 == MODEL_SHA256 && decoded.stories.size <= 10_000)
            require(decoded.inputVersion == ArticleEmbeddingInput.VERSION &&
                decoded.representationVersion == ArticleCategories.REPRESENTATION_VERSION) { "Incompatible category embedding data" }
            require(offset.toLong() + decoded.stories.size.toLong() * ArticleCategories.DIMENSIONS * 8 == bytes.size.toLong())
            val titles = mutableMapOf<Int, FloatArray>()
            val vectors = decoded.stories.associate { story ->
                val preview = ArticleCategories.normalize(FloatArray(ArticleCategories.DIMENSIONS) { Float.fromBits(readInt()) })
                titles[story.id] = ArticleCategories.normalize(FloatArray(ArticleCategories.DIMENSIONS) { Float.fromBits(readInt()) })
                story.id to preview
            }
            defaultVectors = vectors
            defaultTitleVectors = titles
            seed = decoded
        }
    }

    suspend fun examples(categoryId: String): List<ArticleTrainingStory> {
        loadDefaults()
        return examples(categoryId, state.value)
    }

    private fun examples(categoryId: String, config: ArticleTrainingConfiguration): List<ArticleTrainingStory> {
        val ids = seed?.assignments?.get(categoryId).orEmpty().toSet() - config.excluded[categoryId].orEmpty()
        return (seed?.stories.orEmpty().filter { it.id in ids } + config.added[categoryId].orEmpty().map { it.story })
            .distinctBy { it.id }
    }

    fun create(name: String): String {
        require(name.trim().isNotEmpty() && name.trim().length <= 40)
        require(state.value.customCategories.size < 50) { "You can create up to 50 categories" }
        var id: String
        do { id = "custom_" + Random.nextLong().toULong().toString(16) } while (categories.any { it.id == id })
        update(state.value.copy(customCategories = state.value.customCategories + ArticleCategory(id, name.trim(), "Your training stories")))
        return id
    }

    fun delete(categoryId: String) {
        require(state.value.customCategories.any { it.id == categoryId })
        update(state.value.copy(
            customCategories = state.value.customCategories.filterNot { it.id == categoryId },
            excluded = state.value.excluded - categoryId, added = state.value.added - categoryId,
        ))
    }

    suspend fun removeExample(categoryId: String, storyId: Int) {
        loadDefaults()
        require(categories.any { it.id == categoryId })
        update(state.value.copy(
            excluded = state.value.excluded + (categoryId to (state.value.excluded[categoryId].orEmpty() + storyId)),
            added = state.value.added + (categoryId to state.value.added[categoryId].orEmpty().filterNot { it.story.id == storyId }),
        ))
    }

    fun resetExamples(categoryId: String) {
        require(categories.any { it.id == categoryId })
        update(state.value.copy(excluded = state.value.excluded - categoryId, added = state.value.added - categoryId))
    }

    suspend fun addExamples(categoryId: String, examples: List<EmbeddedTrainingStory>) {
        loadDefaults()
        require(categories.any { it.id == categoryId })
        require(examples.all { it.story.id > 0 && it.story.title.isNotBlank() &&
            it.embedding.size == ArticleCategories.DIMENSIONS && it.titleEmbedding.size == ArticleCategories.DIMENSIONS })
        val normalized = examples.map { it.copy(embedding = ArticleCategories.normalize(it.embedding.toFloatArray()).toList(),
            titleEmbedding = ArticleCategories.normalize(it.titleEmbedding.toFloatArray()).toList()) }
        val merged = (state.value.added[categoryId].orEmpty() + normalized).distinctBy { it.story.id }
        require(merged.size <= MAX_USER_EXAMPLES) { "Use up to $MAX_USER_EXAMPLES history stories per category" }
        update(state.value.copy(
            excluded = state.value.excluded + (categoryId to (state.value.excluded[categoryId].orEmpty() - examples.map { it.story.id }.toSet())),
            added = state.value.added + (categoryId to merged),
        ))
    }

    suspend fun centers(titleOnly: Boolean = false): Map<String, FloatArray> {
        val config = state.value
        return centers(config, titleOnly)
    }

    fun project(embedding: FloatArray, titleOnly: Boolean = false): FloatArray = ArticleCategories.project(embedding, embeddingOrigin(titleOnly))

    private suspend fun centers(config: ArticleTrainingConfiguration, titleOnly: Boolean): Map<String, FloatArray> {
        (if (titleOnly) cachedTitleCenters else cachedCenters)?.takeIf { it.first == config }?.let { return it.second }
        if (config.excluded.values.all { it.isEmpty() } && config.added.values.all { it.isEmpty() }) {
            return if (titleOnly) ArticleCategoryPrototypes.titleVectors else ArticleCategoryPrototypes.vectors
        }
        loadDefaults()
        return withContext(Dispatchers.Default) {
            (ArticleCategories.defaults + config.customCategories).mapNotNull { category ->
                val additions = config.added[category.id].orEmpty().associate {
                    it.story.id to (if (titleOnly) it.titleEmbedding else it.embedding).toFloatArray()
                }
                val allExamples = examples(category.id, config)
                val contextual = if (titleOnly) allExamples else allExamples.filter { hasPreview(it) }
                // A custom category with only headline examples remains usable until richer ones are added.
                val references = contextual.ifEmpty { allExamples }
                val defaults = if (titleOnly) defaultTitleVectors else defaultVectors
                val vectors = references.mapNotNull { additions[it.id] ?: defaults[it.id] }.map { project(it, titleOnly) }
                if (vectors.isEmpty()) null else {
                    val sum = FloatArray(ArticleCategories.DIMENSIONS)
                    vectors.forEach { vector -> sum.indices.forEach { sum[it] += vector[it] } }
                    category.id to ArticleCategories.normalize(sum)
                }
            }.toMap().also {
                if (titleOnly) cachedTitleCenters = config to it else cachedCenters = config to it
            }
        }
    }

    /** Uses stored vectors, so example scores also work with the model removed. */
    suspend fun exampleSimilarities(categoryId: String): Map<Int, Float> {
        loadDefaults()
        return withContext(Dispatchers.Default) {
            val config = state.value
            val previewCenter = centers(config, false)[categoryId]
            val titleCenter = centers(config, true)[categoryId]
            val additions = config.added[categoryId].orEmpty().associateBy { it.story.id }
            examples(categoryId, config).mapNotNull { story ->
                val titleOnly = !hasPreview(story)
                val center = (if (titleOnly) titleCenter else previewCenter) ?: return@mapNotNull null
                val added = additions[story.id]
                val vector = if (titleOnly) added?.titleEmbedding?.toFloatArray() ?: defaultTitleVectors[story.id]
                    else added?.embedding?.toFloatArray() ?: defaultVectors[story.id]
                vector ?: return@mapNotNull null
                story.id to ArticleCategories.scores(project(vector, titleOnly), mapOf(categoryId to center)).single().similarity
            }.toMap()
        }
    }

    private fun update(config: ArticleTrainingConfiguration) {
        store.putString(STORAGE_KEY, json.encodeToString(config))
        mutableState.value = config
        cachedCenters = null
        cachedTitleCenters = null
    }

    private fun hasPreview(story: ArticleTrainingStory): Boolean =
        ArticleCategories.input(story.title, story.description) != ArticleCategories.input(story.title, null)

    companion object {
        const val STORAGE_KEY = "article_category_training_v1"
        const val MAX_USER_EXAMPLES = 200
        const val MODEL_SHA256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb"
    }
}
