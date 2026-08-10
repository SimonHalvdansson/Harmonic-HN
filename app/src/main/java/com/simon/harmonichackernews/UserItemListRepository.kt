package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemSnapshots
import com.simon.harmonichackernews.utils.Utils

internal object UserItemListRepository {
    fun normalizeSnapshot(
        itemIds: List<Int>,
        commentIds: List<Int>
    ): SavedItemSnapshot = SavedItemSnapshots.normalize(itemIds, commentIds)

    fun loadCachedSnapshot(context: Context?, source: Source): SavedItemSnapshot {
        if (context == null) return SavedItemSnapshot(emptyList(), emptySet())

        val itemIds = loadCache(context, source)
            .map(Bookmark::id)
            .distinct()
            .sortedDescending()
        return SavedItemSnapshot(itemIds, loadCommentIds(context, source))
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
        snapshot: SavedItemSnapshot
    ): Boolean {
        val cachedItems = loadCache(context, source)
        val cachedCommentIds = loadCommentIds(context, source)

        return snapshot.matches(
            SavedItemCodec.fromBookmarks(cachedItems),
            cachedCommentIds,
        )
    }

    fun saveIds(
        context: Context,
        source: Source,
        snapshot: SavedItemSnapshot
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

    internal enum class Source {
        FAVORITES,
        UPVOTED
    }

}
