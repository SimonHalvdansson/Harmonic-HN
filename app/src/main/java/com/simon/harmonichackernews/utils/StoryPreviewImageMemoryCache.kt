package com.simon.harmonichackernews.utils

import android.util.LruCache

object StoryPreviewImageMemoryCache {
    private const val MAX_ENTRIES = 48
    private const val TINT_CACHE_VERSION = "3"
    private val tintCache = LruCache<String, Int>(MAX_ENTRIES)

    fun putTintColor(storyId: Int, imageUrl: String?, baseColor: Int, tintColor: Int) {
        if (storyId <= 0 || imageUrl.isNullOrEmpty()) return
        synchronized(tintCache) {
            tintCache.put(getTintKey(storyId, imageUrl, baseColor), tintColor)
        }
    }

    fun getTintColor(storyId: Int, imageUrl: String?, baseColor: Int): Int? {
        if (storyId <= 0 || imageUrl.isNullOrEmpty()) return null
        return synchronized(tintCache) {
            tintCache.get(getTintKey(storyId, imageUrl, baseColor))
        }
    }

    fun clearTintColors() {
        synchronized(tintCache) {
            tintCache.evictAll()
        }
    }

    private fun getKey(storyId: Int, imageUrl: String): String = "$storyId:$imageUrl"

    private fun getTintKey(storyId: Int, imageUrl: String, baseColor: Int): String =
        "${getKey(storyId, imageUrl)}:$TINT_CACHE_VERSION:$baseColor"
}
