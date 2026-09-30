package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.PreparedCommentThread
import com.simon.harmonichackernews.data.Story
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Selects the configured comments source and owns the transport-neutral Algolia-to-official-API
 * fallback policy. Platform shells only decide how to present the result.
 */
class CommentThreadRepository(
    private val algoliaRepository: AlgoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository,
    private val algoliaCommentsParser: AlgoliaCommentsParser = AlgoliaCommentsParser(),
    private val preloads: CommentsPreloadRepository? = null,
    requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val requests = AlgoliaCommentRequests(algoliaRepository, requestDispatcher)

    fun acquireAlgoliaRequest(storyId: Int): AlgoliaCommentRequest =
        preloads?.acquireAlgoliaRequest(storyId) ?: requests.acquire(storyId)

    private val officialLoader = OfficialCommentThreadLoader(hackerNewsRepository)
    private val missingTopLevelComments = MissingTopLevelComments(
        hackerNewsRepository, algoliaCommentsParser, requestDispatcher,
    )

    /** Shared by fresh loads and both ready/in-flight preloads when a discussion is opened. */
    suspend fun reconcileMissingTopLevelComments(
        result: CommentThreadLoadResult.Algolia,
        topLevelCommentIds: List<Int>,
        filteredUsers: Set<String>,
        onPending: suspend () -> Unit = {},
    ): CommentThreadLoadResult.Algolia = missingTopLevelComments.reconcile(
        result,
        topLevelCommentIds.ifEmpty { result.parsed.cacheSummary?.topLevelCommentIds.orEmpty() },
        filteredUsers,
        onPending,
    )

    suspend fun takePreloadedAlgolia(
        storyId: Int,
        topLevelCommentIds: List<Int> = emptyList(),
        filteredUsers: Set<String> = emptySet(),
        awaitInFlight: Boolean = true,
    ): PreloadedCommentsThread? = preloads?.takeOrAwait(
        storyId,
        topLevelCommentIds,
        filteredUsers,
        awaitInFlight,
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
        cachedThread: PreparedCommentThread? = null,
        onAlgoliaFallback: () -> Unit = {},
        algoliaRequest: AlgoliaCommentRequest? = null,
    ): CommentThreadLoadResult {
        require(storyId > 0) { "A positive Hacker News item ID is required" }

        if (!useAlgolia) {
            return officialLoader.load(storyId, filteredUsers, usedAsFallback = false)
        }

        return try {
            coroutineScope {
                val response = async {
                    if (algoliaRequest != null) {
                        require(algoliaRequest.storyId == storyId)
                        algoliaRequest.await()
                    } else {
                        algoliaRepository.getItemJson(storyId)
                    }
                }
                val resolvedIds = if (topLevelCommentIds.isEmpty()) {
                    async { resolveTopLevelCommentIds(storyId, topLevelCommentIds) }
                } else {
                    null
                }
                val responseText = response.await()
                val orderedIds = resolvedIds?.await() ?: topLevelCommentIds
                CommentThreadLoadResult.Algolia(
                    responseText,
                    parseAlgolia(responseText, orderedIds, filteredUsers, cachedThread),
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
        cachedThread: PreparedCommentThread? = null,
    ): AlgoliaCommentsResponse = algoliaCommentsParser.parseForDisplay(
        response, topLevelCommentIds, filteredUsers, cachedThread,
    )

    private fun Exception.shouldFallBackToOfficialApi(): Boolean =
        this is HttpRequestTimeoutException ||
            this is HttpStatusException && (statusCode == 404 || statusCode >= 500)
}

/** Downloads and prepares a complete comment forest from the official per-item HN API. */
class OfficialCommentThreadLoader(
    private val hackerNewsRepository: HackerNewsRepository,
) {
    // Share the limit across branches and simultaneous loads; release each permit before
    // descending so a wide tree cannot flood the transport or deadlock waiting for children.
    private val requests = Semaphore(WORKER_COUNT)
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
        if (topLevelIds.isEmpty()) return@coroutineScope mutableListOf()
        val roots = MutableList<LoadedCommentBranch?>(topLevelIds.size) { null }
        val work = Channel<CommentWork>(Channel.UNLIMITED)
        val completed = Channel<CompletedComment>(WORKER_COUNT)
        // Only these workers suspend in the transport. The coordinator owns the tree and queue,
        // so wide/deep discussions don't create a coroutine and awaitAll list for every branch.
        val workers = List(WORKER_COUNT) {
            launch {
                for (next in work) {
                    val comment = try {
                        requests.withPermit { hackerNewsRepository.getComment(next.id) }
                    } catch (error: CancellationException) {
                        // A transport may cancel its own request while the parent is still active.
                        // Propagate that cancellation rather than leave the coordinator waiting.
                        this@coroutineScope.cancel(error)
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    completed.send(CompletedComment(next, comment))
                }
            }
        }
        try {
            topLevelIds.forEachIndexed { index, id ->
                work.send(CommentWork(id, 0, roots, index))
            }
            var remaining = topLevelIds.size
            while (remaining > 0) {
                val (next, comment) = completed.receive()
                remaining--
                val author = comment?.by ?: continue
                if (author.lowercase() in filteredUsers) continue
                comment.expanded = true
                comment.depth = next.depth
                val childIds = comment.kidsIds
                val children = MutableList<LoadedCommentBranch?>(childIds?.size ?: 0) { null }
                next.destination[next.index] = LoadedCommentBranch(comment, children)
                childIds?.forEachIndexed { index, id ->
                    work.send(CommentWork(id, next.depth + 1, children, index))
                    remaining++
                }
            }
        } finally {
            work.close()
            workers.forEach { it.cancel() }
            completed.cancel()
        }
        // Each branch retains only its direct children. Flatten once after loading so a deeply
        // nested reply is not copied into every ancestor's intermediate list.
        val pending = ArrayDeque<LoadedCommentBranch>()
        for (index in roots.indices.reversed()) roots[index]?.let(pending::addLast)
        val comments = mutableListOf<Comment>()
        while (pending.isNotEmpty()) {
            coroutineContext.ensureActive()
            val branch = pending.removeLast()
            comments.add(branch.comment)
            for (index in branch.children.indices.reversed()) {
                branch.children[index]?.let(pending::addLast)
            }
        }
        comments
    }

    private class LoadedCommentBranch(val comment: Comment, val children: List<LoadedCommentBranch?>)

    private class CommentWork(
        val id: Int,
        val depth: Int,
        val destination: MutableList<LoadedCommentBranch?>,
        val index: Int,
    )

    private data class CompletedComment(val work: CommentWork, val comment: Comment?)

    private companion object {
        const val WORKER_COUNT = 8
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
