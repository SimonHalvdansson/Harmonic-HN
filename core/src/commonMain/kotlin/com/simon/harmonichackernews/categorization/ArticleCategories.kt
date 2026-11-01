package com.simon.harmonichackernews.categorization

import kotlin.math.sqrt
import kotlinx.serialization.Serializable

@Serializable
data class ArticleCategory(val id: String, val name: String, val description: String)
data class ArticleCategoryScore(val categoryId: String, val similarity: Float)

object ArticleCategories {
    const val DIMENSIONS = 768
    const val DEFAULT_CUTOFF = 0.78f
    const val MIN_CUTOFF = 0.75f
    const val MAX_CUTOFF = 0.90f
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
    )

    /** Keep identical to tools/article_categories/generate_prototypes.py. No AI summaries as input. */
    fun input(title: String, description: String?): String {
        fun clean(value: String) = value.replace(Regex("\\s+"), " ").trim()
        val headline = clean(title).take(300)
        val details = clean(description.orEmpty()).take(900)
        return "title: $headline | text: ${details.ifBlank { headline }}"
    }

    fun normalize(vector: FloatArray): FloatArray {
        require(vector.isNotEmpty() && vector.all(Float::isFinite)) { "Invalid embedding" }
        val length = sqrt(vector.sumOf { it.toDouble() * it })
        require(length > 0.0) { "Empty embedding" }
        return FloatArray(vector.size) { (vector[it] / length).toFloat() }
    }

    fun scores(embedding: FloatArray, prototypes: Map<String, FloatArray>): List<ArticleCategoryScore> {
        val unit = normalize(embedding)
        return prototypes.map { (id, prototype) ->
            require(prototype.size == unit.size) { "Embedding dimensions do not match prototypes" }
            val center = normalize(prototype)
            ArticleCategoryScore(id, unit.indices.sumOf { unit[it].toDouble() * center[it] }.toFloat())
        }.sortedByDescending { it.similarity }
    }

    fun labels(scores: List<ArticleCategoryScore>, settings: ArticleCategoryPreferences): List<String> =
        if (!settings.enabled) emptyList() else scores.sortedByDescending { it.similarity }.filter {
            it.similarity.isFinite() && it.similarity >= settings.cutoff && it.categoryId in settings.selected &&
                settings.categories.any { category -> category.id == it.categoryId }
        }.take(settings.maxLabels.coerceIn(1, 3)).mapNotNull { score ->
            settings.categories.firstOrNull { it.id == score.categoryId }?.let { settings.name(it) }
        }
}
