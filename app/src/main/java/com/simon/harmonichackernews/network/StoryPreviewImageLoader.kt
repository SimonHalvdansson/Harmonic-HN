package com.simon.harmonichackernews.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.Job

object StoryPreviewImageLoader {
    private const val PREVIEW_IMAGE_CACHE_PREFERENCES =
        "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES"
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val CACHE = PreviewContentCache()
    fun loadPreviewImageUrl(pageUrl: String?, callback: PreviewImageCallback): PreviewImageRequest {
        return loadPreviewImageUrl(null, 0, pageUrl, callback)
    }

    fun loadPreviewImageUrl(
        context: Context?,
        storyId: Int,
        pageUrl: String?,
        callback: PreviewImageCallback
    ): PreviewImageRequest {
        return loadPreviewImageUrl(context, storyId, pageUrl, false, callback)
    }

    fun loadPreviewImageUrl(
        context: Context?,
        storyId: Int,
        pageUrl: String?,
        forceRefresh: Boolean,
        callback: PreviewImageCallback
    ): PreviewImageRequest {
        return loadPreviewContent(
            context,
            storyId,
            pageUrl,
            false,
            forceRefresh,
            PreviewContentCallback { imageUrl: String?, summary: LinkSummary? ->
                callback.onPreviewImageUrlLoaded(
                    imageUrl
                )
            })
    }

    fun loadPreviewContent(
        context: Context?,
        storyId: Int,
        pageUrl: String?,
        requireSummary: Boolean,
        callback: PreviewContentCallback
    ): PreviewImageRequest {
        return loadPreviewContent(
            context,
            storyId,
            pageUrl,
            requireSummary,
            false,
            callback
        )
    }

