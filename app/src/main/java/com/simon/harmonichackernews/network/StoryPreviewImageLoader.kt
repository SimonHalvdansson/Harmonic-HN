package com.simon.harmonichackernews.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.Job

object StoryPreviewImageLoader {
    private const val MAX_CACHE_SIZE = 300
    private const val LEGACY_TINT_CACHE_KEYS_REMOVED_PER_SAVE = 8
    private const val PREVIEW_IMAGE_CACHE_PREFERENCES =
        "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES"
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val LINK_SUMMARY_CACHE: MutableMap<String?, LinkSummary?> = HashMap()
    private val CACHE_ORDERS: MutableMap<String, List<String>> = HashMap()
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
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
                val cachedImageUrl = if (TextUtils.isEmpty(cachedSummary.imageUrl))
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

        val job = NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.previewContentCoordinator.load(
                    pageUrl = normalizedPageUrl,
                    requireSummary = requireSummary,
                    forceRefresh = forceRefresh,
                ) {
                    NetworkComponent.linkSummaryRepository.load(normalizedPageUrl)
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
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            return null
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).imageUrl
    }

    fun isCachedPreviewImageUrlLoaded(context: Context?, storyId: Int, pageUrl: String?): Boolean {
        val appContext = if (context == null) null else context.getApplicationContext()
        val normalizedPageUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (TextUtils.isEmpty(normalizedPageUrl)) {
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
        if (context == null) {
            return
        }

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
            CACHE_ORDERS.clear()
            editor.apply()
        }
    }

    private fun loadCachedPreviewImageUrl(
        context: Context?,
        previewImageCacheEntryId: String?,
        updateCacheOrder: Boolean = true
    ): CachedPreviewImageUrl {
        if (context == null || TextUtils.isEmpty(previewImageCacheEntryId)) {
            return CachedPreviewImageUrl(false, null)
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            val imageUrl = preferences.getString(
                StoryPreviewImageLoader.getPreviewImageUrlKey(previewImageCacheEntryId!!), null
            )
            val loaded = preferences.getBoolean(
                StoryPreviewImageLoader.getPreviewImageUrlLoadedKey(previewImageCacheEntryId),
                false
            )
                    || !TextUtils.isEmpty(imageUrl)
            if (updateCacheOrder && loaded) {
                movePreviewImageCacheIdToEnd(preferences, previewImageCacheEntryId)
            }
            return CachedPreviewImageUrl(loaded, imageUrl)
        }
    }

    private fun saveCachedPreviewImageUrl(
        context: Context?,
        previewImageCacheEntryId: String?,
        imageUrl: String?
    ) {
        if (context == null || TextUtils.isEmpty(previewImageCacheEntryId)) {
            return
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            val orderUpdate = PreviewCachePolicy.touch(
                readPreviewImageCacheOrder(preferences),
                previewImageCacheEntryId!!,
            )

            val editor = preferences.edit()
                .putBoolean(
                    StoryPreviewImageLoader.getPreviewImageUrlLoadedKey(
                        previewImageCacheEntryId
                    ), true
                )
            if (TextUtils.isEmpty(imageUrl)) {
                editor.remove(StoryPreviewImageLoader.getPreviewImageUrlKey(previewImageCacheEntryId))
            } else {
                editor.putString(
                    StoryPreviewImageLoader.getPreviewImageUrlKey(
                        previewImageCacheEntryId
                    ), imageUrl
                )
            }

            for (oldestId in orderUpdate.evicted) {
                editor.remove(getPreviewImageUrlKey(oldestId))
                editor.remove(getPreviewImageUrlLoadedKey(oldestId))
            }
            putCacheOrder(
                editor,
                PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
                orderUpdate.order,
            ).apply()
        }
    }

    fun saveCachedPreviewImageTintColor(
        context: Context?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int,
        tintColor: Int
    ) {
        if (context == null || storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            val tintColorKey =
                StoryPreviewImageLoader.getPreviewImageTintColorKey(storyId, imageUrl!!, baseColor)
            val orderedKeys = readPreviewImageTintCacheOrder(preferences)
            val editor = preferences.edit()
            var legacyKeysRemoved = 0
            val iterator: MutableIterator<String?> = orderedKeys.iterator()
            while (iterator.hasNext()) {
                if (legacyKeysRemoved >= LEGACY_TINT_CACHE_KEYS_REMOVED_PER_SAVE) {
                    break
                }
                val orderedKey = iterator.next()
                if (!PreviewCachePolicy.isCurrentTintKey(orderedKey)) {
                    iterator.remove()
                    editor.remove(orderedKey)
                    legacyKeysRemoved++
                }
            }
            val orderUpdate = PreviewCachePolicy.touch(orderedKeys.filterNotNull(), tintColorKey)

            editor.putInt(tintColorKey, tintColor)
            for (evictedKey in orderUpdate.evicted) {
                editor.remove(evictedKey)
            }
            putCacheOrder(
                editor,
                PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY,
                orderUpdate.order,
            ).apply()
        }
    }

    fun loadCachedPreviewImageTintColor(
        context: Context?,
        storyId: Int,
        imageUrl: String?,
        baseColor: Int
    ): Int? {
        if (context == null || storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null
        }

        StoryPreviewImageMemoryCache.getTintColor(storyId, imageUrl, baseColor)?.let {
            return it
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            val key =
                StoryPreviewImageLoader.getPreviewImageTintColorKey(storyId, imageUrl!!, baseColor)
            if (!preferences.contains(key)) {
                return null
            }

            val tintColor = preferences.getInt(key, baseColor)
            StoryPreviewImageMemoryCache.putTintColor(storyId, imageUrl, baseColor, tintColor)
            movePreviewImageTintCacheKeyToEnd(preferences, key)
            return tintColor
        }
    }

    private fun movePreviewImageCacheIdToEnd(
        preferences: SharedPreferences,
        previewImageCacheEntryId: String?
    ) {
        val orderUpdate = PreviewCachePolicy.touch(
            readPreviewImageCacheOrder(preferences),
            previewImageCacheEntryId!!,
        )
        putCacheOrder(
            preferences.edit(),
            PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY,
            orderUpdate.order,
        ).apply()
    }

    private fun readPreviewImageCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, PreviewCachePolicy.PREVIEW_IMAGE_ORDER_KEY)
    }

    private fun readPreviewImageTintCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
    }

    private fun readLinkSummaryCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY)
    }

    private fun readCacheOrder(
        preferences: SharedPreferences,
        orderKey: String,
    ): MutableList<String> {
        CACHE_ORDERS[orderKey]?.let { return ArrayList(it) }

        val orderedIds = PreviewCachePolicy.decodeOrder(preferences.getString(orderKey, ""))
        CACHE_ORDERS[orderKey] = ArrayList(orderedIds)
        return orderedIds
    }

    private fun putCacheOrder(
        editor: SharedPreferences.Editor,
        orderKey: String,
        order: List<String>,
    ): SharedPreferences.Editor {
        CACHE_ORDERS[orderKey] = ArrayList(order)
        return editor.putString(orderKey, PreviewCachePolicy.encodeOrder(order))
    }

    private fun movePreviewImageTintCacheKeyToEnd(
        preferences: SharedPreferences,
        tintColorKey: String?
    ) {
        val orderUpdate = PreviewCachePolicy.touch(
            readPreviewImageTintCacheOrder(preferences),
            tintColorKey!!,
        )
        putCacheOrder(
            preferences.edit(),
            PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY,
            orderUpdate.order,
        ).apply()
    }

    fun clearCachedPreviewImageTintColors(context: Context?) {
        if (context == null) {
            return
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            if (!preferences.contains(PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)) {
                return
            }

            val editor = preferences.edit()
                .remove(PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
            CACHE_ORDERS.remove(PreviewCachePolicy.PREVIEW_TINT_ORDER_KEY)
            for (key in preferences.getAll().keys) {
                if (key.startsWith(PreviewCachePolicy.PREVIEW_TINT_PREFIX)) {
                    editor.remove(key)
                }
            }
            editor.apply()
        }
    }

    fun getCachedPreviewImageTintColorCount(context: Context?): Int {
        if (context == null) {
            return 0
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            return readPreviewImageTintCacheOrder(
                getPreviewImageCachePreferences(context)
            ).size
        }
    }

    private fun getPreviewImageCachePreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREVIEW_IMAGE_CACHE_PREFERENCES, Context.MODE_PRIVATE)
    }

    private fun getPreviewImageCacheEntryId(storyId: Int, pageUrl: String?): String? {
        return PreviewCachePolicy.previewEntryId(storyId, pageUrl)
    }

    private fun getPreviewImageUrlKey(previewImageCacheEntryId: String): String {
        return PreviewCachePolicy.PREVIEW_IMAGE_URL_PREFIX + previewImageCacheEntryId
    }

    private fun getPreviewImageUrlLoadedKey(previewImageCacheEntryId: String): String {
        return PreviewCachePolicy.PREVIEW_IMAGE_LOADED_PREFIX + previewImageCacheEntryId
    }

    private fun getPreviewImageTintColorKey(
        storyId: Int,
        imageUrl: String,
        baseColor: Int
    ): String {
        return (PreviewCachePolicy.PREVIEW_TINT_PREFIX
                + getPreviewImageTintColorCacheId(storyId, imageUrl, baseColor))
    }

    fun getCachedLinkSummary(
        context: Context?,
        pageUrl: String?
    ): LinkSummary? {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (TextUtils.isEmpty(normalizedUrl)) {
            return null
        }
        synchronized(StoryPreviewImageLoader::class.java) {
            val memoryResult = LINK_SUMMARY_CACHE.get(normalizedUrl)
            if (memoryResult != null) {
                return memoryResult
            }
            if (context == null) {
                return null
            }
            val preferences = getPreviewImageCachePreferences(context)
            val key = StoryPreviewImageLoader.getLinkSummaryKey(normalizedUrl!!)
            val serialized = preferences.getString(key, null) ?: return null
            val result = deserializeLinkSummary(serialized)
            if (result != null) {
                LINK_SUMMARY_CACHE.put(normalizedUrl, result)
                val orderUpdate = PreviewCachePolicy.touch(
                    readLinkSummaryCacheOrder(preferences),
                    key,
                )
                putCacheOrder(
                    preferences.edit(),
                    PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY,
                    orderUpdate.order,
                ).apply()
            }
            return result
        }
    }

    fun saveCachedLinkSummary(
        context: Context?,
        pageUrl: String?,
        result: LinkSummary?
    ) {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (TextUtils.isEmpty(normalizedUrl) || result == null) {
            return
        }
        synchronized(StoryPreviewImageLoader::class.java) {
            if (LINK_SUMMARY_CACHE.size >= MAX_CACHE_SIZE) {
                LINK_SUMMARY_CACHE.clear()
            }
            LINK_SUMMARY_CACHE.put(normalizedUrl, result)
            if (context == null) {
                return
            }
            val preferences = getPreviewImageCachePreferences(context)
            val key = StoryPreviewImageLoader.getLinkSummaryKey(normalizedUrl!!)
            val orderUpdate = PreviewCachePolicy.touch(
                readLinkSummaryCacheOrder(preferences),
                key,
            )
            val editor = preferences.edit()
                .putString(key, serializeLinkSummary(result))
            for (evictedKey in orderUpdate.evicted) {
                editor.remove(evictedKey)
            }
            putCacheOrder(
                editor,
                PreviewCachePolicy.LINK_SUMMARY_ORDER_KEY,
                orderUpdate.order,
            ).apply()
        }
    }

    private fun getLinkSummaryKey(pageUrl: String): String {
        return PreviewCachePolicy.LINK_SUMMARY_PREFIX + sha256Hex(pageUrl)
    }

    private fun serializeLinkSummary(result: LinkSummary): String = LinkSummaryCodec.encode(result)

    private fun deserializeLinkSummary(serialized: String?): LinkSummary? {
        if (serialized.isNullOrEmpty()) {
            return null
        }
        return LinkSummaryCodec.decode(serialized)
    }

    private fun getPreviewImageTintColorCacheId(
        storyId: Int,
        imageUrl: String,
        baseColor: Int
    ): String {
        return (""
                + storyId
                + ":"
                + baseColor
                + ":"
                + PreviewCachePolicy.TINT_VERSION
                + ":"
                + sha256Hex(imageUrl))
    }

    private fun sha256Hex(value: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(value.toByteArray(StandardCharsets.UTF_8))
            val hex = CharArray(bytes.size * 2)
            for (i in bytes.indices) {
                val unsignedByte = bytes[i].toInt() and 0xff
                hex[i * 2] = HEX_DIGITS[unsignedByte ushr 4]
                hex[i * 2 + 1] = HEX_DIGITS[unsignedByte and 0x0f]
            }
            return String(hex)
        } catch (e: NoSuchAlgorithmException) {
            return Integer.toHexString(value.hashCode())
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

    private class CachedPreviewImageUrl(val loaded: Boolean, val imageUrl: String?)
}
