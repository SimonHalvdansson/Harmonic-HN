package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.categorization.ArticleCategoryPreferences
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntime
import com.simon.harmonichackernews.categorization.ArticleCategoryRuntimeState
import com.simon.harmonichackernews.categorization.ArticleCategoryStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Cached results are the initial state, including when a lazy row is composed again. */
@Composable
internal fun rememberArticleCategoryPresentation(
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
): ArticleCategoryLabelPresentation {
    if (runtime == null || !preferences.enabled || preferences.selected.isEmpty() || !eligible || title.isNullOrBlank() ||
        runtimeState.status in setOf(ArticleCategoryStatus.OFF, ArticleCategoryStatus.NEEDS_MODEL, ArticleCategoryStatus.UNAVAILABLE)) {
        return ArticleCategoryLabelPresentation.Empty
    }
    return key(runtime, storyId, title, url) {
        val results = remember(runtime, storyId, title, url) { runtime.observeResult(storyId, title, url) }
        val result by results.collectAsStateWithLifecycle(initialValue = runtime.cachedResult(storyId, title, url))
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
        remember(result, preferences) {
            result?.let {
                val inspection = StoryCategoryInspection(storyId, title, url, it, preferences)
                ArticleCategoryLabelPresentation(inspection.labels, inspection)
            } ?: ArticleCategoryLabelPresentation.Empty
        }
    }
}
