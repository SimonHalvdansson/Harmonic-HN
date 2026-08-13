package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.cache.StoryCacheRequest
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.cache.StoryCacheState
import com.simon.harmonichackernews.cache.StoryCacheStatus
import com.simon.harmonichackernews.platform.PresentationCopy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
        StoryCacheStatus.IDLE -> PresentationCopy.CACHE_STORIES
        StoryCacheStatus.CACHING -> PresentationCopy.cachingStories(state.total)
        StoryCacheStatus.FINISHED -> PresentationCopy.CACHE_FINISHED
        StoryCacheStatus.EMPTY -> PresentationCopy.CACHE_EMPTY
        StoryCacheStatus.FAILED -> PresentationCopy.CACHE_FAILED
    }

    fun cacheStories(request: StoryCacheRequest) {
        if (isCachingStories) return
        val context = callbacks.context?.applicationContext ?: return
        getOrCreateRuntime(context).start(request)
    }

    private fun getOrCreateRuntime(context: Context): StoryCacheRuntime {
        runtime?.let { return it }
        val created = context.harmonicAppComposition.createStoryCacheRuntime(scope)
        runtime = created
        scope.launch {
            created.state.collect { callbacks.onCacheProgressChanged() }
        }
        return created
    }
}
