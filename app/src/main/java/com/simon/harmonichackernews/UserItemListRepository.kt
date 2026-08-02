package com.simon.harmonichackernews

import android.content.Context
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.simon.harmonichackernews.data.Bookmark
import com.simon.harmonichackernews.utils.Utils
import java.util.Collections
internal object UserItemListRepository {
    fun normalizeSnapshot(
        itemIds: MutableList<Int>,
        commentIds: MutableList<Int>
    ): Snapshot {
        val normalizedItemIds = normalizeItemIds(itemIds)
        return Snapshot(normalizedItemIds, normalizeCommentIds(normalizedItemIds, commentIds))
    }

    fun loadCachedSnapshot(context: Context?, source: Source): Snapshot {
        val itemIds = ArrayList<Int>()
        if (context == null) {
            return Snapshot(itemIds, HashSet<Int>())
        }

        val items = loadCache(context, source)
        val seenItemIds: MutableSet<Int> = HashSet<Int>(items.size)
        for (item in items) {
            if (seenItemIds.add(item.id)) {
                itemIds.add(item.id)
            }
        }

        Collections.sort<Int>(
            itemIds,
            Comparator { id1: Int, id2: Int -> Integer.compare(id2!!, id1!!) })
        return Snapshot(itemIds, loadCommentIds(context, source))
    }

    fun loadCache(context: Context?, source: Source): ArrayList<Bookmark> {
        if (context == null) {
            return ArrayList<Bookmark>()
        }
        if (source == Source.UPVOTED) {
            return Utils.loadUpvoted(context, true)
        }
        return Utils.loadFavorites(context, true)
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
            if (cachedItems.get(i).id != snapshot.itemIds.get(i)) {
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
        if (source == Source.UPVOTED) {
            Utils.saveUpvotedIds(context, snapshot.itemIds)
            Utils.saveUpvotedCommentIds(context, snapshot.commentIds)
        } else {
            Utils.saveFavoriteIds(context, snapshot.itemIds)
            Utils.saveFavoriteCommentIds(context, snapshot.commentIds)
        }
    }

    private fun loadCommentIds(context: Context, source: Source): MutableSet<Int> {
        if (source == Source.UPVOTED) {
            return Utils.loadUpvotedCommentIds(context)
        }
        return Utils.loadFavoriteCommentIds(context)
    }

    private fun normalizeItemIds(itemIds: MutableList<Int>): ArrayList<Int> {
        val normalizedItemIds = ArrayList<Int>(itemIds.size)
        val seenItemIds: MutableSet<Int> = HashSet<Int>(itemIds.size)
        for (id in itemIds) {
            if (seenItemIds.add(id)) {
                normalizedItemIds.add(id)
            }
        }

        Collections.sort<Int>(
            normalizedItemIds,
            Comparator { id1: Int, id2: Int -> Integer.compare(id2!!, id1!!) })
        return normalizedItemIds
    }

    private fun normalizeCommentIds(
        itemIds: MutableList<Int>,
        commentIds: MutableList<Int>
    ): MutableSet<Int> {
        val itemIdSet: MutableSet<Int> = HashSet<Int>(itemIds)
        val normalizedCommentIds: MutableSet<Int> = HashSet<Int>()
        for (id in commentIds) {
            if (itemIdSet.contains(id)) {
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
