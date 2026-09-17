package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemMutationToken
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.HackerNewsActionFailureReason
import com.simon.harmonichackernews.network.HackerNewsActionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

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
    val mutationToken: SavedItemMutationToken? = null,
    val previousItemPresent: Boolean = previousPresent,
    val previousCommentPresent: Boolean = false,
)

sealed interface SavedItemActionOutcome {
    data class Success(val action: PendingSavedItemAction) : SavedItemActionOutcome

    data class Failure(
        val action: PendingSavedItemAction,
        val result: HackerNewsActionResult,
    ) : SavedItemActionOutcome

    /** HN may have committed the request, so the optimistic target is retained until sync. */
    data class Indeterminate(
        val action: PendingSavedItemAction,
        val result: HackerNewsActionResult.Failure,
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

    suspend fun toggleBookmarkAtomic(itemId: Int): Boolean = repository.toggleMembershipAtomic(
        SavedItemSource.BOOKMARKS,
        itemId,
        nowMillis(),
    ).currentPresent

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

    suspend fun beginVoteAtomic(
        itemId: Int,
        isComment: Boolean,
        direction: String,
    ): PendingSavedItemAction {
        require(direction == "up" || direction == "down" || direction == "un")
        val targetPresent = direction == "up"
        val mutation = if (isComment) {
            repository.updateClassifiedMembershipAtomic(
                SavedItemSource.UPVOTED,
                itemId,
                targetPresent,
                nowMillis(),
                previousFromComment = true,
            )
        } else {
            repository.updateMembershipAtomic(
                SavedItemSource.UPVOTED,
                itemId,
                targetPresent,
                nowMillis(),
            )
        }
        return PendingSavedItemAction(
            kind = SavedItemActionKind.VOTE,
            itemId = itemId,
            isComment = isComment,
            targetPresent = targetPresent,
            previousPresent = mutation.previousPresent,
            voteDirection = direction,
            mutationToken = mutation.token,
            previousItemPresent = mutation.previousItemPresent,
            previousCommentPresent = mutation.previousCommentPresent,
        )
    }

    suspend fun toggleVoteAtomic(
        itemId: Int,
        isComment: Boolean,
    ): PendingSavedItemAction {
        val mutation = if (isComment) {
            repository.toggleClassifiedMembershipAtomic(
                SavedItemSource.UPVOTED,
                itemId,
                nowMillis(),
                previousFromComment = true,
            )
        } else {
            repository.toggleMembershipAtomic(SavedItemSource.UPVOTED, itemId, nowMillis())
        }
        return PendingSavedItemAction(
            kind = SavedItemActionKind.VOTE,
            itemId = itemId,
            isComment = isComment,
            targetPresent = mutation.currentPresent,
            previousPresent = mutation.previousPresent,
            voteDirection = if (mutation.currentPresent) "up" else "un",
            mutationToken = mutation.token,
            previousItemPresent = mutation.previousItemPresent,
            previousCommentPresent = mutation.previousCommentPresent,
        )
    }

    /**
     * Serializes the complete optimistic vote transaction for one item. Keeping the local
     * mutation, request, and rollback/reconciliation under the same repository-owned lock also
     * orders actions started by different screens or use-case instances.
     */
    suspend fun toggleVoteAndExecuteAtomic(
        itemId: Int,
        isComment: Boolean,
        onPending: (PendingSavedItemAction) -> Unit = {},
    ): SavedItemActionOutcome = executeSerialized(
        source = SavedItemSource.UPVOTED,
        itemId = itemId,
        createPending = { toggleVoteAtomic(itemId, isComment) },
        onPending = onPending,
    )

    suspend fun updateVoteAndExecuteAtomic(
        itemId: Int,
        isComment: Boolean,
        direction: String,
        onPending: (PendingSavedItemAction) -> Unit = {},
    ): SavedItemActionOutcome = executeSerialized(
        source = SavedItemSource.UPVOTED,
        itemId = itemId,
        createPending = { beginVoteAtomic(itemId, isComment, direction) },
        onPending = onPending,
    )

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

    suspend fun beginFavoriteAtomic(
        itemId: Int,
        isComment: Boolean = false,
    ): PendingSavedItemAction {
        val mutation = if (isComment) {
            repository.toggleClassifiedMembershipAtomic(
                SavedItemSource.FAVORITES,
                itemId,
                nowMillis(),
                previousFromComment = false,
            )
        } else {
            repository.toggleMembershipAtomic(
                SavedItemSource.FAVORITES,
                itemId,
                nowMillis(),
            )
        }
        return PendingSavedItemAction(
            kind = SavedItemActionKind.FAVORITE,
            itemId = itemId,
            isComment = isComment,
            targetPresent = mutation.currentPresent,
            previousPresent = mutation.previousPresent,
            mutationToken = mutation.token,
            previousItemPresent = mutation.previousItemPresent,
            previousCommentPresent = mutation.previousCommentPresent,
        )
    }

    suspend fun toggleFavoriteAndExecuteAtomic(
        itemId: Int,
        isComment: Boolean = false,
        onPending: (PendingSavedItemAction) -> Unit = {},
    ): SavedItemActionOutcome = executeSerialized(
        source = SavedItemSource.FAVORITES,
        itemId = itemId,
        createPending = { beginFavoriteAtomic(itemId, isComment) },
        onPending = onPending,
    )

    suspend fun execute(action: PendingSavedItemAction): SavedItemActionOutcome {
        try {
            currentCoroutineContext().ensureActive()
        } catch (error: CancellationException) {
            rollbackIgnoringFailure(action)
            throw error
        }
        // Once a mutating request starts, let the HTTP layer's bounded result settle the
        // optimistic state. A lifecycle cancellation after the server commits is ambiguous and
        // must not guess by rolling local state back.
        return withContext(NonCancellable) {
            val result = try {
                when (action.kind) {
                    SavedItemActionKind.VOTE -> voteRequest(
                        action.itemId,
                        requireNotNull(action.voteDirection),
                    )

                    SavedItemActionKind.FAVORITE -> favoriteRequest(
                        action.itemId,
                        action.targetPresent,
                    )
                }
            } catch (error: CancellationException) {
                HackerNewsActionResult.Failure(
                    summary = when (action.kind) {
                        SavedItemActionKind.VOTE -> "Vote was interrupted"
                        SavedItemActionKind.FAVORITE -> "Favorite update was interrupted"
                    },
                    detail = error.message,
                )
            } catch (error: Throwable) {
                HackerNewsActionResult.Failure(
                    summary = when (action.kind) {
                        SavedItemActionKind.VOTE -> "Vote failed"
                        SavedItemActionKind.FAVORITE -> "Favorite update failed"
                    },
                    detail = error.message,
                )
            }
            when {
                result is HackerNewsActionResult.Success -> {
                    reconcileSuccessIgnoringFailure(action)
                    SavedItemActionOutcome.Success(action)
                }
                result is HackerNewsActionResult.Failure &&
                    result.reason == HackerNewsActionFailureReason.INDETERMINATE -> {
                    SavedItemActionOutcome.Indeterminate(action, result)
                }
                else -> {
                    rollbackIgnoringFailure(action)
                    SavedItemActionOutcome.Failure(action, result)
                }
            }
        }
    }

    suspend fun cancel(action: PendingSavedItemAction) {
        rollbackIgnoringFailure(action)
    }

    private suspend fun executeSerialized(
        source: SavedItemSource,
        itemId: Int,
        createPending: suspend () -> PendingSavedItemAction,
        onPending: (PendingSavedItemAction) -> Unit,
    ): SavedItemActionOutcome = repository.withSerializedAction(source, itemId) {
        val pending = createPending()
        try {
            onPending(pending)
            // Preserve the UI contract that observers see the optimistic state before a fast
            // request can complete and reconcile it.
            yield()
        } catch (error: CancellationException) {
            cancel(pending)
            throw error
        } catch (error: Throwable) {
            cancel(pending)
            throw error
        }
        execute(pending)
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

    private suspend fun persistAtomic(action: PendingSavedItemAction, present: Boolean) {
        when {
            action.kind == SavedItemActionKind.FAVORITE -> repository.setMembershipAtomic(
                SavedItemSource.FAVORITES,
                action.itemId,
                present,
                nowMillis(),
            )

            action.isComment -> repository.setCommentMembershipAtomic(
                SavedItemSource.UPVOTED,
                action.itemId,
                present,
            )

            else -> repository.setMembershipAtomic(
                SavedItemSource.UPVOTED,
                action.itemId,
                present,
                nowMillis(),
            )
        }
    }

    private suspend fun rollbackIgnoringFailure(action: PendingSavedItemAction) {
        withContext(NonCancellable) {
            runCatching {
                val token = action.mutationToken
                if (token == null) {
                    persistAtomic(action, action.previousPresent)
                } else {
                    repository.restoreMembershipIfCurrentAtomic(
                        token = token,
                        previousItemPresent = action.previousItemPresent,
                        previousCommentPresent = action.previousCommentPresent,
                        createdAtMillis = nowMillis(),
                    )
                }
            }
        }
    }

    private suspend fun reconcileSuccessIgnoringFailure(action: PendingSavedItemAction) {
        val token = action.mutationToken ?: return
        withContext(NonCancellable) {
            runCatching {
                repository.reconcileMembershipIfNoNewerMutationAtomic(
                    token = token,
                    present = action.targetPresent,
                    createdAtMillis = nowMillis(),
                )
            }
        }
    }

}
