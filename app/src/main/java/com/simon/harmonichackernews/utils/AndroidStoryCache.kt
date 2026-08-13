package com.simon.harmonichackernews.utils

import android.content.Context
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheRepository
import java.io.File

object AndroidStoryCache {
    fun store(context: Context, id: Int, payload: String?) =
        repository(context).storeStory(id, payload, System.currentTimeMillis())

    fun loadPayload(context: Context, id: Int): String? =
        id.takeIf { it > 0 }?.let { repository(context).loadStoryPayload(it) }

    fun hydrate(context: Context?, story: Story?): Boolean =
        if (context == null) false
        else story?.takeIf { it.id > 0 }?.let { repository(context).hydrateStory(it) } ?: false

    fun itemCount(context: Context): Int = repository(context).cachedItemIds().size

    fun clear(context: Context): Int = repository(context).clear()

    fun remove(context: Context, id: Int) {
        if (id > 0) repository(context).remove(id)
    }

    fun loadArticle(context: Context, id: Int): String? =
        id.takeIf { it > 0 }?.let { repository(context).loadArticle(it, System.currentTimeMillis()) }

    fun articleUrl(context: Context, id: Int): String? =
        id.takeIf { it > 0 }?.let { repository(context).articleUrl(it) }

    fun articleDirectory(context: Context): File = File(context.filesDir, "article_cache")

    fun hasRecentStories(context: Context): Boolean =
        repository(context).hasRecentStories(System.currentTimeMillis())

    fun recentStories(context: Context): ArrayList<Story> =
        ArrayList(repository(context).recentStories(System.currentTimeMillis()))

    private fun repository(context: Context): StoryCacheRepository =
        AndroidStoryCacheRepositories.get(context)
}
