package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.StoryPreviewRepository
import com.simon.harmonichackernews.platform.ObservableHistoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ArticleCategoryHistoryPage(val stories: List<ArticleTrainingStory>, val nextOffset: Int, val hasMore: Boolean, val failures: Int)

/** Reads existing history and cached preview metadata. It never fetches article bodies. */
class ArticleCategoryHistory(
    private val history: ObservableHistoryStore,
    private val stories: HackerNewsRepository,
    private val cache: StoryCacheRepository,
    private val previews: StoryPreviewRepository,
) {
    fun contains(storyId: Int): Boolean = storyId in history.historyState.value.ids

    suspend fun page(offset: Int, size: Int = 30): ArticleCategoryHistoryPage = coroutineScope {
        val entries = history.historyState.value.histories.sortedByDescending { it.created }
        val selected = entries.drop(offset).take(size)
        val permits = Semaphore(4)
        val results = selected.map { entry -> async {
            permits.withPermit {
                try {
                    val cached = withContext(Dispatchers.Default) {
                        com.simon.harmonichackernews.data.Story().apply { id = entry.id }.takeIf { cache.hydrateStory(it) }
                    }
                    val story = cached ?: stories.getStory(entry.id)
                    val title = story?.title?.takeIf(String::isNotBlank)
                    if (title == null || story.isComment || story.isJob) return@withPermit null to false
                    val description = story.url?.let { previews.cachedLinkSummary(it)?.description }
                        ?: story.linkSummaryDescription.orEmpty()
                    ArticleTrainingStory(entry.id, title, story.url.orEmpty(), description,
                        viewedAtEpochSeconds = entry.created / 1000) to false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null to true }
            }
        } }.awaitAll()
        ArticleCategoryHistoryPage(results.mapNotNull { it.first }, offset + selected.size,
            offset + selected.size < entries.size, results.count { it.second })
    }
}
