package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.utils.Utils

internal object UserItemListRepository {
    fun normalizeSnapshot(
        itemIds: List<Int>,
        commentIds: List<Int>
    ): Snapshot {
        val normalizedItemIds = normalizeItemIds(itemIds)
        return Snapshot(normalizedItemIds, normalizeCommentIds(normalizedItemIds, commentIds))
    }

    fun loadCachedSnapshot(context: Context?, source: Source): Snapshot {
        val itemIds = ArrayList<Int>()
        if (context == null) {
            return Snapshot(itemIds, HashSet())
        }

        val items = loadCache(context, source)
        val seenItemIds = HashSet<Int>(items.size)
        for (item in items) {
            if (seenItemIds.add(item.id)) {
                itemIds.add(item.id)
            }
        }

        itemIds.sortDescending()
        return Snapshot(itemIds, loadCommentIds(context, source))
    }

    fun loadCache(context: Context?, source: Source): ArrayList<Bookmark> {
        if (context == null) {
            return ArrayList()
        }
        return when (source) {
            Source.UPVOTED -> Utils.loadUpvoted(context, true)
            Source.FAVORITES -> Utils.loadFavorites(context, true)
        }
    }

    fun idsMatchCache(
        context: Context,
        source: Source,
        snapshot: Snapshot
    ): Boolean {
        val cachedItems = loadCache(context, source)
        val cachedCommentIds = loadCommentIds(context, source)

        if (cachedItems.size != snapshot.itemIds.size
            || cachedCommentIds != snapshot.commentIds
        ) {
            return false
        }

        for (i in cachedItems.indices) {
            if (cachedItems[i].id != snapshot.itemIds[i]) {
                return false
            }
        }

        return true
    }

    fun saveIds(
        context: Context,
        source: Source,
        snapshot: Snapshot
    ) {
        when (source) {
            Source.UPVOTED -> {
                Utils.saveUpvotedIds(context, snapshot.itemIds)
                Utils.saveUpvotedCommentIds(context, snapshot.commentIds)
            }
            Source.FAVORITES -> {
                Utils.saveFavoriteIds(context, snapshot.itemIds)
                Utils.saveFavoriteCommentIds(context, snapshot.commentIds)
            }
        }
    }

    private fun loadCommentIds(context: Context, source: Source): MutableSet<Int> =
        when (source) {
            Source.UPVOTED -> Utils.loadUpvotedCommentIds(context)
            Source.FAVORITES -> Utils.loadFavoriteCommentIds(context)
        }

    private fun normalizeItemIds(itemIds: List<Int>): ArrayList<Int> {
        val normalizedItemIds = ArrayList<Int>(itemIds.size)
        val seenItemIds = HashSet<Int>(itemIds.size)
        for (id in itemIds) {
            if (seenItemIds.add(id)) {
                normalizedItemIds.add(id)
            }
        }

        normalizedItemIds.sortDescending()
        return normalizedItemIds
    }

    private fun normalizeCommentIds(
        itemIds: List<Int>,
        commentIds: List<Int>
    ): MutableSet<Int> {
        val itemIdSet = itemIds.toHashSet()
        val normalizedCommentIds = HashSet<Int>()
        for (id in commentIds) {
            if (id in itemIdSet) {
                normalizedCommentIds.add(id)
            }
        }
        return normalizedCommentIds
    }

    internal enum class Source {
        FAVORITES,
        UPVOTED
    }

    internal class Snapshot(val itemIds: ArrayList<Int>, val commentIds: MutableSet<Int>)
}
