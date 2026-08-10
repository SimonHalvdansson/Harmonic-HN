package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.cache.StoryCacheOutcome
import com.simon.harmonichackernews.cache.StoryCacheRequest
import com.simon.harmonichackernews.cache.StoryCacheSink
import com.simon.harmonichackernews.cache.StoryCacheUseCase
import com.simon.harmonichackernews.network.HttpCall
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.utils.ArticleSnapshotDownloader
import com.simon.harmonichackernews.utils.Utils
import kotlin.coroutines.resume
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Android lifecycle and persistence adapter for the shared story-cache workflow. */
internal class StoryCacheController(private val callbacks: Callbacks) {
    internal interface Callbacks {
        val context: Context?
        val userSettings: UserSettings
        fun onCacheProgressChanged()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cacheJob: Job? = null
    private var visibilityJob: Job? = null
    var isCachingStories: Boolean = false
        private set
    var isProgressVisible: Boolean = false
        private set
    private var cacheStoriesTotal = 1
    var progress: Int = 0
        private set
    private var progressStatus: String = CACHE_PROGRESS_STATUS_CACHING

    fun dispose() {
        scope.cancel()
        cacheJob = null
        visibilityJob = null
        isCachingStories = false
        isProgressVisible = false
        resetProgressState()
    }

    val progressMax: Int
        get() = max(cacheStoriesTotal, 1)

    fun getProgressStatus(): String = if (isCachingStories) cachingStatus else progressStatus

    fun cacheStories() {
        if (isCachingStories) return
        val context = callbacks.context?.applicationContext ?: return
        val preferences = callbacks.userSettings.cache
        startProgress(preferences.storiesToCache)

        val useCase = StoryCacheUseCase(
            hackerNewsRepository = NetworkComponent.hackerNewsRepository,
            algoliaRepository = NetworkComponent.algoliaRepository,
            sink = AndroidStoryCacheSink(context),
        )
        cacheJob = scope.launch {
            val outcome = useCase.execute(
                StoryCacheRequest(
                    storyCount = preferences.storiesToCache,
                    cacheArticleSnapshots = preferences.cacheArticleSnapshots,
                ),
            ) { cacheProgress ->
                cacheStoriesTotal = max(cacheProgress.total, 1)
                progress = cacheProgress.completed.coerceAtMost(cacheStoriesTotal)
                callbacks.onCacheProgressChanged()
            }
            finishProgress(
                when (outcome) {
                    StoryCacheOutcome.FINISHED -> CACHE_PROGRESS_STATUS_FINISHED
                    StoryCacheOutcome.EMPTY -> CACHE_PROGRESS_STATUS_EMPTY
                    StoryCacheOutcome.FAILED -> CACHE_PROGRESS_STATUS_FAILED
                },
            )
        }
    }

    private fun startProgress(total: Int) {
        visibilityJob?.cancel()
        isCachingStories = true
        isProgressVisible = true
        cacheStoriesTotal = max(total, 1)
        progress = 0
        progressStatus = CACHE_PROGRESS_STATUS_CACHING
        callbacks.onCacheProgressChanged()
    }

    private fun finishProgress(status: String) {
        isCachingStories = false
        isProgressVisible = true
        progressStatus = status
        callbacks.onCacheProgressChanged()
        visibilityJob = scope.launch {
            delay(CACHE_PROGRESS_FINISHED_HOLD_MS)
            isProgressVisible = false
            callbacks.onCacheProgressChanged()
        }
    }

    private fun resetProgressState() {
        cacheStoriesTotal = 1
        progress = 0
        progressStatus = CACHE_PROGRESS_STATUS_CACHING
    }

    private val cachingStatus: String
        get() = "Caching $cacheStoriesTotal" +
            if (cacheStoriesTotal == 1) " story" else " stories"

    private class AndroidStoryCacheSink(context: Context) : StoryCacheSink {
        private val appContext = context.applicationContext
        private val articleDownloader = ArticleSnapshotDownloader(appContext)

        override suspend fun cacheStory(id: Int, payload: String) {
            withContext(Dispatchers.IO) { Utils.cacheStory(appContext, id, payload) }
        }

        override suspend fun cacheArticle(id: Int, url: String): Boolean =
            suspendCancellableCoroutine { continuation ->
                val call: HttpCall? = articleDownloader.download(id, url) { _, success ->
                    if (continuation.isActive) continuation.resume(success)
                }
                if (call == null) {
                    continuation.resume(false)
                } else {
                    continuation.invokeOnCancellation { call.cancel() }
                }
            }
    }

    private companion object {
        const val CACHE_PROGRESS_FINISHED_HOLD_MS = 1_000L
        const val CACHE_PROGRESS_STATUS_CACHING = "Caching stories"
        const val CACHE_PROGRESS_STATUS_FINISHED = "Finished"
        const val CACHE_PROGRESS_STATUS_FAILED = "Caching failed"
        const val CACHE_PROGRESS_STATUS_EMPTY = "No stories to cache"
    }
}
