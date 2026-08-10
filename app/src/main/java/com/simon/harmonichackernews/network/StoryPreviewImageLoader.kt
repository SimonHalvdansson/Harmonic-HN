package com.simon.harmonichackernews.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.simon.harmonichackernews.network.LinkSummaryLoader.isYoutubeVideoUrl
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.Job

object StoryPreviewImageLoader {
    private const val MAX_CACHE_SIZE = 300
    private const val MAX_MISS_CACHE_SIZE = 1000
    private const val MAX_DISK_CACHE_SIZE = 1000
    private const val LEGACY_TINT_CACHE_KEYS_REMOVED_PER_SAVE = 8
    private const val PREVIEW_IMAGE_TINT_CACHE_VERSION = "3"
    private const val PREVIEW_IMAGE_CACHE_PREFERENCES =
        "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES"
    private const val KEY_PREVIEW_IMAGE_CACHE_ORDER =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_CACHE_ORDER"
    private const val KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER"
    private const val KEY_LINK_SUMMARY_CACHE_ORDER =
        "com.simon.harmonichackernews.KEY_LINK_SUMMARY_CACHE_ORDER"
    private const val KEY_PREVIEW_IMAGE_URL = "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL"
    private const val KEY_PREVIEW_IMAGE_URL_LOADED =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_URL_LOADED"
    private const val KEY_PREVIEW_IMAGE_TINT_COLOR =
        "com.simon.harmonichackernews.KEY_PREVIEW_IMAGE_TINT_COLOR"
    private const val KEY_LINK_SUMMARY = "com.simon.harmonichackernews.KEY_LINK_SUMMARY"
    private const val YOUTUBE_OEMBED_CACHE_SUFFIX = "youtube_oembed"
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val IMAGE_CACHE: MutableMap<String?, String?> = HashMap<String?, String?>()
    private val LINK_SUMMARY_CACHE: MutableMap<String?, LinkSummaryLoader.Result?> =
        HashMap<String?, LinkSummaryLoader.Result?>()
    private val CACHE_ORDERS: MutableMap<String, List<String>> = HashMap()
    private val MISS_CACHE: MutableSet<String?> = LinkedHashSet<String?>()
    private val PENDING_CALLBACKS: MutableMap<String?, PendingPreviewImageBatch?> =
        HashMap<String?, PendingPreviewImageBatch?>()
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
            PreviewContentCallback { imageUrl: String?, summary: LinkSummaryLoader.Result? ->
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
        val previewImageRequest = PendingPreviewImageRequest(
            appContext,
            storyId,
            callback
        )
        val normalizedPageUrl = normalizeHttpUrl(pageUrl)
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

        if (StoryPreviewImageLoader.isLikelyImageUrl(normalizedPageUrl)) {
            saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, normalizedPageUrl)
            postResult(previewImageRequest, normalizedPageUrl, null)
            return previewImageRequest
        }

        var pendingBatch: PendingPreviewImageBatch?
        synchronized(StoryPreviewImageLoader::class.java) {
            if (!forceRefresh) {
                val cachedImageUrl = IMAGE_CACHE.get(normalizedPageUrl)
                if (!requireSummary && !TextUtils.isEmpty(cachedImageUrl)) {
                    saveCachedPreviewImageUrl(appContext, previewImageCacheEntryId, cachedImageUrl)
                    postResult(previewImageRequest, cachedImageUrl, null)
                    return previewImageRequest
                }

                if (!requireSummary && MISS_CACHE.contains(normalizedPageUrl)) {
                    postResult(previewImageRequest, null, null)
                    return previewImageRequest
                }
            }
            pendingBatch = PENDING_CALLBACKS.get(normalizedPageUrl)
            if (pendingBatch != null) {
                previewImageRequest.attach(normalizedPageUrl, pendingBatch)
                pendingBatch.requests.add(previewImageRequest)
                return previewImageRequest
            }

            pendingBatch = PendingPreviewImageBatch()
            previewImageRequest.attach(normalizedPageUrl, pendingBatch)
            pendingBatch.requests.add(previewImageRequest)
            PENDING_CALLBACKS.put(normalizedPageUrl, pendingBatch)
        }

