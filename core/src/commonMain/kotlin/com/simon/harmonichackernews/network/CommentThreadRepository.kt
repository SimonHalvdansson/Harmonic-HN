package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Selects the configured comments source and owns the transport-neutral Algolia-to-official-API
 * fallback policy. Platform shells only decide how to present the result.
 */
class CommentThreadRepository(
    private val algoliaRepository: AlgoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository,
    private val algoliaCommentsParser: AlgoliaCommentsParser = AlgoliaCommentsParser(),
    private val preloads: CommentsPreloadRepository? = null,
) {
    private val officialLoader = OfficialCommentThreadLoader(hackerNewsRepository)

    suspend fun takePreloadedAlgolia(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedCommentsThread? = preloads?.takeOrAwait(
        storyId,
        topLevelCommentIds,
        filteredUsers,
    )

    suspend fun takePreloadedOfficial(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): PreloadedOfficialCommentsThread? = preloads?.takeOfficialOrAwait(
        storyId,
        topLevelCommentIds,
        filteredUsers,
    )

    suspend fun load(
        storyId: Int,
        useAlgolia: Boolean,
        filteredUsers: Set<String> = emptySet(),
        topLevelCommentIds: List<Int> = emptyList(),
        onAlgoliaFallback: () -> Unit = {},
    ): CommentThreadLoadResult {
        require(storyId > 0) { "A positive Hacker News item ID is required" }

        if (!useAlgolia) {
            return officialLoader.load(storyId, filteredUsers, usedAsFallback = false)
        }

        return try {
            coroutineScope {
                val response = async { algoliaRepository.getItemJson(storyId) }
                val resolvedIds = if (topLevelCommentIds.isEmpty()) {
                    async { resolveTopLevelCommentIds(storyId, topLevelCommentIds) }
                } else {
                    null
                }
                val responseText = response.await()
                val orderedIds = resolvedIds?.await() ?: topLevelCommentIds
                CommentThreadLoadResult.Algolia(
                    responseText,
                    parseAlgolia(responseText, orderedIds, filteredUsers),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error.shouldFallBackToOfficialApi()) {
                onAlgoliaFallback()
                officialLoader.load(storyId, filteredUsers, usedAsFallback = true)
            } else {
                CommentThreadLoadResult.Failure(
                    noInternet = error !is HttpStatusException,
                    source = CommentThreadSource.ALGOLIA,
                    cause = error,
                )
            }
        }
    }

    /**
     * Algolia returns a useful comment tree but not Hacker News' ranked top-level order. A story
     * opened from a deep link may not carry its `kids` array, so resolve it before parsing cached
     * or network comment JSON.
     */
    internal suspend fun resolveTopLevelCommentIds(
        storyId: Int,
        knownIds: List<Int>,
    ): List<Int> {
        if (knownIds.isNotEmpty()) return knownIds
        return try {
            hackerNewsRepository.getStory(storyId)?.kids?.toList().orEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun parseAlgolia(
        response: String,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
    ): AlgoliaCommentsResponse = algoliaCommentsParser.parse(
        response,
        topLevelCommentIds,
        filteredUsers,
    )

    private fun Exception.shouldFallBackToOfficialApi(): Boolean =
        this is HttpRequestTimeoutException ||
            this is HttpStatusException && (statusCode == 404 || statusCode >= 500)
}

/** Downloads and prepares a complete comment forest from the official per-item HN API. */
class OfficialCommentThreadLoader(
    private val hackerNewsRepository: HackerNewsRepository,
) {
    suspend fun load(
        storyId: Int,
        filteredUsers: Set<String>,
        usedAsFallback: Boolean,
    ): CommentThreadLoadResult {
        return try {
            val story = hackerNewsRepository.getStory(storyId)
                ?: return CommentThreadLoadResult.Failure(
                    noInternet = false,
                    source = CommentThreadSource.OFFICIAL,
                )
            val normalizedFilteredUsers = filteredUsers.mapTo(mutableSetOf()) { it.lowercase() }
            val comments = loadCommentForest(story.kids ?: intArrayOf(), normalizedFilteredUsers)
            CommentThreadLoadResult.Official(story, comments, usedAsFallback)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            CommentThreadLoadResult.Failure(
                noInternet = error !is HttpStatusException,
                source = CommentThreadSource.OFFICIAL,
                cause = error,
            )
        }
    }

    private suspend fun loadCommentForest(
        topLevelIds: IntArray,
        filteredUsers: Set<String>,
    ): MutableList<Comment> = coroutineScope {
        topLevelIds
            .map { commentId -> async { loadCommentBranch(commentId, 0, filteredUsers) } }
            .awaitAll()
            .flatten()
            .toMutableList()
    }

    private suspend fun loadCommentBranch(
        commentId: Int,
        depth: Int,
        filteredUsers: Set<String>,
    ): List<Comment> {
        val comment = try {
            hackerNewsRepository.getComment(commentId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val author = comment.by ?: return emptyList()
        if (author.lowercase() in filteredUsers) return emptyList()

        comment.expanded = true
        comment.depth = depth
        val descendants = coroutineScope {
            (comment.kidsIds ?: intArrayOf())
                .map { childId -> async { loadCommentBranch(childId, depth + 1, filteredUsers) } }
                .awaitAll()
                .flatten()
        }
        return buildList(1 + descendants.size) {
            add(comment)
            addAll(descendants)
        }
    }
}

enum class CommentThreadSource {
    ALGOLIA,
    OFFICIAL,
}

sealed interface CommentThreadLoadResult {
    data class Algolia(
        val response: String,
        val parsed: AlgoliaCommentsResponse,
    ) : CommentThreadLoadResult

    data class Official(
        val story: Story,
        val comments: MutableList<Comment>,
        val usedAsFallback: Boolean,
    ) : CommentThreadLoadResult

    data class Failure(
        val noInternet: Boolean,
        val source: CommentThreadSource,
        val cause: Throwable? = null,
    ) : CommentThreadLoadResult
}
