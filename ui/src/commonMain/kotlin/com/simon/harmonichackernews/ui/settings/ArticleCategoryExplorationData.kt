package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.resources.BundledHarmonicResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class CategoryMapPoint(val id: Int, val title: String, val category: String, val matches: List<CategoryMapMatch>)

@Serializable
internal data class CategoryMapMatch(val category: String, val similarity: Float)

@Serializable
internal data class CategoryProjection(val id: String, val positions: List<List<Float>>)

@Serializable
internal data class CategoryExplorationData(
    val version: Int,
    val points: List<CategoryMapPoint>,
    val methods: List<CategoryProjection>,
    val density: List<Float>,
) {
    fun validate(): CategoryExplorationData = apply {
        require(version == 2 && points.isNotEmpty() && points.map { it.id }.distinct().size == points.size)
        require(points.all { point -> ArticleCategories.defaults.any { it.id == point.category } })
        require(points.all { point -> point.matches.size == 3 && point.matches.map { it.category }.distinct().size == 3 &&
            point.matches.all { match -> match.similarity.isFinite() && match.similarity in -1f..1f &&
                ArticleCategories.defaults.any { it.id == match.category } } &&
            point.matches.zipWithNext().all { (a, b) -> a.similarity >= b.similarity } })
        require(methods.map { it.id }.toSet() == setOf("umap", "tsne", "pca"))
        require(methods.all { method -> method.positions.size == points.size && method.positions.all { xy ->
            xy.size == 2 && xy.all { it.isFinite() && it in 0f..1f }
        } })
        require(density.size >= 2 && density.all { it.isFinite() && it in 0f..1f })
    }
}

/** Small precomputed asset, decoded once off the main thread. No model is needed to explore it. */
internal object CategoryExplorationResources {
    private val lock = Mutex()
    private val cached = MutableStateFlow<CategoryExplorationData?>(null)
    fun cached(): CategoryExplorationData? = cached.value
    suspend fun load(): CategoryExplorationData = withContext(Dispatchers.Default) {
        lock.withLock {
            cached.value ?: Json { ignoreUnknownKeys = true }.decodeFromString<CategoryExplorationData>(
                BundledHarmonicResources.articleCategoryExploration().decodeToString(),
            ).validate().also { cached.value = it }
        }
    }
}
