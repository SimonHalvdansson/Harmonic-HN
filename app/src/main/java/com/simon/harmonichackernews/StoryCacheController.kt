package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.cache.StoryCacheRequest
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.cache.StoryCacheSink
import com.simon.harmonichackernews.cache.StoryCacheState
import com.simon.harmonichackernews.cache.StoryCacheStatus
import com.simon.harmonichackernews.cache.StoryCacheUseCase
import com.simon.harmonichackernews.utils.ArticleSnapshotDownloader
import com.simon.harmonichackernews.utils.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android lifecycle and persistence adapter for the shared story-cache workflow. */
internal class StoryCacheController(private val callbacks: Callbacks) {
    internal interface Callbacks {
        val context: Context?
        fun onCacheProgressChanged()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runtime: StoryCacheRuntime? = null

    private val state: StoryCacheState
        get() = runtime?.state?.value ?: StoryCacheState()

    val isCachingStories: Boolean get() = state.isCaching
    val isProgressVisible: Boolean get() = state.progressVisible
    val progress: Int get() = state.completed

    fun dispose() {
        runtime?.dispose()
        runtime = null
        scope.cancel()
    }

    val progressMax: Int
        get() = state.progressMax

    fun getProgressStatus(): String = when (state.status) {
        StoryCacheStatus.IDLE -> CACHE_PROGRESS_STATUS_CACHING
        StoryCacheStatus.CACHING -> "Caching ${state.total}" +
            if (state.total == 1) " story" else " stories"
        StoryCacheStatus.FINISHED -> CACHE_PROGRESS_STATUS_FINISHED
        StoryCacheStatus.EMPTY -> CACHE_PROGRESS_STATUS_EMPTY
        StoryCacheStatus.FAILED -> CACHE_PROGRESS_STATUS_FAILED
    }

    fun cacheStories(request: StoryCacheRequest) {
        if (isCachingStories) return
        val context = callbacks.context?.applicationContext ?: return
        getOrCreateRuntime(context).start(request)
    }

    private fun getOrCreateRuntime(context: Context): StoryCacheRuntime {
        runtime?.let { return it }
        val network = AndroidAppComposition.get(context).network
        val useCase = StoryCacheUseCase(
            hackerNewsRepository = network.hackerNewsRepository,
            algoliaRepository = network.algoliaRepository,
            sink = AndroidStoryCacheSink(context),
        )
        val created = StoryCacheRuntime(scope, useCase::execute)
        runtime = created
        scope.launch {
            created.state.collect { callbacks.onCacheProgressChanged() }
        }
        return created
    }

    private class AndroidStoryCacheSink(context: Context) : StoryCacheSink {
        private val appContext = context.applicationContext
        private val articleDownloader = ArticleSnapshotDownloader(appContext)

        override suspend fun cacheStory(id: Int, payload: String) {
            withContext(Dispatchers.IO) { Utils.cacheStory(appContext, id, payload) }
        }

        override suspend fun cacheArticle(id: Int, url: String): Boolean =
            articleDownloader.download(id, url)
    }

    private companion object {
        const val CACHE_PROGRESS_STATUS_CACHING = "Caching stories"
        const val CACHE_PROGRESS_STATUS_FINISHED = "Finished"
        const val CACHE_PROGRESS_STATUS_FAILED = "Caching failed"
        const val CACHE_PROGRESS_STATUS_EMPTY = "No stories to cache"
    }
}
