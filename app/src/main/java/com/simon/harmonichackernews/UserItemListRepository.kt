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
        if (context == null) return Snapshot(emptyList(), emptySet())

        val itemIds = loadCache(context, source)
            .map(Bookmark::id)
            .distinct()
            .sortedDescending()
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

        return cachedItems.map(Bookmark::id) == snapshot.itemIds &&
            cachedCommentIds == snapshot.commentIds
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

    private fun loadCommentIds(context: Context, source: Source): Set<Int> =
        when (source) {
            Source.UPVOTED -> Utils.loadUpvotedCommentIds(context)
            Source.FAVORITES -> Utils.loadFavoriteCommentIds(context)
        }

    private fun normalizeItemIds(itemIds: List<Int>): List<Int> =
        itemIds.distinct().sortedDescending()

    private fun normalizeCommentIds(
        itemIds: List<Int>,
        commentIds: List<Int>
    ): Set<Int> {
        val itemIdSet = itemIds.toHashSet()
        return commentIds.filterTo(mutableSetOf()) { it in itemIdSet }
    }

    internal enum class Source {
        FAVORITES,
        UPVOTED
    }

    internal data class Snapshot(val itemIds: List<Int>, val commentIds: Set<Int>)
}