    private fun loadPreviewContent(
        context: Context?,
        storyId: Int,
        pageUrl: String?,
        requireSummary: Boolean,
        forceRefresh: Boolean,
        callback: PreviewContentCallback
    ): PreviewImageRequest {
        val appContext = context?.applicationContext
        val previewImageRequest = PendingPreviewImageRequest(callback)
        val normalizedPageUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (normalizedPageUrl.isNullOrEmpty()) {
            postResult(previewImageRequest, null, null)
            return previewImageRequest
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        if (!forceRefresh) {
            val cachedDiskImageUrl = loadCachedPreviewImageUrl(
                appContext,
                previewImageCacheEntryId,
                true
            )
            val cachedSummary = if (requireSummary)
                getCachedLinkSummary(appContext, normalizedPageUrl)
            else
                null
            if (cachedSummary != null) {
                val cachedImageUrl = if (cachedSummary.imageUrl.isEmpty())
                    cachedDiskImageUrl.imageUrl
                else
                    cachedSummary.imageUrl
                postResult(previewImageRequest, cachedImageUrl, cachedSummary)
                return previewImageRequest
            }
            if (!requireSummary && cachedDiskImageUrl.loaded) {
                postResult(previewImageRequest, cachedDiskImageUrl.imageUrl, null)
                return previewImageRequest
            }
        }

        if (LinkSummaryParser.isLikelyImageUrl(normalizedPageUrl)) {
            saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, normalizedPageUrl)
            postResult(previewImageRequest, normalizedPageUrl, null)
            return previewImageRequest
        }

        if (appContext == null) {
            postResult(previewImageRequest, null, null)
            return previewImageRequest
        }
        val network = AndroidAppComposition.get(appContext).network
        val job = NetworkComponent.launchCallbackRequest(
            request = {
                network.previewContentCoordinator.load(
                    pageUrl = normalizedPageUrl,
                    requireSummary = requireSummary,
                    forceRefresh = forceRefresh,
                ) {
                    network.linkSummaryRepository.load(normalizedPageUrl)
                }
            },
            onSuccess = { content ->
                if (!previewImageRequest.isCancelled) {
                    saveCachedPreviewImageUrl(
                        appContext,
                        previewImageCacheEntryId,
                        content.imageUrl,
                    )
                    content.summary?.let {
                        saveCachedLinkSummary(appContext, normalizedPageUrl, it)
                    }
                    callback.onPreviewContentLoaded(content.imageUrl, content.summary)
                }
            },
            onFailure = {
                if (!previewImageRequest.isCancelled) {
                    saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, null)
                    callback.onPreviewContentLoaded(null, null)
                }
            },
        )
        previewImageRequest.attach(job)
        return previewImageRequest
    }

    fun getCachedPreviewImageUrl(context: Context?, storyId: Int, pageUrl: String?): String? {
        val appContext = if (context == null) null else context.getApplicationContext()
        val normalizedPageUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (normalizedPageUrl.isNullOrEmpty()) {
            return null
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).imageUrl
    }

    fun isCachedPreviewImageUrlLoaded(context: Context?, storyId: Int, pageUrl: String?): Boolean {
        val appContext = if (context == null) null else context.getApplicationContext()
        val normalizedPageUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (normalizedPageUrl.isNullOrEmpty()) {
            return false
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).loaded
    }

    private fun postResult(
        request: PendingPreviewImageRequest,
        imageUrl: String?,
        summary: LinkSummary?
    ) {
        MAIN_HANDLER.post(Runnable {
            if (!request.isCancelled) {
                request.callback.onPreviewContentLoaded(imageUrl, summary)
            }
        })
    }

    fun clearDiskCache(context: Context?) {
        if (context == null) return

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            val editor = preferences.edit()
            for (key in preferences.getAll().keys) {
                if (PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY == key
                    || PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY == key
                    || PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY == key
                    || key.startsWith(PreviewCachePolicy.PREVIEW_IMAGE_URL_PREFIX)
                    || key.startsWith(PreviewCachePolicy.PREVIEW_IMAGE_LOADED_PREFIX)
                    || key.startsWith(PreviewCachePolicy.PREVIEW_TINT_PREFIX)
                    || key.startsWith(PreviewCachePolicy.LINK_SUMMARY_PREFIX)
                ) {
                    editor.remove(key)
                }
            }
            editor.apply()
            CACHE.reset()
        }
    }

    private fun loadCachedPreviewImageUrl(
        context: Context?,
        previewImageCacheEntryId: String?,
        updateCacheOrder: Boolean = true
    ): CachedPreviewImageUrl = synchronized(StoryPreviewImageLoader::class.java) {
        CACHE.loadPreviewImage(
            previewCacheStore(context),
            previewImageCacheEntryId,
            updateCacheOrder,
        )
    }

    private fun saveCachedPreviewImageUrl(
        context: Context?,
        previewImageCacheEntryId: String?,
        imageUrl: String?
    ) {
        synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.savePreviewImage(
                previewCacheStore(context),
                previewImageCacheEntryId,
                imageUrl,
            )
        }
    }

    fun saveCachedPreviewImageTintColor(
        context: Context?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int,
        tintColor: Int
    ) {
        synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.saveTintColor(
                store = previewCacheStore(context),
                storyId = storyId,
                imageUrl = imageUrl,
                baseColor = baseColor,
                tintColor = tintColor,
            )
        }
    }

    fun cachePreviewImageTintColor(
        storyId: Int,
        imageUrl: String?,
        baseColor: Int,
        tintColor: Int,
    ) {
        synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.cacheTintColor(storyId, imageUrl, baseColor, tintColor)
        }
    }

    fun loadCachedPreviewImageTintColor(
        context: Context?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int
    ): Int? = synchronized(StoryPreviewImageLoader::class.java) {
        CACHE.loadTintColor(previewCacheStore(context), storyId, imageUrl, baseColor)
    }

    fun clearCachedPreviewImageTintColors(context: Context?) {
        synchronized(StoryPreviewImageLoader::class.java) {
            if (context != null) {
                val preferences = getPreviewImageCachePreferences(context)
                val editor = preferences.edit()
                    .remove(PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
                for (key in preferences.getAll().keys) {
                    if (key.startsWith(PreviewCachePolicy.PREVIEW_TINT_PREFIX)) {
                        editor.remove(key)
                    }
                }
                editor.apply()
            }
            CACHE.clearTintMemoryAndOrderSnapshot()
        }
    }

    fun getCachedPreviewImageTintColorCount(context: Context?): Int {
        return synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.tintColorCount(previewCacheStore(context))
        }
    }

    private fun getPreviewImageCachePreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREVIEW_IMAGE_CACHE_PREFERENCES, Context.MODE_PRIVATE)
    }

    private fun previewCacheStore(context: Context?): KeyValueStore? = context?.let {
        AndroidKeyValueStore.named(it, PREVIEW_IMAGE_CACHE_PREFERENCES)
    }

    private fun getPreviewImageCacheEntryId(storyId: Int, pageUrl: String?): String? {
        return PreviewCachePolicy.previewEntryId(storyId, pageUrl)
    }

    fun getCachedLinkSummary(
        context: Context?,
        pageUrl: String?
    ): LinkSummary? {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        return synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.loadLinkSummary(previewCacheStore(context), normalizedUrl)
        }
    }

    fun saveCachedLinkSummary(
        context: Context?,
        pageUrl: String?,
        result: LinkSummary?
    ) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        synchronized(StoryPreviewImageLoader::class.java) {
            CACHE.saveLinkSummary(previewCacheStore(context), normalizedUrl, result)
        }
    }

    interface PreviewImageCallback {
        fun onPreviewImageUrlLoaded(imageUrl: String?)
    }

    fun interface PreviewContentCallback {
        fun onPreviewContentLoaded(
            imageUrl: String?,
            summary: LinkSummary?
        )
    }

    interface PreviewImageRequest {
        fun cancel()

        val isCancelled: Boolean
    }

    private class PendingPreviewImageRequest(
        val callback: PreviewContentCallback
    ) : PreviewImageRequest {
        private var cancelled = false
        private var call: Job? = null

        fun attach(job: Job) {
            synchronized(this) {
                if (cancelled) {
                    job.cancel()
                } else {
                    call = job
                }
            }
        }

        override fun cancel() {
            synchronized(this) {
                if (cancelled) return
                cancelled = true
                call?.cancel()
                call = null
            }
        }

        override val isCancelled: Boolean
            get() = synchronized(this) { cancelled }
    }

}
