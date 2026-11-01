package com.simon.harmonichackernews.categorization

import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class ArticleCategory(val id: String, val name: String, val description: String)
data class ArticleCategoryScore(val categoryId: String, val similarity: Float)

object ArticleCategories {
    const val DIMENSIONS = 768
    const val DEFAULT_CUTOFF = 0.24f
    const val MIN_CUTOFF = 0.10f
    const val MAX_CUTOFF = 0.45f
    const val SECONDARY_RATIO = 0.75f
    const val REPRESENTATION_VERSION = "centered-input-views-v1"
    val defaults = listOf(
        ArticleCategory("ai", "AI", "Artificial intelligence and machine learning"),
        ArticleCategory("software", "Programming", "Languages, developer tools and software engineering"),
        ArticleCategory("security", "Security", "Vulnerabilities, malware and cryptography"),
        ArticleCategory("privacy", "Privacy", "Personal data, tracking and digital rights"),
        ArticleCategory("science", "Science", "Scientific discoveries, physics, biology and research"),
        ArticleCategory("space", "Space", "Astronomy, rockets and space exploration"),
        ArticleCategory("hardware", "Hardware", "Chips, devices, electronics and computer architecture"),
        ArticleCategory("business", "Business", "Companies, acquisitions and corporate strategy"),
        ArticleCategory("startups", "Startups", "Founders, venture capital and building a company"),
        ArticleCategory("economy", "Economy", "Employment, inflation, trade and economic policy"),
        ArticleCategory("finance", "Finance", "Banking, payments, investing and personal finance"),
        ArticleCategory("world", "World news", "International affairs, public policy and society"),
        ArticleCategory("climate", "Climate", "Climate science, emissions and the energy transition"),
        ArticleCategory("cars", "Cars", "Vehicles, transport and the automotive industry"),
        ArticleCategory("history", "History", "Past civilizations, archaeology and historical events"),
        ArticleCategory("culture", "Culture", "Art, music, literature, languages and philosophy"),
        ArticleCategory("energy", "Energy", "Electricity generation, power grids, fuels and energy storage"),
        ArticleCategory("health", "Health", "Medicine, treatments, public health and wellbeing"),
    )

    /** No AI summaries as input. The corpus generator compiles this same formatter. */
    fun input(title: String, description: String?): String = ArticleEmbeddingInput.format(title, description)

    fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all(Float::isFinite)) { "Invalid embedding" }
        val length = sqrt(vector.sumOf { it.toDouble() * it })
        require(length > 0.0) { "Empty embedding" }
        return FloatArray(vector.size) { (vector[it] / length).toFloat() }
    }

    /** Remove the corpus's common direction before comparing topic-specific information. */
    fun project(embedding: FloatArray, origin: FloatArray = ArticleCategoryPrototypes.origin): FloatArray {
        val unit = normalize(embedding)
        require(origin.size == unit.size && origin.all(Float::isFinite))
        return normalize(FloatArray(unit.size) { unit[it] - origin[it] })
    }

    fun scores(embedding: FloatArray, prototypes: Map<String, FloatArray>): List<ArticleCategoryScore> {
        val unit = normalize(embedding)
        return prototypes.map { (id, prototype) ->
            require(prototype.size == unit.size) { "Embedding dimensions do not match prototypes" }
            val center = normalize(prototype)
            ArticleCategoryScore(id, unit.indices.sumOf { unit[it].toDouble() * center[it] }.toFloat())
        }.sortedByDescending { it.similarity }
    }

    private fun labelCandidates(scores: List<ArticleCategoryScore>, settings: ArticleCategoryPreferences): List<ArticleCategoryScore> {
        val known = scores.filter { score -> score.similarity.isFinite() &&
            settings.categories.any { category -> category.id == score.categoryId } }
        // Deselecting the best topic must not promote its weakly related neighbors.
        val strongest = known.maxOfOrNull { it.similarity } ?: return emptyList()
        return known.sortedByDescending { it.similarity }.filter {
            it.similarity >= strongest * SECONDARY_RATIO && it.categoryId in settings.selected
        }
    }

    fun labels(scores: List<ArticleCategoryScore>, settings: ArticleCategoryPreferences): List<String> {
        if (!settings.enabled) return emptyList()
        return labelCandidates(scores, settings).filter { it.similarity >= settings.cutoff }
            .take(settings.maxLabels.coerceIn(1, 3)).mapNotNull { score ->
            settings.categories.firstOrNull { it.id == score.categoryId }?.let { settings.name(it) }
        }
    }
}
