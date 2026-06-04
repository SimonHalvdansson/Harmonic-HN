package com.simon.harmonichackernews;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class UserItemListRepository {
    enum Source {
        FAVORITES,
        UPVOTED
    }

    static class Snapshot {
        final ArrayList<Integer> itemIds;
        final Set<Integer> commentIds;

        Snapshot(ArrayList<Integer> itemIds, Set<Integer> commentIds) {
            this.itemIds = itemIds;
            this.commentIds = commentIds;
        }
    }

    @NonNull
    static Snapshot normalizeSnapshot(@NonNull List<Integer> itemIds,
                                      @NonNull List<Integer> commentIds) {
        ArrayList<Integer> normalizedItemIds = normalizeItemIds(itemIds);
        return new Snapshot(normalizedItemIds, normalizeCommentIds(normalizedItemIds, commentIds));
    }

    @NonNull
    static Snapshot loadCachedSnapshot(@Nullable Context context, @NonNull Source source) {
        ArrayList<Integer> itemIds = new ArrayList<>();
        if (context == null) {
            return new Snapshot(itemIds, new HashSet<>());
        }

        ArrayList<Bookmark> items = loadCache(context, source);
        for (Bookmark item : items) {
            if (!itemIds.contains(item.id)) {
                itemIds.add(item.id);
            }
        }

        Collections.sort(itemIds, (id1, id2) -> Integer.compare(id2, id1));
        return new Snapshot(itemIds, loadCommentIds(context, source));
    }

    @NonNull
    static ArrayList<Bookmark> loadCache(@Nullable Context context, @NonNull Source source) {
        if (context == null) {
            return new ArrayList<>();
        }
        if (source == Source.UPVOTED) {
            return Utils.loadUpvoted(context, true);
        }
        return Utils.loadFavorites(context, true);
    }

    static boolean idsMatchCache(@NonNull Context context,
                                 @NonNull Source source,
                                 @NonNull Snapshot snapshot) {
        ArrayList<Bookmark> cachedItems = loadCache(context, source);
        Set<Integer> cachedCommentIds = loadCommentIds(context, source);

        if (cachedItems.size() != snapshot.itemIds.size()
                || !cachedCommentIds.equals(snapshot.commentIds)) {
            return false;
        }

        for (int i = 0; i < cachedItems.size(); i++) {
            if (cachedItems.get(i).id != snapshot.itemIds.get(i)) {
                return false;
            }
        }

        return true;
    }

    static void saveIds(@NonNull Context context,
                        @NonNull Source source,
                        @NonNull Snapshot snapshot) {
        if (source == Source.UPVOTED) {
            Utils.saveUpvotedIds(context, snapshot.itemIds);
            Utils.saveUpvotedCommentIds(context, snapshot.commentIds);
        } else {
            Utils.saveFavoriteIds(context, snapshot.itemIds);
            Utils.saveFavoriteCommentIds(context, snapshot.commentIds);
        }
    }

    @NonNull
    private static Set<Integer> loadCommentIds(@NonNull Context context, @NonNull Source source) {
        if (source == Source.UPVOTED) {
            return Utils.loadUpvotedCommentIds(context);
        }
        return Utils.loadFavoriteCommentIds(context);
    }

    @NonNull
    private static ArrayList<Integer> normalizeItemIds(@NonNull List<Integer> itemIds) {
        ArrayList<Integer> normalizedItemIds = new ArrayList<>();
        for (int id : itemIds) {
            if (!normalizedItemIds.contains(id)) {
                normalizedItemIds.add(id);
            }
        }

        Collections.sort(normalizedItemIds, (id1, id2) -> Integer.compare(id2, id1));
        return normalizedItemIds;
    }

    @NonNull
    private static Set<Integer> normalizeCommentIds(@NonNull List<Integer> itemIds,
                                                    @NonNull List<Integer> commentIds) {
        Set<Integer> itemIdSet = new HashSet<>(itemIds);
        Set<Integer> normalizedCommentIds = new HashSet<>();
        for (int id : commentIds) {
            if (itemIdSet.contains(id)) {
                normalizedCommentIds.add(id);
            }
        }
        return normalizedCommentIds;
    }
}
