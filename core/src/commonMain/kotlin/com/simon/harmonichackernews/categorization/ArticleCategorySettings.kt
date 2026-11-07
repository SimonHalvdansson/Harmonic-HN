package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.emptyFlow

data class ArticleCategoryPreferences(
    val enabled: Boolean = false,
    val cutoff: Float = ArticleCategories.DEFAULT_CUTOFF,
    val selected: Set<String> = ArticleCategories.defaults.map { it.id }.toSet(),
    val names: Map<String, String> = emptyMap(),
    val categories: List<ArticleCategory> = ArticleCategories.defaults,
    val maxLabels: Int = 3,
    val maxInputTokens: Int = ArticleEmbeddingInput.DEFAULT_MAX_INPUT_TOKENS,
) {
    fun name(category: ArticleCategory): String = names[category.id] ?: category.name
}

class ArticleCategorySettings(
    private val store: KeyValueStore,
    changes: Flow<Unit>,
    private val catalog: () -> List<ArticleCategory> = { ArticleCategories.defaults },
    catalogChanges: Flow<Unit> = emptyFlow(),
) {
    private val revision = MutableStateFlow(0)
    val updates = merge(changes, catalogChanges, revision.map { Unit }).onStart { emit(Unit) }
        .map { snapshot() }.distinctUntilChanged()

    fun snapshot(): ArticleCategoryPreferences {
        val categories = catalog()
        val valid = categories.map { it.id }.toSet()
        return ArticleCategoryPreferences(
            enabled = store.getBoolean(ENABLED, false),
            cutoff = sanitizeCutoff(store.getFloat(CUTOFF, ArticleCategories.DEFAULT_CUTOFF)),
            selected = if (store.contains(SELECTED)) store.getStringSet(SELECTED).intersect(valid) else valid,
            names = categories.mapNotNull { category ->
                store.getString(NAME_PREFIX + category.id)?.trim()?.take(40)?.takeIf(String::isNotBlank)
                    ?.let { category.id to it }
            }.toMap(),
            categories = categories,
            maxLabels = store.getInt(MAX_LABELS, 3).coerceIn(1, 3),
            maxInputTokens = store.getInt(MAX_INPUT_TOKENS, ArticleEmbeddingInput.DEFAULT_MAX_INPUT_TOKENS)
                .takeIf { it in ArticleEmbeddingInput.TOKEN_LIMITS } ?: ArticleEmbeddingInput.DEFAULT_MAX_INPUT_TOKENS,
        )
    }

    fun setEnabled(enabled: Boolean) = write { putBoolean(ENABLED, enabled) }
    fun setCutoff(cutoff: Float) = write { putFloat(CUTOFF, sanitizeCutoff(cutoff)) }
    fun setMaxLabels(max: Int) = write { putInt(MAX_LABELS, max.coerceIn(1, 3)) }
    fun setMaxInputTokens(max: Int) {
        require(max in ArticleEmbeddingInput.TOKEN_LIMITS)
        write { putInt(MAX_INPUT_TOKENS, max) }
    }
    fun setSelected(id: String, selected: Boolean) {
        require(catalog().any { it.id == id })
        val ids = snapshot().selected
        write { putStringSet(SELECTED, if (selected) ids + id else ids - id) }
    }
    fun rename(id: String, name: String) {
        require(catalog().any { it.id == id })
        write { putString(NAME_PREFIX + id, name.trim().take(40).takeIf(String::isNotBlank)) }
    }
    private fun write(block: KeyValueStore.Editor.() -> Unit) {
        store.update(block)
        revision.update { it + 1 }
    }
    private fun sanitizeCutoff(value: Float) =
        if (value.isFinite()) value.coerceIn(ArticleCategories.MIN_CUTOFF, ArticleCategories.MAX_CUTOFF) else ArticleCategories.DEFAULT_CUTOFF

    companion object {
        const val ENABLED = "pref_article_categories_enabled"
        const val CUTOFF = "pref_article_categories_cutoff"
        const val SELECTED = "pref_article_categories_selected"
        const val NAME_PREFIX = "pref_article_category_name_"
        const val MAX_LABELS = "pref_article_categories_max_labels"
        const val MAX_INPUT_TOKENS = "pref_article_categories_max_input_tokens"
    }
}
