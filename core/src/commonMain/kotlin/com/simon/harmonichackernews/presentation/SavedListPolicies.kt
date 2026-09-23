package com.simon.harmonichackernews.presentation

enum class SavedListKind {
    HISTORY,
    FAVORITES,
    UPVOTED,
    BOOKMARKS,
}

object SavedListPresentationPolicy {
    fun emptyMessage(
        kind: SavedListKind,
        filter: SavedItemFilter,
        sourceHasItems: Boolean,
    ): String = when (kind) {
        SavedListKind.HISTORY -> "No history"
        SavedListKind.FAVORITES -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No favorites"
            filter == SavedItemFilter.STORIES -> "No favorite stories"
            else -> "No favorite comments"
        }
        SavedListKind.UPVOTED -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No upvoted items"
            filter == SavedItemFilter.STORIES -> "No upvoted stories"
            else -> "No upvoted comments"
        }
        SavedListKind.BOOKMARKS -> when {
            !sourceHasItems || filter == SavedItemFilter.BOTH -> "No bookmarks"
            filter == SavedItemFilter.STORIES -> "No bookmarked stories"
            else -> "No bookmarked comments"
        }
    }
}

data class SavedItemStoryReconciliation(
    val changed: Boolean,
    val stories: List<com.simon.harmonichackernews.data.Story>,
)

object SavedItemStoryReconciler {
    fun reconcile(
        currentStories: List<com.simon.harmonichackernews.data.Story>,
        currentCommentIds: Set<Int>,
        itemIds: List<Int>,
        commentIds: Set<Int>,
    ): SavedItemStoryReconciliation {
        val unchanged = currentCommentIds == commentIds &&
            currentStories.size == itemIds.size &&
            currentStories.indices.all { currentStories[it].id == itemIds[it] }
        if (unchanged) return SavedItemStoryReconciliation(false, currentStories)

        val existingById = currentStories.associateBy { it.id }
        val reconciled = itemIds.map { id ->
            (existingById[id] ?: com.simon.harmonichackernews.data.Story(
                "Loading...",
                id,
                false,
                false,
            )).also { story ->
                if (id in commentIds) story.isComment = true
            }
        }
        return SavedItemStoryReconciliation(true, reconciled)
    }
}