        val requestBatch = pendingBatch
        val job = NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.linkSummaryRepository.load(normalizedPageUrl)
            },
            onSuccess = { sharedSummary ->
                val summary = LinkSummaryLoader.fromShared(sharedSummary)
                StoryPreviewImageLoader.finish(
                    normalizedPageUrl,
                    requestBatch!!,
                    summary.imageUrl,
                    summary,
                )
            },
            onFailure = {
                StoryPreviewImageLoader.finish(normalizedPageUrl, requestBatch!!, null, null)
            },
        )
        synchronized(StoryPreviewImageLoader::class.java) {
            if (PENDING_CALLBACKS.get(normalizedPageUrl) === requestBatch) {
                requestBatch!!.call = job
            } else {
                job.cancel()
            }
        }
        return previewImageRequest
    }

    fun getCachedPreviewImageUrl(context: Context?, storyId: Int, pageUrl: String?): String? {
        val appContext = if (context == null) null else context.getApplicationContext()
        val normalizedPageUrl = normalizeHttpUrl(pageUrl)
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            return null
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).imageUrl
    }

    fun isCachedPreviewImageUrlLoaded(context: Context?, storyId: Int, pageUrl: String?): Boolean {
        val appContext = if (context == null) null else context.getApplicationContext()
        val normalizedPageUrl = normalizeHttpUrl(pageUrl)
        if (TextUtils.isEmpty(normalizedPageUrl)) {
            return false
        }

        val previewImageCacheEntryId = getPreviewImageCacheEntryId(storyId, normalizedPageUrl)
        return loadCachedPreviewImageUrl(appContext, previewImageCacheEntryId, false).loaded
    }

    private fun normalizeHttpUrl(url: String?): String? {
        if (TextUtils.isEmpty(url)) {
            return null
        }

        val parsedUrl: NetworkUrl? = url?.toNetworkUrlOrNull()
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            return null
        }
        return parsedUrl.toString()
    }

    private fun isHttpScheme(url: NetworkUrl): Boolean {
        return "http" == url.scheme || "https" == url.scheme
    }

    private fun isLikelyImageUrl(url: String): Boolean {
        val parsedUrl: NetworkUrl? = url.toNetworkUrlOrNull()
        if (parsedUrl == null) {
            return false
        }

        val path = parsedUrl.encodedPath.lowercase()
        return path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".png")
                || path.endsWith(".gif")
                || path.endsWith(".webp")
                || path.endsWith(".avif")
    }

    private fun finish(
        pageUrl: String?,
        batch: PendingPreviewImageBatch,
        imageUrl: String?,
        summary: LinkSummaryLoader.Result?
    ) {
        val pendingRequests: MutableList<PendingPreviewImageRequest>?
        synchronized(StoryPreviewImageLoader::class.java) {
            if (PENDING_CALLBACKS.get(pageUrl) !== batch) {
                return
            }
            PENDING_CALLBACKS.remove(pageUrl)
            pendingRequests = ArrayList<PendingPreviewImageRequest>(batch.requests)
            for (pendingRequest in pendingRequests) {
                pendingRequest.detach(batch)
            }
            if (TextUtils.isEmpty(imageUrl)) {
                cacheMiss(pageUrl)
            } else {
                if (IMAGE_CACHE.size >= MAX_CACHE_SIZE) {
                    IMAGE_CACHE.clear()
                    MISS_CACHE.clear()
                }
                IMAGE_CACHE.put(pageUrl, imageUrl)
            }
        }

        if (pendingRequests == null) {
            return
        }

        for (pendingRequest in pendingRequests) {
            if (!pendingRequest.isCancelled) {
                saveCachedPreviewImageUrl(
                    pendingRequest.context,
                    getPreviewImageCacheEntryId(pendingRequest.storyId, pageUrl),
                    imageUrl
                )
                if (summary != null) {
                    saveCachedLinkSummary(pendingRequest.context, pageUrl, summary)
                }
            }
        }

        MAIN_HANDLER.post(Runnable {
            for (pendingRequest in pendingRequests) {
                if (!pendingRequest.isCancelled) {
                    pendingRequest.callback.onPreviewContentLoaded(imageUrl, summary)
                }
            }
        })
    }

    private fun cacheMiss(pageUrl: String?) {
        MISS_CACHE.remove(pageUrl)
        MISS_CACHE.add(pageUrl)
        while (MISS_CACHE.size > MAX_MISS_CACHE_SIZE) {
            val iterator = MISS_CACHE.iterator()
            iterator.next()
            iterator.remove()
        }
    }

    private fun postResult(
        request: PendingPreviewImageRequest,
        imageUrl: String?,
        summary: LinkSummaryLoader.Result?
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
                if (KEY_PREVIEW_IMAGE_CACHE_ORDER == key
                    || KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER == key
                    || KEY_LINK_SUMMARY_CACHE_ORDER == key
                    || key.startsWith(KEY_PREVIEW_IMAGE_URL)
                    || key.startsWith(KEY_PREVIEW_IMAGE_URL_LOADED)
                    || key.startsWith(KEY_PREVIEW_IMAGE_TINT_COLOR)
                    || key.startsWith(KEY_LINK_SUMMARY)
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
            val orderedIds = readPreviewImageCacheOrder(preferences)
            orderedIds.remove(previewImageCacheEntryId)
            orderedIds.add(previewImageCacheEntryId!!)

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

            while (orderedIds.size > MAX_DISK_CACHE_SIZE) {
                val oldestId = orderedIds.removeAt(0)
                editor.remove(getPreviewImageUrlKey(oldestId))
                editor.remove(getPreviewImageUrlLoadedKey(oldestId))
            }
            putCacheOrder(editor, KEY_PREVIEW_IMAGE_CACHE_ORDER, orderedIds).apply()
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
                if (!isCurrentPreviewImageTintColorKey(orderedKey)) {
                    iterator.remove()
                    editor.remove(orderedKey)
                    legacyKeysRemoved++
                }
            }
            orderedKeys.remove(tintColorKey)
            orderedKeys.add(tintColorKey)

            editor.putInt(tintColorKey, tintColor)
            while (orderedKeys.size > MAX_DISK_CACHE_SIZE) {
                editor.remove(orderedKeys.removeAt(0))
            }
            putCacheOrder(editor, KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER, orderedKeys).apply()
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
        val orderedIds = readPreviewImageCacheOrder(preferences)
        orderedIds.remove(previewImageCacheEntryId)
        orderedIds.add(previewImageCacheEntryId!!)
        putCacheOrder(
            preferences.edit(),
            KEY_PREVIEW_IMAGE_CACHE_ORDER,
            orderedIds,
        ).apply()
    }

    private fun readPreviewImageCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, KEY_PREVIEW_IMAGE_CACHE_ORDER)
    }

    private fun readPreviewImageTintCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER)
    }

    private fun readLinkSummaryCacheOrder(preferences: SharedPreferences): MutableList<String> {
        return readCacheOrder(preferences, KEY_LINK_SUMMARY_CACHE_ORDER)
    }

    private fun readCacheOrder(
        preferences: SharedPreferences,
        orderKey: String,
    ): MutableList<String> {
        CACHE_ORDERS[orderKey]?.let { return ArrayList(it) }

        val orderedIds = ArrayList<String>()
        val seenIds = HashSet<String>()
        val order = preferences.getString(orderKey, "").orEmpty()
        if (order.isEmpty()) {
            CACHE_ORDERS[orderKey] = emptyList()
            return orderedIds
        }

        for (storyId in order.split(',')) {
            if (storyId.isNotEmpty() && seenIds.add(storyId)) {
                orderedIds.add(storyId)
            }
        }
        CACHE_ORDERS[orderKey] = ArrayList(orderedIds)
        return orderedIds
    }

    private fun putCacheOrder(
        editor: SharedPreferences.Editor,
        orderKey: String,
        order: List<String>,
    ): SharedPreferences.Editor {
        CACHE_ORDERS[orderKey] = ArrayList(order)
        return editor.putString(orderKey, TextUtils.join(",", order))
    }

    private fun movePreviewImageTintCacheKeyToEnd(
        preferences: SharedPreferences,
        tintColorKey: String?
    ) {
        val orderedKeys = readPreviewImageTintCacheOrder(preferences)
        orderedKeys.remove(tintColorKey)
        orderedKeys.add(tintColorKey!!)
        putCacheOrder(
            preferences.edit(),
            KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER,
            orderedKeys,
        ).apply()
    }

    fun clearCachedPreviewImageTintColors(context: Context?) {
        if (context == null) {
            return
        }

        synchronized(StoryPreviewImageLoader::class.java) {
            val preferences = getPreviewImageCachePreferences(context)
            if (!preferences.contains(KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER)) {
                return
            }

            val editor = preferences.edit()
                .remove(KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER)
            CACHE_ORDERS.remove(KEY_PREVIEW_IMAGE_TINT_CACHE_ORDER)
            for (key in preferences.getAll().keys) {
                if (key.startsWith(KEY_PREVIEW_IMAGE_TINT_COLOR)) {
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
        if (storyId <= 0) {
            return null
        }

        if (isYoutubeVideoUrl(pageUrl)) {
            return storyId.toString() + ":" + YOUTUBE_OEMBED_CACHE_SUFFIX
        }
        return storyId.toString()
    }

    private fun getPreviewImageUrlKey(previewImageCacheEntryId: String): String {
        return KEY_PREVIEW_IMAGE_URL + previewImageCacheEntryId
    }

    private fun getPreviewImageUrlLoadedKey(previewImageCacheEntryId: String): String {
        return KEY_PREVIEW_IMAGE_URL_LOADED + previewImageCacheEntryId
    }

    private fun getPreviewImageTintColorKey(
        storyId: Int,
        imageUrl: String,
        baseColor: Int
    ): String {
        return (KEY_PREVIEW_IMAGE_TINT_COLOR
                + getPreviewImageTintColorCacheId(storyId, imageUrl, baseColor))
    }

    fun getCachedLinkSummary(
        context: Context?,
        pageUrl: String?
    ): LinkSummaryLoader.Result? {
        val normalizedUrl = normalizeHttpUrl(pageUrl)
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
                val order = readLinkSummaryCacheOrder(preferences)
                order.remove(key)
                order.add(key)
                putCacheOrder(
                    preferences.edit(),
                    KEY_LINK_SUMMARY_CACHE_ORDER,
                    order,
                ).apply()
            }
            return result
        }
    }

    fun saveCachedLinkSummary(
        context: Context?,
        pageUrl: String?,
        result: LinkSummaryLoader.Result?
    ) {
        val normalizedUrl = normalizeHttpUrl(pageUrl)
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
            val order = readLinkSummaryCacheOrder(preferences)
            order.remove(key)
            order.add(key)
            val editor = preferences.edit()
                .putString(key, serializeLinkSummary(result))
            while (order.size > MAX_DISK_CACHE_SIZE) {
                editor.remove(order.removeAt(0))
            }
            putCacheOrder(editor, KEY_LINK_SUMMARY_CACHE_ORDER, order).apply()
        }
    }

    private fun getLinkSummaryKey(pageUrl: String): String {
        return KEY_LINK_SUMMARY + sha256Hex(pageUrl)
    }

    private fun serializeLinkSummary(result: LinkSummaryLoader.Result): String {
        return LinkSummaryCodec.encode(LinkSummaryLoader.toShared(result))
    }

    private fun deserializeLinkSummary(serialized: String?): LinkSummaryLoader.Result? {
        if (serialized.isNullOrEmpty()) {
            return null
        }
        return LinkSummaryCodec.decode(serialized)?.let(LinkSummaryLoader::fromShared)
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
                + PREVIEW_IMAGE_TINT_CACHE_VERSION
                + ":"
                + sha256Hex(imageUrl))
    }

    private fun isCurrentPreviewImageTintColorKey(key: String?): Boolean {
        if (TextUtils.isEmpty(key) || !key!!.startsWith(KEY_PREVIEW_IMAGE_TINT_COLOR)) {
            return false
        }

        val cacheId = key.substring(KEY_PREVIEW_IMAGE_TINT_COLOR.length)
        val parts = cacheId.split(':', limit = 4)
        if (parts.size != 4
            || parts[2] != PREVIEW_IMAGE_TINT_CACHE_VERSION
            || parts[3].isEmpty()
        ) {
            return false
        }
        return parts[0].toIntOrNull() != null && parts[1].toIntOrNull() != null
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
            summary: LinkSummaryLoader.Result?
        )
    }

    interface PreviewImageRequest {
        fun cancel()

        val isCancelled: Boolean
    }

    private class PendingPreviewImageBatch {
        val requests: MutableList<PendingPreviewImageRequest> = ArrayList()
        var call: Job? = null
    }

    private class PendingPreviewImageRequest(
        val context: Context?,
        val storyId: Int,
        val callback: PreviewContentCallback
    ) : PreviewImageRequest {
        private var cancelled = false
        private var pageUrl: String? = null
        private var batch: PendingPreviewImageBatch? = null

        fun attach(pageUrl: String?, batch: PendingPreviewImageBatch?) {
            this.pageUrl = pageUrl
            this.batch = batch
        }

        fun detach(detachedBatch: PendingPreviewImageBatch?) {
            if (batch === detachedBatch) {
                pageUrl = null
                batch = null
            }
        }

        override fun cancel() {
            synchronized(StoryPreviewImageLoader::class.java) {
                if (cancelled) {
                    return
                }
                cancelled = true
                val attachedPageUrl = pageUrl ?: return
                val attachedBatch = batch ?: return

                attachedBatch.requests.remove(this)
                if (attachedBatch.requests.isEmpty() &&
                    PENDING_CALLBACKS[attachedPageUrl] === attachedBatch
                ) {
                    PENDING_CALLBACKS.remove(attachedPageUrl)
                    attachedBatch.call?.cancel()
                }
                pageUrl = null
                batch = null
            }
        }

        override val isCancelled: Boolean
            get() = synchronized(StoryPreviewImageLoader::class.java) { cancelled }
    }

    private class CachedPreviewImageUrl(val loaded: Boolean, val imageUrl: String?)
}
