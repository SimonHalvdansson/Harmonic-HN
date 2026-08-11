package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult

enum class SavedItemActionKind {
    VOTE,
    FAVORITE,
}

/** A persisted optimistic mutation that can be sent to Hacker News or rolled back. */
data class PendingSavedItemAction(
    val kind: SavedItemActionKind,
    val itemId: Int,
    val isComment: Boolean,
    val targetPresent: Boolean,
    val previousPresent: Boolean,
    val voteDirection: String? = null,
)

sealed interface SavedItemActionOutcome {
    data class Success(val action: PendingSavedItemAction) : SavedItemActionOutcome

    data class Failure(
        val action: PendingSavedItemAction,
        val result: HackerNewsActionResult,
    ) : SavedItemActionOutcome
}

/** Read-only saved-item state exposed to presentation controllers. */
interface SavedItemStateReader {
    fun isBookmarked(itemId: Int): Boolean

    fun isFavorited(itemId: Int): Boolean

    fun isUpvoted(itemId: Int, isComment: Boolean): Boolean
}

/**
 * Owns bookmark state and the optimistic persistence/rollback transaction for HN actions.
 * UI layers only decide how to render the pending and completed states.
 */
class SavedItemActionUseCase(
    private val repository: SavedItemsRepository,
    private val nowMillis: () -> Long,
    private val voteRequest: suspend (itemId: Int, direction: String) -> HackerNewsActionResult,
    private val favoriteRequest: suspend (itemId: Int, favorite: Boolean) -> HackerNewsActionResult,
) : SavedItemStateReader {
    override fun isBookmarked(itemId: Int): Boolean =
        repository.contains(SavedItemSource.BOOKMARKS, itemId)

    override fun isFavorited(itemId: Int): Boolean =
        repository.contains(SavedItemSource.FAVORITES, itemId)

    override fun isUpvoted(itemId: Int, isComment: Boolean): Boolean =
        if (isComment) {
            itemId in repository.loadCommentIds(SavedItemSource.UPVOTED)
        } else {
            repository.contains(SavedItemSource.UPVOTED, itemId)
        }

    fun setBookmarked(itemId: Int, bookmarked: Boolean): Boolean =
        repository.setMembership(
            SavedItemSource.BOOKMARKS,
            itemId,
            bookmarked,
            nowMillis(),
        )

    fun toggleBookmark(itemId: Int): Boolean {
        val bookmarked = !isBookmarked(itemId)
        setBookmarked(itemId, bookmarked)
        return bookmarked
    }

    fun beginVote(
        itemId: Int,
        isComment: Boolean,
        direction: String,
    ): PendingSavedItemAction {
        require(direction == "up" || direction == "down" || direction == "un")
        val previous = isUpvoted(itemId, isComment)
        val action = PendingSavedItemAction(
            kind = SavedItemActionKind.VOTE,
            itemId = itemId,
            isComment = isComment,
            targetPresent = direction == "up",
            previousPresent = previous,
            voteDirection = direction,
        )
        persist(action, action.targetPresent)
        return action
    }

    fun beginFavorite(itemId: Int, isComment: Boolean = false): PendingSavedItemAction {
        val previous = isFavorited(itemId)
        val action = PendingSavedItemAction(
            kind = SavedItemActionKind.FAVORITE,
            itemId = itemId,
            isComment = isComment,
            targetPresent = !previous,
            previousPresent = previous,
        )
        persist(action, action.targetPresent)
        return action
    }

    suspend fun execute(action: PendingSavedItemAction): SavedItemActionOutcome {
        val result = when (action.kind) {
            SavedItemActionKind.VOTE -> voteRequest(
                action.itemId,
                requireNotNull(action.voteDirection),
            )

            SavedItemActionKind.FAVORITE -> favoriteRequest(
                action.itemId,
                action.targetPresent,
            )
        }
        return if (result is HackerNewsActionResult.Success) {
            SavedItemActionOutcome.Success(action)
        } else {
            persist(action, action.previousPresent)
            SavedItemActionOutcome.Failure(action, result)
        }
    }

    private fun persist(action: PendingSavedItemAction, present: Boolean) {
        when {
            action.kind == SavedItemActionKind.FAVORITE -> repository.setMembership(
                SavedItemSource.FAVORITES,
                action.itemId,
                present,
                nowMillis(),
            )

            action.isComment -> repository.setCommentMembership(
                SavedItemSource.UPVOTED,
                action.itemId,
                present,
            )

            else -> repository.setMembership(
                SavedItemSource.UPVOTED,
                action.itemId,
                present,
                nowMillis(),
            )
        }
    }
}
