package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.CancellationException

/** Resolves the parent story for a comment row without any platform navigation assumptions. */
class CommentMasterResolver(
    private val repository: HackerNewsRepository,
) {
    suspend fun resolve(source: Story): Story {
        val placeholder = source.toRootStory() ?: return source
        if (placeholder.loaded) return placeholder

        val resolved = repository.getStory(placeholder.id) ?: return placeholder
        source.updateRootStoryFrom(resolved)
        return resolved
    }

    suspend fun resolveParentChain(
        source: Story,
        parentId: Int,
        maxDepth: Int = 8,
        requestAttempts: Int = 3,
    ): Story? {
        var nextParentId = parentId
        repeat(maxDepth.coerceAtLeast(0)) {
            if (nextParentId <= 0) return null
            val parent = loadParent(nextParentId, requestAttempts) ?: return null
            if (!parent.isComment) {
                source.updateRootStoryFrom(parent)
                return parent
            }
            nextParentId = parent.parentId
        }
        return null
    }

    private suspend fun loadParent(id: Int, requestAttempts: Int): Story? {
        var lastFailure: Exception? = null
        repeat(requestAttempts.coerceAtLeast(1)) {
            try {
                return repository.getStory(id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastFailure = error
            }
        }
        throw checkNotNull(lastFailure)
    }
}
