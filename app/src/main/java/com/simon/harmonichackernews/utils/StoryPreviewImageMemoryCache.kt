package com.simon.harmonichackernews.utils

import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.text.TextUtils
import android.util.LruCache
import androidx.annotation.Nullable

object StoryPreviewImageMemoryCache {
    private const val MAX_ENTRIES = 48
    private val CACHE = LruCache<String?, ConstantState?>(MAX_ENTRIES)
    private val TINT_CACHE = LruCache<String?, Int?>(MAX_ENTRIES)

    fun put(storyId: Int, imageUrl: String?, drawable: Drawable?) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl) || drawable == null) {
            return
        }

        val constantState = drawable.getConstantState()
        if (constantState != null) {
            synchronized(CACHE) {
                CACHE.put(getKey(storyId, imageUrl), constantState)
            }
        }
    }

    fun get(storyId: Int, imageUrl: String?): Drawable? {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null
        }

        val constantState: ConstantState?
        synchronized(CACHE) {
            constantState = CACHE.get(getKey(storyId, imageUrl))
        }
        return if (constantState == null) null else constantState.newDrawable()
    }

    fun putTintColor(storyId: Int, imageUrl: String?, baseColor: Int, tintColor: Int) {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return
        }

        synchronized(TINT_CACHE) {
            TINT_CACHE.put(getTintKey(storyId, imageUrl, baseColor), tintColor)
        }
    }

    fun getTintColor(storyId: Int, imageUrl: String?, baseColor: Int): Int? {
        if (storyId <= 0 || TextUtils.isEmpty(imageUrl)) {
            return null
        }

        synchronized(TINT_CACHE) {
            return TINT_CACHE.get(getTintKey(storyId, imageUrl, baseColor))
        }
    }

    fun clearTintColors() {
        synchronized(TINT_CACHE) {
            TINT_CACHE.evictAll()
        }
    }

    private fun getKey(storyId: Int, imageUrl: String?): String {
        return storyId.toString() + ":" + imageUrl
    }

    private fun getTintKey(storyId: Int, imageUrl: String?, baseColor: Int): String {
        return getKey(storyId, imageUrl) + ":" + baseColor
    }
}
