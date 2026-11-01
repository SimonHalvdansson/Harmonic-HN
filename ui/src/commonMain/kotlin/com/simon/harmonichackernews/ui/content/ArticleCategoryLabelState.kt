package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategories
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntime
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntimeState
import com.simon.harmonichackernews.categorization.ArticleCategoryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Cached results are the initial state, including when a lazy row is composed again. */
@Composable
internal fun rememberArticleCategoryLabels(
    storyId: Int,
    title: String?,
    url: String?,
    description: String?,
    preferences: ArticleCategoryPreferences,
    runtime: ArticleCategoryRuntime?,
    runtimeState: ArticleCategoryRuntimeState,
    eligible: Boolean = true,
    requestDelayMillis: Long = 180,
    cachedDescription: (suspend () -> String?)? = null,
): List<String> {
    if (runtime == null || !preferences.enabled || preferences.selected.isEmpty() || !eligible || title.isNullOrBlank() ||
        runtimeState.status in setOf(ArticleCategoryStatus.OFF, ArticleCategoryStatus.NEEDS_MODEL, ArticleCategoryStatus.UNAVAILABLE)) {
        return emptyList()
    }
    return key(runtime, storyId, title, url) {
        val results = remember(runtime, storyId, title, url) { runtime.observeScores(storyId, title, url) }
        val scores by results.collectAsStateWithLifecycle(initialValue = runtime.cachedScores(storyId, title, url))
        val currentCachedDescription by rememberUpdatedState(cachedDescription)
        LaunchedEffect(runtimeState, description, requestDelayMillis) {
            if (runtimeState.status == ArticleCategoryStatus.READY) {
                if (requestDelayMillis > 0 && runtime.cachedScores(storyId, title, url) == null) delay(requestDelayMillis)
                val cached = try { currentCachedDescription?.invoke() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                runtime.scoresForStory(storyId, title, url, cached ?: description)
            }
        }
        remember(scores, preferences) { ArticleCategories.labels(scores.orEmpty(), preferences) }
    }
}
