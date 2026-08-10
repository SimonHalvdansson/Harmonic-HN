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
    ): Snapshot {
        return SavedItemSnapshots.normalize(itemIds, commentIds).toAndroidSnapshot()
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

        return snapshot.toShared().matches(
            SavedItemCodec.fromBookmarks(cachedItems),
            cachedCommentIds,
        )
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

    internal enum class Source {
        FAVORITES,
        UPVOTED
    }

    internal data class Snapshot(val itemIds: List<Int>, val commentIds: Set<Int>) {
        fun toShared(): SavedItemSnapshot = SavedItemSnapshot(itemIds, commentIds)
    }

    private fun SavedItemSnapshot.toAndroidSnapshot(): Snapshot = Snapshot(itemIds, commentIds)
}
